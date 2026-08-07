# MultiTenant

## Propósito

Documentar la estrategia de multi-tenancy del sistema, que utiliza aislamiento físico mediante schemas separados en PostgreSQL.

## Estado actual

La estrategia está definida en los esquemas SQL y **implementada en la capa de aplicación**: el flujo de aprovisionamiento crea el schema físico y migra el template por tenant al crear una organización (ADR-0004).

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

1. **TX 1** — `TenantProvisioningService.initialize`: inserta `tenant` con `status = 'PENDING_PROVISIONING'`, `subscription` (`ACTIVE`), `tenant_usage`, `tenant_member` (admin) e `identity_provider` (opcional). COMMIT.
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

## Pendientes

- [ ] Implementar `TenantContext` holder (ThreadLocal) para mantener el tenant actual en cada request
- [ ] Implementar filtro/middleware que resuelva el tenant desde el request (dominio, header, JWT)
- [ ] Implementar `TenantConnectionProvider` o `MultiTenantConnectionProvider` para Hibernate
- [x] Implementar servicio de aprovisionamiento de nuevos tenants (crear schema, ejecutar tenant_schema.sql) — ADR-0004
- [ ] Implementar lógica de suspensión/cancelación de tenants
- [x] Agregar migraciones Flyway para schemas de tenant — `db/tenant/V1__tenant_schema.sql`

## Preguntas abiertas

- ¿Cómo se resuelve el tenant en cada request? ¿Por subdominio (`tenant1.app.com`), header (`X-Tenant-ID`), o del JWT?
- ¿Se usará un pool de conexiones por tenant o un pool único con `search_path` dinámico?
- ¿Cómo se reintenta un tenant con `status = 'SUSPENDED'` por fallo de aprovisionamiento (retry manual o automático)?
