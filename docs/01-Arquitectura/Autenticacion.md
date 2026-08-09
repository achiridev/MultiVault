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

- [ ] Configurar Spring Security con `SecurityFilterChain`
- [ ] Implementar `JwtDecoder` multi-issuer que use `tenant_identity_provider` para obtener claves públicas
- [ ] Implementar `ApiKeyFilter` para autenticación vía API keys
- [ ] Implementar `PlatformUserAuthenticationProvider` para login de staff
- [ ] Implementar servicio de creación/rotación de API keys
- [ ] Implementar `TenantMemberService` para upsert de miembros
- [ ] Agregar endpoint de login para platform_user
- [ ] Agregar endpoint de refresh de API keys
- [x] Implementar creación de API keys (key inicial del admin en onboarding); rotación/validación pendientes

## Preguntas abiertas

- ¿Los JWTs se validan contra el JWKS URI en cada request o se cachean las claves?
- ¿Cómo se distingue si un request usa JWT vs API Key?
- ¿Los SERVICE keys requieren algún tipo de rate limiting diferente?
- ¿Cómo se maneja la expiración de sesiones de platform_user?
