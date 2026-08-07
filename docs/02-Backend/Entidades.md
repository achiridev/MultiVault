# Entidades

## Propósito

Documentar las entidades JPA que mapean las tablas de la base de datos.

## Estado actual

Existen las entidades `Tenant`, `TenantIdentityProvider`, `TenantMember`, `AuditLog` (schema público), `Plan` y `Subscription`, y las clases base `BaseEntity`, `DateAudit` y `SoftDeletable` (auditoría). Este documento mapea cada tabla a su entidad basada en el schema SQL.

## Información encontrada

### Mapeo tabla → posible entidad

#### Schema público

| Tabla | Posible clase | Notas |
|---|---|---|
| `plan` | `Plan` ✅ | Catálogo de planes, enum `PlanCode` |
| `tenant` | `Tenant` ✅ | Core, schema_name validado, enum `TenantStatus` |
| `subscription` | `Subscription` ✅ | Historial, un ACTIVE por tenant, enum `SubscriptionStatus` |
| `tenant_identity_provider` | `TenantIdentityProvider` ✅ | OIDC config, PK = `tenant_id`, `List<String>` (TEXT[]) |
| `api_key` | `ApiKey` ✅ | key_hash, key_prefix, key_type enum `ApiKeyType` |
| `platform_user` | `PlatformUser` | password_hash, role enum |
| `tenant_member` | `TenantMember` ✅ | tenant_id + subject unique |
| `tenant_usage` | `TenantUsage` ✅ | PK = tenant_id |
| `audit_log` | `AuditLog` ✅ | WORM, metadata JSONB, `created_at` sin `updated_at` |

#### Schema por tenant

| Tabla | Posible clase | Notas |
|---|---|---|
| `folder` | `Folder` | Self-ref parent, path materializado |
| `document` | `Document` | current_version_id FK circular |
| `document_version` | `DocumentVersion` | Immutable, storage_key, checksum |
| `document_permission` | `DocumentPermission` | ACL, permission_level enum |

### Convenciones de mapeo

| Convención | Valor |
|---|---|
| Naming table → entity | snake_case → PascalCase (`plan` → `Plan`) |
| Naming column → field | snake_case → camelCase (`current_version_id` → `currentVersionId`) |
| IDs | `java.util.UUID` con `@GeneratedValue(strategy = GenerationType.UUID)` |
| Timestamps | `OffsetDateTime` o `Instant` para `TIMESTAMPTZ` |
| JSONB | `com.fasterxml.jackson.databind.JsonNode` |
| Arrays (TEXT[]) | `List<String>` con `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Array(length)` — el `AttributeConverter` **no aplica** (Hibernate 7 lo trata como basic type y falla en `BasicPluralType`) |
| Enums | Java `enum` con `@Enumerated(EnumType.STRING)` |
| Soft deletes | campo `deletedAt`; queries siempre con `WHERE deleted_at IS NULL` |
| Versiones de documentos | nunca UPDATE; solo INSERT y repunte de FK en `current_version_id` |
| Auditoría | entidades con `created_at`/`updated_at` → `DateAudit`; con `deleted_at` → `SoftDeletable` |
| Organización | package por feature con sub-paquetes: entidades en `<feature>/model/` (p.ej. `tenant/model/`) |

### Entidades del schema público

| Entidad | Notas |
|---|---|
| `plan/model/Plan.java` | `extends DateAudit`. `code` como `PlanCode` (`@Enumerated(STRING)`, `length = 50`). `max_storage_bytes` como `Long`. |
| `tenant/model/Tenant.java` | `extends DateAudit`. `status` como `TenantStatus` (`@Enumerated(STRING)`, `length = 30`). `current_plan_id` como `UUID` plano (denormalized cache, sin `@ManyToOne` a `Plan`). `length` explícito por columna por `ddl-auto: validate`. |
| `subscription/model/Subscription.java` | `extends DateAudit`. `tenant_id`/`plan_id` como `UUID` plano (sin `@ManyToOne`, consistente con `Tenant.current_plan_id`). `status` como `SubscriptionStatus` (`length = 20`). `starts_at`/`ends_at`/`cancelled_at` como `Instant`. |
| `tenant/model/TenantIdentityProvider.java` | PK = `tenant_id` (inline, sin `DateAudit`: no tiene columna `id`). Audit timestamps inline (`@CreatedDate`/`@LastModifiedDate` + `@EntityListeners`). `allowed_algorithms` como `List<String>` con `@JdbcTypeCode(SqlTypes.ARRAY)`. |
| `tenant/model/TenantMember.java` | `extends BaseEntity` (no tiene `created_at`/`updated_at`; usa `first_seen_at`/`last_seen_at` con default `now()`). `tenant_id` + `subject` unique. |
| `audit/model/AuditLog.java` | `extends BaseEntity` (WORM: no tiene `updated_at`). `created_at` con `@CreatedDate` + `@EntityListeners`. `actor_type` como `ActorType` (`length = 20`). `metadata` JSONB → `tools.jackson.databind.JsonNode` con `@JdbcTypeCode(SqlTypes.JSON)` (Jackson 3 en Boot 4). `ip_address` INET → `java.net.InetAddress` con `@JdbcTypeCode(SqlTypes.INET)` (soporte nativo Hibernate 7). `tenant_id`/`actor_user_id`/`api_key_id`/`resource_id` como `UUID` plano. |
| `audit/model/AuditLog.java` | `extends BaseEntity` (WORM: no tiene `updated_at`). `created_at` con `@CreatedDate` + `@EntityListeners`. `actor_type` como `ActorType` (`length = 20`). `metadata` JSONB → `tools.jackson.databind.JsonNode` con `@JdbcTypeCode(SqlTypes.JSON)` (Jackson 3 en Boot 4). `ip_address` INET → `java.net.InetAddress` con `@JdbcTypeCode(SqlTypes.INET)` (soporte nativo Hibernate 7). `tenant_id`/`actor_user_id`/`api_key_id`/`resource_id` como `UUID` plano. |
| `apikey/model/ApiKey.java` | `extends BaseEntity` (solo `created_at`, sin `updated_at`). `key_type` como `ApiKeyType` (`length = 10`). `scopes` como `List<String>` con `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Array`. Solo se persiste `key_hash` (SHA-256 hex); la key raw nunca se guarda. |
| `tenant/model/TenantUsage.java` | PK = `tenant_id` (inline, no extiende `BaseEntity`). `storage_bytes_used`/`user_count` con defaults 0. `updated_at` con `@LastModifiedDate` (la tabla no tiene `created_at`). |
| `tenant/model/TenantStatus.java` | Enum: `PENDING_PROVISIONING`, `ACTIVE`, `SUSPENDED`, `CANCELLED` |
| `plan/model/PlanCode.java` | Enum: `FREE`, `PRO`, `BUSINESS`, `ENTERPRISE` |
| `subscription/model/SubscriptionStatus.java` | Enum: `ACTIVE`, `CANCELLED`, `PAST_DUE`, `TRIALING` |
| `audit/model/ActorType.java` | Enum: `TENANT_USER`, `PLATFORM_STAFF`, `SYSTEM`, `API_KEY` |
| `apikey/model/ApiKeyType.java` | Enum: `SERVICE`, `STANDARD` |

### Clases base y auditoría

| Clase | Campos | Uso |
|---|---|---|
| `BaseEntity` | `id: UUID` (`@GeneratedValue(strategy = GenerationType.UUID)`) | Todas las entidades |
| `DateAudit extends BaseEntity` | `createdAt`, `updatedAt` (`Instant`, poblados automáticamente vía `AuditingEntityListener`) | Entidades con `created_at`/`updated_at` (schema público y de tenant) |
| `SoftDeletable extends DateAudit` | `deletedAt` (`Instant`) | Entidades soft-delete (`folder`, `document`) |

- Las columnas se mapean explícitamente (`@Column(name = "created_at")`) porque con `ddl-auto: validate` y `PhysicalNamingStrategyStandardImpl` no hay conversión a snake_case.
- `AuditorAwareImpl` (`infrastructure/persistence/auditing`) es un placeholder que devuelve un UUID de sistema (`00000000-0000-0000-0000-000000000000`) hasta que exista autenticación. Solo aplica a who-columns del schema de tenant (`folder.created_by`, `document_version.created_by`) que guardan `external user_id`; el schema público no usa who-columns.
- **Requisito futuro**: reemplazar `AuditorAwareImpl` por un `AuditorAware` que lea el `tenant_member.id` autenticado antes de poblar `@CreatedBy` con datos reales.

### Ejemplo de estructura esperada

```java
@Entity
@Table(name = "plan")
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer priceCents;

    // ... resto de campos
}
```

## Pendientes

- [ ] Crear el resto de entidades JPA para el schema público (`platform_user`)
- [ ] Crear todas las entidades JPA para el schema por tenant
- [x] Definir enum: `TenantStatus`
- [x] Definir enums: `PlanCode`, `SubscriptionStatus`
- [x] Definir enum: `ActorType`
- [x] Definir enums: `ApiKeyType`; pendientes `PlatformUserRole`, `DocumentStatus`, `PermissionLevel`
- [x] Definir converters para: `JsonNode` (JSONB) vía `@JdbcTypeCode(SqlTypes.JSON)`, `Inet` (INET) vía `InetAddress` + `@JdbcTypeCode(SqlTypes.INET)` — `List<String>` (TEXT[]) ya resuelto con `@JdbcTypeCode(SqlTypes.ARRAY)`
- [ ] Definir relaciones JPA (`@OneToMany`, `@ManyToOne`, `@OneToOne`)
- [x] Definir `@EntityListeners` para `created_at` / `updated_at` automáticos (`DateAudit`)

## Preguntas abiertas

- ¿Las entidades del schema de tenant serán clases separadas o se usarán en un persistence unit distinto?
- ¿Cómo se manejará la FK circular entre `document` y `document_version`? ¿Se omite la relación JPA en un lado?
