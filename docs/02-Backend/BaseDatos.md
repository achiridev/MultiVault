# Base de Datos

## Propósito

Documentar la estructura completa de la base de datos, migraciones, convenciones y políticas de datos.

## Estado actual

La base de datos está completamente modelada a nivel SQL con tres archivos:
1. `db/migration/V1__public_schema.sql` — Schema público (181 líneas, 9 tablas)
2. `db/tenant/V1__tenant_schema.sql` — Schema por tenant (116 líneas, 4 tablas + trigger + FK circular)
3. `db/tenant/V2__fix_document_owner_permission_trigger.sql` — Trigger owner permission independiente del `search_path`

Flyway está habilitado (`spring-boot-starter-flyway` + `flyway-database-postgresql`) y ejecuta las migraciones de `classpath:db/migration` al arrancar contra el DataSource de `DB_URL`. Las migraciones ya aplicadas se registran en la tabla `flyway_schema_history`. El template por tenant (`classpath:db/tenant`) NO lo ejecuta el Flyway principal: se aplica por schema con una instancia Flyway programática al aprovisionar cada tenant (ADR-0004).

### Setup local (perfil `local`)

- `application-local.yml` conecta a `jdbc:postgresql://localhost:5432/multivault` (user/password `postgres`/`postgres`).
- Flyway exige un schema `public` **vacío** sin historial: si la DB fue creada manualmente con `V1__public_schema.sql`, el arranque falla con *"Found non-empty schema(s) but no schema history table"*. En ese caso recrear la DB vacía:
  `dropdb multivault && createdb multivault`
- La DB local debe crearse vacía para que Flyway sea el único dueño del schema (no aplicar migraciones a mano).

## Información encontrada

### Schema público (`V1__public_schema.sql`)

| Tabla | Columnas clave | Constraints |
|---|---|---|
| `plan` | id, code (FREE/PRO/BUSINESS/ENTERPRISE), name, price_cents, max_storage_bytes, max_users, max_requests_per_minute, is_active | PK, UNIQUE(code), CHECK(price_cents >= 0), CHECK(max_storage_bytes >= 0) |
| `tenant` | id, name, schema_name, status, current_plan_id, suspended_at, suspended_reason | PK, UNIQUE(schema_name), CHECK regex schema_name, CHECK status IN(...) |
| `subscription` | id, tenant_id, plan_id, status, starts_at, ends_at, cancelled_at | PK, FK(tenant), FK(plan), PARTIAL UNIQUE INDEX one active |
| `tenant_identity_provider` | tenant_id, issuer, jwks_uri, audience, allowed_algorithms, clock_skew_seconds | PK(FK tenant), CHECK no 'none' algorithm |
| `api_key` | id, tenant_id, name, key_prefix, key_hash, key_type, scopes, last_used_at | PK, FK(tenant), PARTIAL UNIQUE INDEX(key_hash WHERE NOT revoked) |
| `platform_user` | id, email, password_hash, full_name, role | PK, UNIQUE(email), CHECK role IN(SUPER_ADMIN, SUPPORT) |
| `tenant_member` | id, tenant_id, subject, display_name, email, is_active | PK, FK(tenant), UNIQUE(tenant_id, subject) |
| `tenant_usage` | tenant_id, storage_bytes_used, user_count | PK(FK tenant), CHECK(storage >= 0) |
| `audit_log` | id, tenant_id, actor_user_id, actor_type, action, resource_type, resource_id, ip_address, user_agent, metadata(JSONB) | PK, FK(tenant), CHECK actor_type IN(...), indexes on (tenant_id, created_at) y (resource_type, resource_id) |

### Schema por tenant (`db/tenant/V1__tenant_schema.sql`)

| Tabla | Columnas clave | Constraints |
|---|---|---|
| `folder` | id, name, parent_folder_id (self-ref), path, created_by, created_at, updated_at, deleted_at | PK, FK(parent_folder), PARTIAL UNIQUE INDEX root name, PARTIAL UNIQUE INDEX parent+name |
| `document` | id, folder_id, owner_user_id, current_version_id, status, created_at, updated_at, deleted_at | PK, FK(folder), CHECK status IN(ACTIVE, ARCHIVED) |
| `document_version` | id, document_id, version_number, name, storage_key, mime_type, size_bytes, checksum, metadata(JSONB), created_by, created_by_snapshot(JSONB), created_at | PK, FK(document), UNIQUE(document_id, version_number) |
| `document_permission` | id, document_id, user_id, permission_level, granted_by, granted_at | PK, FK(document), UNIQUE(document_id, user_id), PARTIAL UNIQUE INDEX single owner |

#### FK Circular

```sql
ALTER TABLE document
    ADD CONSTRAINT fk_document_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_version(id);
```

#### Trigger

```sql
-- Inserta automáticamente permiso OWNER al crear un documento
CREATE TRIGGER trg_document_owner_permission
AFTER INSERT ON document
FOR EACH ROW EXECUTE FUNCTION fn_document_owner_permission();
```

V1 definía `fn_document_owner_permission` referenciando `document_permission` sin calificar: la función depende del `search_path` de la conexión al ejecutarse. V2 la reemplaza y resuelve el schema desde la propia tabla del trigger (`TG_RELID` + `pg_namespace`), con `EXECUTE format(...) USING ...` — funciona con cualquier `search_path` (detectado por el test de integración).

### Convenciones generales

- **UUIDs:** Todas las PKs usan `UUID` con `DEFAULT gen_random_uuid()` (requiere extensión `pgcrypto`)
- **Timestamps:** `created_at` y `updated_at` como `TIMESTAMPTZ NOT NULL DEFAULT now()`
- **Soft deletes:** `deleted_at TIMESTAMPTZ` en `folder` y `document`
- **Integridad referencial:** ON DELETE CASCADE en la mayoría de FKs
- **Checksums:** SHA-256 hex para verificación de integridad de documentos

### Reglas de migraciones Flyway

- **No modificar** migraciones ya aplicadas (`V1__public_schema.sql`)
- Nuevos cambios = nueva migración (`V2__descripcion.sql`, `V3__descripcion.sql`, etc.)
- El `db/tenant/V1__tenant_schema.sql` se aplica por schema con una instancia Flyway programática al aprovisionar cada tenant; cada schema conserva su propio `flyway_schema_history` (ADR-0004)
- No duplicar en la aplicación lógica que ya existe en la base de datos (ej. trigger de owner permission)
- Si se necesita una nueva dependencia, verificar que no exista ya en `pom.xml` y documentar por qué es necesaria

## Pendientes

- [x] Implementar estrategia de migraciones para schemas de tenant — ADR-0004 (`db/tenant/V1__tenant_schema.sql`)
- [x] Configurar BD de tests — Testcontainers con PostgreSQL (ADR-0005)

## Preguntas abiertas

- ¿Cómo se manejarán las migraciones Flyway en los schemas de tenant existentes cuando se agreguen nuevas tablas?
- ¿Se usará `ddl-auto: validate` para verificar que las entidades coinciden con el schema?
