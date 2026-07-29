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
-- api_key: single mechanism for machine-to-machine auth. Only the hash
-- is stored; the raw key is shown to the user exactly once at creation time.
-- ---------------------------------------------------------------------------
CREATE TABLE api_key (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    key_prefix        VARCHAR(12)  NOT NULL, -- shown in UI for identification, e.g. 'mv_live_a1b2'
    key_hash          VARCHAR(255) NOT NULL, -- sha256/bcrypt hash
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
-- tenant_member: NOT an identity table. A cache of external user_ids
-- seen for a tenant (populated the first time a JWT with that user_id
-- hits the API). Exists purely to (a) enforce the "max users" quota
-- and (b) show a human-readable member list without calling back into
-- the tenant's own system on every request.
-- ---------------------------------------------------------------------
CREATE TABLE tenant_member (
    tenant_id         UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    external_user_id  UUID NOT NULL,
    display_name      VARCHAR(255),
    email             VARCHAR(255),
    first_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, external_user_id)
);

-- ---------------------------------------------------------------------
-- tenant_usage: incremental quota counters. Updated by the app on
-- upload/delete/member-seen events — avoid SUM()/COUNT() over the
-- tenant's documents on every request.
-- ---------------------------------------------------------------------
CREATE TABLE tenant_usage (
    tenant_id            UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    storage_bytes_used   BIGINT      NOT NULL DEFAULT 0
        CHECK (storage_bytes_used >= 0),
    user_count           INTEGER     NOT NULL DEFAULT 0 -- denormalized from tenant_member
        CHECK (user_count >= 0),
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

-- Enforce WORM at the database level, not just by convention in code.
-- Replace `multivault_app` with your actual application role name.
-- REVOKE UPDATE, DELETE ON audit_log FROM multivault_app;
