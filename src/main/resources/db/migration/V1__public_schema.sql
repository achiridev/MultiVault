-- =====================================================================
-- MultiVault — Public schema (shared across all tenants)
-- Applied once at application bootstrap.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS public;
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

-- ---------------------------------------------------------------------
-- plan: billing catalog. Not tenant-specific.
-- ---------------------------------------------------------------------
CREATE TABLE plan (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                     VARCHAR(50)  NOT NULL UNIQUE, -- 'FREE', 'PRO', 'ENTERPRISE'
    name                     VARCHAR(100) NOT NULL,
    price_cents              INTEGER      NOT NULL DEFAULT 0
        CHECK (price_cents >= 0),
    max_storage_bytes        BIGINT       NOT NULL
        CHECK (max_storage_bytes >= 0),
    max_users                INTEGER      NOT NULL
        CHECK (max_users >= 0),
    max_requests_per_minute  INTEGER      NOT NULL
        CHECK (max_requests_per_minute >= 0),
    is_active                BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- tenant: the core entity. schema_name is the physical Postgres schema
-- that holds this tenant's documents/folders/permissions.
-- ---------------------------------------------------------------------
CREATE TABLE tenant (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    schema_name       VARCHAR(63)  NOT NULL UNIQUE-- Postgres identifier limit is 63 chars
        CHECK (schema_name ~ '^[a-z][a-z0-9_]*$'),
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING_PROVISIONING'
        CHECK (status IN ('PENDING_PROVISIONING', 'ACTIVE', 'SUSPENDED', 'CANCELLED')),
    suspended_at      TIMESTAMPTZ,
    suspended_reason  TEXT,
    current_plan_id   UUID REFERENCES plan(id), -- denormalized cache; source of truth is `subscription`
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_status ON tenant(status);

-- ---------------------------------------------------------------------
-- subscription: billing history per tenant. Source of truth for plan.
-- ---------------------------------------------------------------------
CREATE TABLE subscription (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    plan_id       UUID NOT NULL REFERENCES plan(id),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'CANCELLED', 'PAST_DUE', 'TRIALING')),
    starts_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at       TIMESTAMPTZ,
    cancelled_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_subscription_one_active
    ON subscription(tenant_id) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- tenant_identity_provider: configuración OIDC/JWT por tenant.
-- Sin esta fila, ningún JWT de ese tenant puede validarse.
-- ---------------------------------------------------------------------------
CREATE TABLE tenant_identity_provider (
    tenant_id           UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    issuer              VARCHAR(255) NOT NULL,        -- claim 'iss' esperado
    jwks_uri            VARCHAR(500) NOT NULL,        -- endpoint público de llaves
    audience            VARCHAR(255) NOT NULL,        -- claim 'aud' esperado (tu API)
    allowed_algorithms  TEXT[] NOT NULL DEFAULT '{RS256}',
    clock_skew_seconds  INTEGER NOT NULL DEFAULT 60,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_no_none_alg CHECK (NOT ('none' = ANY(allowed_algorithms)))
);

-- ---------------------------------------------------------------------------
-- api_key: single mechanism for machine-to-machine auth. Only the hash
-- is stored; the raw key is shown to the user exactly once at creation time.
-- ---------------------------------------------------------------------------
CREATE TABLE api_key (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    key_prefix        VARCHAR(12)  NOT NULL, -- shown in UI for identification, e.g. 'mv_live_a1b2'
    key_hash          VARCHAR(255) NOT NULL, -- sha256/bcrypt hash
    key_type            VARCHAR(10)  NOT NULL DEFAULT 'STANDARD'
            CHECK (key_type IN ('SERVICE', 'STANDARD')),
            -- SERVICE: sin humano detrás, JWT no requerido (sync, backup, health, webhooks)
            -- STANDARD: 95% del sistema, la app SIEMPRE debe exigir JWT junto a esta key
    scopes            TEXT[]       NOT NULL DEFAULT '{}',
    created_by_user_id UUID        NOT NULL, -- external user_id (identity lives in the tenant's system)
    last_used_at      TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_key_tenant ON api_key(tenant_id) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX idx_api_key_hash ON api_key(key_hash) WHERE revoked_at IS NULL;

-- ---------------------------------------------------------------------
-- platform_user: YOUR staff only (super-admins/support who log in
-- directly to the MultiVault admin panel). Not tenant end users.
-- ---------------------------------------------------------------------
CREATE TABLE platform_user (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL DEFAULT 'SUPPORT'
        CHECK (role IN ('SUPER_ADMIN', 'SUPPORT')),
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- tenant_member: identidad canónica de usuarios finales dentro de MultiVault.
-- `id` es el UUID interno usado en TODO el schema del tenant (document_
-- permission.user_id, document.owner_user_id, etc). `subject` es el claim
-- crudo del JWT externo — puede no ser UUID, por eso NO se usa directo.
-- Se puebla/actualiza (upsert) la primera vez que llega un JWT válido con
-- ese (tenant_id, subject).
-- ---------------------------------------------------------------------
CREATE TABLE tenant_member (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    subject        VARCHAR(255) NOT NULL, -- claim 'sub' del JWT del tenant
    display_name   VARCHAR(255),
    email          VARCHAR(255),
    is_active      BOOLEAN NOT NULL DEFAULT true, -- permite desactivar sin borrar (preserva FKs históricas)
    first_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_member_subject UNIQUE (tenant_id, subject)
);

CREATE INDEX idx_tenant_member_tenant ON tenant_member(tenant_id);

-- ---------------------------------------------------------------------
-- tenant_usage: contadores de cuota. user_count ahora se mantiene con
-- trigger en vez de a criterio de la app.
-- ---------------------------------------------------------------------
CREATE TABLE tenant_usage (
    tenant_id            UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    storage_bytes_used   BIGINT      NOT NULL DEFAULT 0 CHECK (storage_bytes_used >= 0),
    user_count           INTEGER     NOT NULL DEFAULT 0 CHECK (user_count >= 0),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- audit_log: WORM (write-once-read-many). No UPDATE/DELETE at the
-- application role level — see the REVOKE statement at the bottom.
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    actor_user_id  UUID,             -- external user_id, platform_user.id, or NULL for system actions
    api_key_id     UUID REFERENCES api_key(id), -- qué api_key originó la llamada (NULL para platform_user auth)
    actor_type     VARCHAR(20) NOT NULL DEFAULT 'TENANT_USER'
        CHECK (actor_type IN ('TENANT_USER', 'PLATFORM_STAFF', 'SYSTEM', 'API_KEY')),
    action         VARCHAR(100) NOT NULL, -- e.g. 'DOCUMENT_CREATED', 'TENANT_SUSPENDED'
    resource_type  VARCHAR(50),
    resource_id    UUID,
    ip_address     INET,
    user_agent     TEXT,
    metadata       JSONB NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tenant_created ON audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_api_key ON audit_log(api_key_id);
