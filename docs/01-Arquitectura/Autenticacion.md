# Autenticación

## Propósito

Documentar el modelo de autenticación del sistema, que soporta tres mecanismos: OIDC/JWT por tenant, API keys para M2M, y usuarios de plataforma interna.

## Estado actual

El modelo de datos para autenticación está completamente definido en el schema público. No existe implementación de Spring Security.

## Información encontrada

### Mecanismo 1: OIDC/JWT por tenant (tenant_identity_provider)

Cada tenant configura su propio Identity Provider externo.

```sql
CREATE TABLE tenant_identity_provider (
    tenant_id           UUID PRIMARY KEY,
    issuer              VARCHAR(255) NOT NULL,   -- claim 'iss' esperado
    jwks_uri            VARCHAR(500) NOT NULL,   -- endpoint de llaves públicas
    audience            VARCHAR(255) NOT NULL,   -- claim 'aud' esperado
    allowed_algorithms  TEXT[] DEFAULT '{RS256}',
    clock_skew_seconds  INTEGER DEFAULT 60,
    is_active           BOOLEAN DEFAULT true,
    CONSTRAINT chk_no_none_alg CHECK (NOT ('none' = ANY(allowed_algorithms)))
);
```

- Sin fila en esta tabla → ningún JWT de ese tenant puede validarse. Por eso es **obligatoria al crear el tenant** (`POST /api/v1/tenants` rechaza con 400 si falta) y se actualiza con `PUT /api/v1/tenants/{id}/identity-provider` (ADR-0006).
- El algoritmo `'none'` está explícitamente prohibido
- Cada tenant usa su propio `issuer`, lo que permite validar JWTs de múltiples fuentes

**Librería JWT:** se usa `jjwt-api` + `jjwt-impl` (sin `jjwt-jackson`, que arrastraría Jackson 2 y rompería la persistencia JSON de Hibernate — ver ADR-0007). La serialización JSON se hace con un codec propio sobre Jackson 3: `JwtJackson3Serializer` / `JwtJackson3Deserializer` (`infrastructure.security.jwt`). Toda construcción/parseo de JWT debe registrarlo: `Jwts.builder().serializeToJsonWith(serializer)` y `Jwts.parser().deserializeJsonWith(deserializer)`.

### Mecanismo 2: API Keys (api_key)

Para integraciones machine-to-machine.

```sql
CREATE TABLE api_key (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    name              VARCHAR(100) NOT NULL,
    key_prefix        VARCHAR(12) NOT NULL,      -- ej. 'mv_live_a1b2'
    key_hash          VARCHAR(255) NOT NULL,      -- hash sha256/bcrypt
    key_type          VARCHAR(10) NOT NULL DEFAULT 'STANDARD'
        CHECK (key_type IN ('SERVICE', 'STANDARD')),
    scopes            TEXT[] NOT NULL DEFAULT '{}',
    created_by_user_id UUID NOT NULL,
    last_used_at      TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ
);
```

- **SERVICE:** Sin humano detrás (sync, backup, health, webhooks)
- **STANDARD:** Uso normal, la app SIEMPRE debe exigir JWT junto a esta key
- Solo se almacena el hash; la key raw se muestra una única vez al crearla
- Índice único parcial sobre `key_hash WHERE revoked_at IS NULL`
- Al crear un tenant, el onboarding genera automáticamente la **API key inicial del admin** (`ApiKeyService.createInitial`): raw `mv_live_` + 40 hex, `key_prefix` = primeros 12 chars, hash SHA-256, `key_type = STANDARD`, `created_by_user_id = tenant_member.id` del admin. La raw se devuelve una sola vez en la respuesta de `POST /api/v1/tenants`

### Validación de API keys por request (`ApiKeyAuthenticationFilter`)

`infrastructure/security/apikey/ApiKeyAuthenticationFilter` (`OncePerRequestFilter`, registrado antes de `UsernamePasswordAuthenticationFilter`) valida la key en cada request:

- Header: `Authorization: Bearer <api-key>`. Se distingue de un JWT porque la key empieza con el prefijo `mv_live_`; cualquier otro token (ej. `eyJ...`) se deja pasar para el futuro filtro JWT.
- Se hashea la raw (SHA-256, `ApiKeyHasher`) y se busca con `findByKeyHashAndRevokedAtIsNull` (usa el índice único parcial).
- Key desconocida, revocada (`revoked_at`) o expirada (`expires_at` pasado) → no autentica → `401` (`RestAuthenticationEntryPoint` responde con `ErrorResponse` JSON).
- `SERVICE` válida → autentica el request. Principal = `ApiKeyPrincipal(keyId, tenantId, name, keyType)`; authorities = scopes mapeados a `SCOPE_<scope>`. Actualiza `last_used_at` de forma asíncrona (`ApiKeyUsageRecorder` con `@Async`/`@EnableAsync`, `AsyncConfig`).
- `STANDARD` válida → se valida pero **no autentica sola**: exige JWT además (filtro JWT pendiente). Sin JWT → `401`.

El hash se extrajo a `apikey/service/ApiKeyHasher` (reutilizado por `ApiKeyService` y el filtro).

### Mecanismo 3: Platform User (platform_user)

Para el staff interno que administra MultiVault.

```sql
CREATE TABLE platform_user (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    role           VARCHAR(20) NOT NULL DEFAULT 'SUPPORT'
        CHECK (role IN ('SUPER_ADMIN', 'SUPPORT')),
    is_active      BOOLEAN DEFAULT true,
    last_login_at  TIMESTAMPTZ
);
```

### Identidad canónica (tenant_member)

```sql
CREATE TABLE tenant_member (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    subject        VARCHAR(255) NOT NULL,   -- claim 'sub' del JWT
    display_name   VARCHAR(255),
    email          VARCHAR(255),
    is_active      BOOLEAN DEFAULT true,
    first_seen_at  TIMESTAMPTZ,
    last_seen_at   TIMESTAMPTZ,
    CONSTRAINT uq_tenant_member_subject UNIQUE (tenant_id, subject)
);
```

- `id` es el UUID interno usado en TODO el schema del tenant
- `subject` es el raw claim del JWT externo (no necesariamente UUID)
- Se puebla con upsert la primera vez que llega un JWT válido

## Pendientes

- [x] Configurar Spring Security con `SecurityFilterChain` (`SecurityConfig`: CSRF off, stateless, `POST /tenants` público, resto autenticado)
- [ ] Implementar `JwtDecoder` multi-issuer que use `tenant_identity_provider` para obtener claves públicas
- [x] Implementar `ApiKeyFilter` para autenticación vía API keys
- [ ] Implementar `PlatformUserAuthenticationProvider` para login de staff
- [ ] Implementar servicio de creación/rotación de API keys
- [ ] Implementar `TenantMemberService` para upsert de miembros
- [ ] Agregar endpoint de login para platform_user
- [ ] Agregar endpoint de refresh de API keys
- [x] Implementar creación de API keys (key inicial del admin en onboarding); rotación pendiente

## Preguntas abiertas

- ¿Los JWTs se validan contra el JWKS URI en cada request o se cachean las claves?
- ¿Cómo se distingue si un request usa JWT vs API Key? → **Resuelto:** por prefijo `mv_live_` en el token Bearer (`ApiKeyAuthenticationFilter`)
- ¿Los SERVICE keys requieren algún tipo de rate limiting diferente?
- ¿Cómo se maneja la expiración de sesiones de platform_user?
