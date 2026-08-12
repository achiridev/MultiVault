# MultiTenant

## Propósito

Documentar la estrategia de multi-tenancy del sistema, que utiliza aislamiento físico mediante schemas separados en PostgreSQL.

## Estado actual

La estrategia está definida en los esquemas SQL e **implementada en la capa de aplicación** en dos planos:

1. **Aprovisionamiento**: el flujo de onboarding crea el schema físico y migra el template por tenant (ADR-0004).
2. **Enrutamiento en runtime** (ADR-0009): las consultas JPA caen en el schema del tenant vía `hibernate.multiTenancy=SCHEMA` con `SET search_path` sobre un pool único (HikariCP). Implementado por `TenantContext` (ThreadLocal), `CurrentTenantIdentifierResolverImpl`, `MultiTenantConnectionProviderImpl` y `TenantContextFilter`.

## Información encontrada

### Estrategia: Schema-per-tenant

Cada tenant tiene un schema PostgreSQL propio. Las tablas compartidas viven en el schema `public`.

#### Tablas globales (schema `public`)

| Tabla | Propósito |
|---|---|
| `plan` | Catálogo de planes de facturación |
| `tenant` | Registro de tenants, con `schema_name` para localizar su schema |
| `subscription` | Historial de suscripciones por tenant |
| `tenant_identity_provider` | Configuración OIDC/JWT por tenant |
| `api_key` | API keys para autenticación M2M |
| `platform_user` | Usuarios del staff interno (SUPER_ADMIN, SUPPORT) |
| `tenant_member` | Identidad canónica de usuarios finales |
| `tenant_usage` | Contadores de cuota (almacenamiento, usuarios) |
| `audit_log` | Log de auditoría compartido |

#### Tablas por tenant (schema del tenant)

| Tabla | Propósito |
|---|---|
| `folder` | Carpetas jerárquicas con path materializado |
| `document` | Documentos lógicos |
| `document_version` | Versiones inmutables de documentos |
| `document_permission` | ACLs por documento |

### Flujo de aprovisionamiento de tenant

Implementado en `POST /api/v1/tenants` (ADR-0004). Tres límites transaccionales:

1. **TX 1** — `TenantProvisioningService.initialize`: inserta `tenant` con `status = 'PENDING_PROVISIONING'`, `subscription` (`ACTIVE`), `tenant_usage`, `tenant_member` (admin) e `identity_provider` (obligatorio, ADR-0006). COMMIT.
2. **Fuera de transacción** — `TenantSchemaProvisioner.provision`: `CREATE SCHEMA <schema>;` + Flyway programático sobre `classpath:db/tenant` (historial `flyway_schema_history` dentro del schema del tenant).
   - Si falla → `markProvisioningFailed` (tenant `SUSPENDED` + `suspended_reason = 'schema_provisioning_failed'`) + `TenantProvisioningFailedEvent` (notifica al admin; canal log hoy) + error 500.
3. **TX 2** — `TenantProvisioningService.activate`: tenant `status = 'ACTIVE'` + `current_plan_id`, creación de la API key inicial del admin (`ApiKeyService.createInitial`) y auditoría `TENANT_CREATED` (AFTER_COMMIT).

`schema_name` se genera como `mv_` + slug del `name` (vacío → 400; truncado a 63 caracteres). Un `schema_name` duplicado responde 409.

### Validación de schema_name

```sql
CHECK (schema_name ~ '^[a-z][a-z0-9_]*$')
```

Límite de 63 caracteres (límite de identificadores PostgreSQL).

### Aislamiento

- Cada tenant opera sobre su propio schema
- Las conexiones JDBC usan `search_path` para apuntar al schema correcto
- No hay riesgo de fuga de datos entre tenants a nivel de base de datos

### Enrutamiento en runtime (ADR-0009)

- `TenantContext` (ThreadLocal) guarda el `schema_name` del request actual (`infrastructure/persistence/tenant/context/TenantContext`).
- `TenantContextFilter` (`OncePerRequestFilter`, registrado **después** de los filtros JWT/API key) lee el principal (`TenantUserPrincipal.tenantId` o `ApiKeyPrincipal.tenantId`), resuelve el schema vía `TenantSchemaResolver` (lookup `tenant.schema_name` por `TenantRepository`, 404 si el tenant no existe) y lo setea en `TenantContext`; limpia el contexto en `finally`. Ambos viven en `infrastructure/persistence/tenant/context/`.
- Requests sin autenticación (p.ej. `POST /api/v1/tenants`) o sin tenant → contexto vacío. El resolver de Hibernate mapea ese caso al identificador sentinela `public` (Hibernate 7 exige un identificador **no nulo** al abrir una Session); el connection provider trata `public` como la conexión `any` (sin `SET search_path`).
- `MultiTenantConnectionProviderImpl.selectConnectionProvider(schema)` entrega una conexión del pool único con `SET search_path TO "<schema>"` (schema validado por regex al crearse; comillas escapadas igualmente). Al liberar (`releaseConnection`) ejecuta `SET search_path TO public` para no fugar el schema del tenant en el pool. `supportsAggressiveRelease()` → `false`.
- Las tablas públicas se cualifican explícitamente con `@Table(schema = "public")` (no dependen del `search_path`); las tablas por-tenant se dejan sin schema y resuelven vía `search_path`.
- `TenantSchemaProvisioner` y Flyway usan el DataSource directo (no Hibernate) → no se ven afectados por el multi-tenancy.

## Pendientes

- [x] Implementar `TenantContext` holder (ThreadLocal) para mantener el tenant actual en cada request
- [x] Implementar filtro/middleware que resuelva el tenant desde el request (JWT/API key) — `TenantContextFilter`
- [x] Implementar `MultiTenantConnectionProvider` para Hibernate (`search_path` sobre pool único, ADR-0009)
- [x] Implementar servicio de aprovisionamiento de nuevos tenants (crear schema, ejecutar tenant_schema.sql) — ADR-0004
- [ ] Implementar lógica de suspensión/cancelación de tenants
- [x] Agregar migraciones Flyway para schemas de tenant — `db/tenant/V1__tenant_schema.sql`, `db/tenant/V2__fix_document_owner_permission_trigger.sql`

## Preguntas abiertas

- ~~¿Cómo se resuelve el tenant en cada request?~~ → **Resuelto:** desde el principal de seguridad (`TenantUserPrincipal.tenantId` para JWT, `ApiKeyPrincipal.tenantId` para API key), vía `TenantContextFilter` (ADR-0009)
- ~~¿Pool por tenant o pool único con search_path?~~ → **Resuelto:** pool único con `search_path` dinámico (ADR-0009)
- ¿Cómo se reintenta un tenant con `status = 'SUSPENDED'` por fallo de aprovisionamiento (retry manual o automático)?
