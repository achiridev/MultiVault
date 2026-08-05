# Servicios

## Propósito

Documentar la capa de servicios de la aplicación, sus responsabilidades y dependencias.

## Estado actual

Implementado `TenantService.create` (creación transaccional de organización: tenant + subscription ACTIVE + identity provider + admin member). El resto se infiere de las entidades y la funcionalidad esperada.

## Información encontrada

El diseño de los servicios se infiere de las entidades y la funcionalidad esperada.

### Posibles servicios

| Servicio | Responsabilidades |
|---|---|
| `TenantService` ✅ | CRUD tenants, aprovisionamiento de schema, suspensión/cancelación — `create` implementado |
| `SubscriptionService` | Gestión de suscripciones, cambio de plan, facturación |
| `PlanService` | CRUD de planes (solo SUPER_ADMIN) |
| `TenantIdentityProviderService` | CRUD de config OIDC por tenant |
| `ApiKeyService` | Creación, revocación, validación de API keys |
| `PlatformUserService` | CRUD de staff, login, cambio de contraseña |
| `TenantMemberService` | Upsert de miembros desde JWT, activación/desactivación |
| `FolderService` | CRUD carpetas, mover, path materializado |
| `DocumentService` | CRUD documentos, cambio de versión actual, archive |
| `DocumentVersionService` | Subida de versiones, descarga, verificación checksum |
| `DocumentPermissionService` | CRUD permisos, verificación de acceso |
| `AuditLogService` | Registro de eventos de auditoría |
| `TenantUsageService` | Actualización de contadores de cuota |
| `StorageService` | Interacción con S3/MinIO para subida/descarga |

### Posibles reglas de negocio

- Solo puede haber una suscripción ACTIVE por tenant
- Solo un OWNER por documento
- El OWNER no puede ser degradado ni removido
- No se permite crear carpetas con el mismo nombre en la misma raíz/parent
- El path materializado se actualiza al mover carpetas
- Al crear un documento, se inserta automáticamente el permiso OWNER (via trigger DB)
- Los audit_log son insert-only

## Pendientes

- [x] Implementar `TenantService.create` (organización)
- [ ] Implementar resto de servicios del schema público
- [ ] Implementar servicios del schema de tenant
- [ ] Implementar servicio de almacenamiento S3/MinIO
- [ ] Implementar validación de reglas de negocio
- [ ] Implementar manejo de excepciones y mensajes de error
- [ ] Definir transaccionalidad (@Transactional)

## Preguntas abiertas

- ¿Se usará @Transactional a nivel de servicio o de repositorio?
- ¿Los servicios expondrán interfaces o serán clases directas?
- ¿Cómo se propagan las excepciones entre capas?
