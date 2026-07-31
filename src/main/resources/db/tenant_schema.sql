-- =====================================================================
-- MultiVault — Per-tenant schema template
-- Applied by Flyway once per tenant, with search_path set to that
-- tenant's schema (the schema itself is created by the app BEFORE
-- running this migration, e.g. `CREATE SCHEMA tenant_acme;`).
-- =====================================================================

-- ---------------------------------------------------------------------
-- folder: supports nesting via parent_folder_id. `path` is a
-- materialized path (e.g. '/1/5/12/') maintained by the app on
-- insert/move, so subtree queries don't need a recursive CTE.
-- ---------------------------------------------------------------------
CREATE TABLE folder (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    parent_folder_id  UUID REFERENCES folder(id) ON DELETE CASCADE,
    path              VARCHAR(1000),
    created_by        UUID NOT NULL, -- external user_id, no FK: identity lives outside this service
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_folder_root_name
    ON folder(name)
    WHERE parent_folder_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_folder_parent_name_active
    ON folder(parent_folder_id, name)
    WHERE deleted_at IS NULL AND parent_folder_id IS NOT NULL;

CREATE INDEX idx_folder_parent ON folder(parent_folder_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_folder_path_prefix ON folder(path varchar_pattern_ops) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- document: the logical document. Its actual content lives in
-- document_version rows (S3/MinIO object per version).
-- current_version_id is added via ALTER after document_version exists,
-- since the two tables reference each other.
-- ---------------------------------------------------------------------
CREATE TABLE document (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id           UUID REFERENCES folder(id) ON DELETE SET NULL,
    owner_user_id       UUID NOT NULL, -- external user_id; implicit OWNER in document_permission
    current_version_id  UUID,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ -- logical delete only, no scheduled purge
);

CREATE INDEX idx_document_folder ON document(folder_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_document_owner ON document(owner_user_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- document_version: one row per uploaded revision. Immutable once
-- created — never UPDATE a version's content, only INSERT a new one
-- and repoint document.current_version_id.
-- ---------------------------------------------------------------------
CREATE TABLE document_version (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id          UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    version_number       INTEGER NOT NULL CHECK (version_number > 0),
    name                 VARCHAR(500) NOT NULL,
    storage_key          VARCHAR(1000) NOT NULL, -- S3/MinIO object key
    mime_type            VARCHAR(150) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    checksum             VARCHAR(128) NOT NULL, -- sha256 hex, for integrity verification
    metadata             JSONB NOT NULL DEFAULT '{}', -- free-form custom fields on this version
    created_by           UUID NOT NULL, -- external user_id who uploaded this version
    created_by_snapshot  JSONB, -- similar to user_data
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_version UNIQUE (document_id, version_number)
);

CREATE INDEX idx_document_version_document ON document_version(document_id);

ALTER TABLE document
    ADD CONSTRAINT fk_document_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_version(id);

-- ---------------------------------------------------------------------
-- document_permission: per-resource ACL. This is what actually
-- implements "OWNER / EDITOR / VIEWER" — it did not exist in the
-- original design and is the core of the access-control feature.
-- ---------------------------------------------------------------------
CREATE TABLE document_permission (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL, -- external user_id
    permission_level  VARCHAR(10) NOT NULL
        CHECK (permission_level IN ('OWNER', 'EDITOR', 'VIEWER')),
    granted_by        UUID NOT NULL, -- external user_id who granted this permission (el que dio el permiso)
    granted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_permission UNIQUE (document_id, user_id)
);

CREATE INDEX idx_document_permission_user ON document_permission(user_id);
CREATE UNIQUE INDEX uq_document_single_owner
    ON document_permission(document_id) WHERE permission_level = 'OWNER';

-- Al crear el documento, se crea automáticamente su fila OWNER.
-- Ya no depende de que la app recuerde hacer el INSERT extra.
CREATE OR REPLACE FUNCTION fn_document_owner_permission() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO document_permission (document_id, user_id, permission_level, granted_by)
    VALUES (NEW.id, NEW.owner_user_id, 'OWNER', NEW.owner_user_id)
    ON CONFLICT (document_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_document_owner_permission
AFTER INSERT ON document
FOR EACH ROW EXECUTE FUNCTION fn_document_owner_permission();
