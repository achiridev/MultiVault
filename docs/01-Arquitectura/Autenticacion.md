# Autenticación

## Propósito

Documentar el modelo de autenticación del sistema, que soporta tres mecanismos: OIDC/JWT por tenant, API keys para M2M, y usuarios de plataforma interna.

## Estado actual

El modelo de datos para autenticación está definido en el schema público. Spring Security está implementado con dos filtros: `ApiKeyAuthenticationFilter` (API keys M2M) y `JwtAuthenticationFilter` (JWT multi-issuer vía JWKS con caché Redis). Login de platform_user pendiente.

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

**Librería JWT:** se usa `jjwt-api` + `jjwt-impl` (sin `jjwt-jackson`, que arrastraría Jackson 2 y rompería la persistencia JSON de Hibernate — ver ADR-0007). La serialización JSON se hace con un codec propio sobre Jackson 3: `JwtJackson3Serializer` / `JwtJackson3Deserializer` (`infrastructure.security.codec`). Toda construcción/parseo de JWT debe registrarlo: `Jwts.builder().serializeToJsonWith(serializer)` y `Jwts.parser().deserializeJsonWith(deserializer)`.

### Validación de JWTs por request (`JwtAuthenticationFilter`)

`infrastructure/security/jwt/JwtAuthenticationFilter` (`OncePerRequestFilter`, registrado después del filtro de API key y antes de `UsernamePasswordAuthenticationFilter`):

- Solo procesa tokens `Authorization: Bearer` que **no** empiecen con `mv_live_` (esos son API keys). Si el request ya está autenticado (key SERVICE), no hace nada.
- `MultiIssuerJwtDecoder.authenticate`:
  1. Lee los claims `iss` y el header `kid`/`alg`.
  2. Busca el provider por issuer (`findByIssuerAndIsActiveTrue`); sin fila → JWT inválido.
  3. Valida `alg` contra `allowed_algorithms` del provider.
  4. Obtiene el JWKS del issuer (`JwksProvider`, cacheado 10 min en Redis) y arma la clave RSA desde `n`/`e` del JWK.
  5. Verifica la firma con `Jwts.parser().verifyWith(publicKey)`. Si la firma falla, se hace `evict` del JWKS cacheado y se reintenta una vez (soporta key rotation del IdP).
  6. Valida `aud` contra el audience configurado y la expiración (con `clock_skew_seconds` del provider).
- JWT válido → upsert de `tenant_member` (`TenantMemberService.upsert`: crea o actualiza `display_name`, `email`, `last_seen_at`) y autentica con principal `TenantUserPrincipal(memberId, tenantId, subject)`.
- STANDARD key presente + JWT del **mismo tenant** → se combinan: authorities = scopes de la key (`SCOPE_<scope>`) y principal = miembro. Si los tenants difieren → no autentica.
- JWT inválido → `SecurityContextHolder.clearContext()` → `401` (`RestAuthenticationEntryPoint` con `ErrorResponse` JSON).

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

- Header: `Authorization: Bearer <api-key>`. Se distingue de un JWT porque la key empieza con el prefijo `mv_live_`; cualquier otro token (ej. `eyJ...`) se deja pasar para el filtro JWT (`JwtAuthenticationFilter`). También acepta el header `X-API-Key`.
- Se hashea la raw (SHA-256, `ApiKeyHasher`) y se busca con `findByKeyHashAndRevokedAtIsNull` (usa el índice único parcial), con caché en Redis (`@Cacheable("apiKeys")`, TTL 5 min, key = hash).
- Key desconocida, revocada (`revoked_at`) o expirada (`expires_at` pasado) → no autentica → `401` (`RestAuthenticationEntryPoint` responde con `ErrorResponse` JSON).
- `SERVICE` válida → autentica el request. Principal = `ApiKeyPrincipal(keyId, tenantId, name, keyType)`; authorities = scopes mapeados a `SCOPE_<scope>`. Actualiza `last_used_at` de forma asíncrona (`ApiKeyUsageRecorder` con `@Async`/`@EnableAsync`, `AsyncConfig`).
- `STANDARD` válida → **no autentica sola**: expone la key en el request (`STANDARD_API_KEY_ATTR`) y el filtro JWT exige además un JWT del **mismo tenant**. Sin JWT → `401`; con JWT → scopes de la key limitan los del miembro (authorities `SCOPE_<scope>` combinadas).

El hash se extrajo a `apikey/service/ApiKeyHasher` (reutilizado por `ApiKeyService` y el filtro).

### Resolución del tenant por request (`TenantContextFilter`)

`infrastructure/persistence/tenant/context/TenantContextFilter` (`OncePerRequestFilter`, registrado **después** de `JwtAuthenticationFilter` en `SecurityConfig`) resuelve el schema del tenant para el multi-tenancy por `search_path` (ADR-0009):

- Lee el principal del `SecurityContext`: `TenantUserPrincipal.tenantId` (JWT) o `ApiKeyPrincipal.tenantId` (API key).
- Resuelve el `schema_name` vía `TenantSchemaResolver` (lookup `tenant.schema_name`; 404 si el tenant no existe) y lo guarda en `TenantContext` (ThreadLocal).
- Limpia el contexto en `finally`. Requests sin autenticación (p.ej. `POST /api/v1/tenants`) y operaciones públicas quedan en `public` (el resolver de Hibernate mapea el contexto vacío al identificador sentinela `public`).
- La validación del token y el upsert de `tenant_member` corren contra `public` porque el filtro se registra después de los filtros de auth.

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
- [x] Implementar `JwtDecoder` multi-issuer que use `tenant_identity_provider` para obtener claves públicas
- [x] Implementar `ApiKeyFilter` para autenticación vía API keys
- [ ] Implementar `PlatformUserAuthenticationProvider` para login de staff
- [ ] Implementar servicio de creación/rotación de API keys
- [x] Implementar `TenantMemberService` para upsert de miembros
- [ ] Agregar endpoint de login para platform_user
- [ ] Agregar endpoint de refresh de API keys
- [x] Implementar creación de API keys (key inicial del admin en onboarding); rotación pendiente

## Preguntas abiertas

- ¿Los JWTs se validan contra el JWKS URI en cada request o se cachean las claves? → **Resuelto:** el JWKS se cachea en Redis (TTL 10 min) y se re-descarga al fallar la firma (evict + retry, soporta key rotation)
- ¿Cómo se distingue si un request usa JWT vs API Key? → **Resuelto:** por prefijo `mv_live_` en el token Bearer (`ApiKeyAuthenticationFilter`)
- ¿Los SERVICE keys requieren algún tipo de rate limiting diferente?
- ¿Cómo se maneja la expiración de sesiones de platform_user?
