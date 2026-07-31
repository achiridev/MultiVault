# Entidades

## Propósito

Documentar las entidades JPA que mapean las tablas de la base de datos.

## Estado actual

No existe ninguna entidad JPA en el código fuente. Este documento mapea cada tabla a su posible entidad basada en el schema SQL.

## Información encontrada

### Mapeo tabla → posible entidad

#### Schema público

| Tabla | Posible clase | Notas |
|---|---|---|
| `plan` | `Plan` | Catálogo de planes |
| `tenant` | `Tenant` | Core, schema_name validado |
| `subscription` | `Subscription` | Historial, un ACTIVE por tenant |
| `tenant_identity_provider` | `TenantIdentityProvider` | OIDC config, PK = tenant_id |
| `api_key` | `ApiKey` | key_hash, key_prefix, key_type enum |
| `platform_user` | `PlatformUser` | password_hash, role enum |
| `tenant_member` | `TenantMember` | tenant_id + subject unique |
| `tenant_usage` | `TenantUsage` | PK = tenant_id |
| `audit_log` | `AuditLog` | WORM, metadata JSONB |

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
| Arrays (TEXT[]) | `List<String>` con converter |
| Enums | Java `enum` con `@Enumerated(EnumType.STRING)` |
| Soft deletes | campo `deletedAt`; queries siempre con `WHERE deleted_at IS NULL` |
| Versiones de documentos | nunca UPDATE; solo INSERT y repunte de FK en `current_version_id` |

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

- [ ] Crear todas las entidades JPA para el schema público
- [ ] Crear todas las entidades JPA para el schema por tenant
- [ ] Definir enums: `PlanCode`, `TenantStatus`, `SubscriptionStatus`, `ApiKeyType`, `PlatformUserRole`, `ActorType`, `DocumentStatus`, `PermissionLevel`
- [ ] Definir converters para: `List<String>` (TEXT[]), `JsonNode` (JSONB), `Inet` (INET)
- [ ] Definir relaciones JPA (`@OneToMany`, `@ManyToOne`, `@OneToOne`)
- [ ] Definir `@EntityListeners` para `created_at` / `updated_at` automáticos

## Preguntas abiertas

- ¿Las entidades del schema de tenant serán clases separadas o se usarán en un persistence unit distinto?
- ¿Cómo se manejará la FK circular entre `document` y `document_version`? ¿Se omite la relación JPA en un lado?
- ¿Se usará `@MappedSuperclass` para tener campos auditables base?
