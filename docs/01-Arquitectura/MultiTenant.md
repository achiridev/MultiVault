# MultiTenant

## Propósito

Documentar la estrategia de multi-tenancy del sistema, que utiliza aislamiento físico mediante schemas separados en PostgreSQL.

## Estado actual

La estrategia está definida completamente en los esquemas SQL, pero no hay implementación en la capa de aplicación.

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

1. Se inserta una fila en `tenant` con `status = 'PENDING_PROVISIONING'`
2. La aplicación crea el schema físico: `CREATE SCHEMA tenant_<nombre>;`
3. Se ejecuta `tenant_schema.sql` contra ese schema
4. Se actualiza `tenant.status = 'ACTIVE'`

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
- [ ] Implementar servicio de aprovisionamiento de nuevos tenants (crear schema, ejecutar tenant_schema.sql)
- [ ] Implementar lógica de suspensión/cancelación de tenants
- [ ] Agregar migraciones Flyway para schemas de tenant

## Preguntas abiertas

- ¿Cómo se resuelve el tenant en cada request? ¿Por subdominio (`tenant1.app.com`), header (`X-Tenant-ID`), o del JWT?
- ¿Se usará un pool de conexiones por tenant o un pool único con `search_path` dinámico?
- ¿Cómo se maneja la ejecución de migraciones Flyway en schemas de tenant existentes?
