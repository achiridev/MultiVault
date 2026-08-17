# Servicios

## Propósito

Documentar la capa de servicios de la aplicación, sus responsabilidades y dependencias.

## Estado actual

`TenantService.create` (orquestador, no transaccional) coordina el aprovisionamiento de organización en tres etapas (ADR-0004): `TenantProvisioningService.initialize` (TX 1: `tenant` PENDING_PROVISIONING + `subscription` ACTIVE + `tenant_usage` + `tenant_member` + `identity_provider` obligatorio, ADR-0006), `TenantSchemaProvisioner.provision` (CREATE SCHEMA + Flyway por tenant, fuera de transacción) y `TenantProvisioningService.activate` (TX 2: tenant ACTIVE + `current_plan_id`, API key inicial del admin vía `ApiKeyService.createInitial`, auditoría `TENANT_CREATED`). `TenantService.updateIdentityProvider` actualiza la config OIDC vía `PUT /api/v1/tenants/{id}/identity-provider` (auditoría `TENANT_IDENTITY_PROVIDER_UPDATED`). El mapeo DTO ↔ Entidad lo generan mappers MapStruct (`tenant/mapper/`, `subscription/mapper/`, `document/mapper/`) según ADR-0002. `ApiKeyService` genera la key raw (`mv_live_` + 40 hex), persiste solo su hash SHA-256 y la devuelve una única vez.

`TenantLifecycleService` gestiona el ciclo de vida del tenant vía `PUT /api/v1/tenants/{id}/status` (ADR-0009): `updateStatus` valida la transición, aplica los efectos colaterales (cancelar/suscripción, revocar keys, desactivar miembros) y registra auditoría (`TENANT_CANCELLED`, `TENANT_SUSPENDED`, `TENANT_REINSTATED`). Cancelación es permanente; suspensión es reversible (reinstate solo desde `SUSPENDED`).

`DocumentService` (`document/service/`, `@Transactional`) implementa el flujo de documentos metadata-only (ADR-0009 / decisión de storage): `create` inserta `document` (status ACTIVE; el trigger DB crea la fila OWNER) + versión v1 y repunta `current_version_id`; `addVersion` calcula `version_number = max + 1` (la UNIQUE `(document_id, version_number)` cubre la concurrencia), crea la versión inmutable y repunta el current; `get` devuelve el documento con su versión actual. El `storageKey` lo genera la app como `{schema}/{documentId}/{versionNumber}/{checksum}`; el actor (`owner_user_id`/`created_by`) lo resuelve `DocumentController` desde el principal (JWT → `memberId`; key SERVICE → `ownerUserId` del body, obligatorio).

## Información encontrada

El diseño de los servicios se infiere de las entidades y la funcionalidad esperada.

### Posibles servicios

| Servicio | Responsabilidades |
|---|---|
| `TenantService` ✅ | CRUD tenants, orquesta aprovisionamiento — `create` implementado (no transaccional) |
| `TenantProvisioningService` ✅ | TX de onboarding (`tenant/provisioning/`): `initialize`, `markProvisioningFailed`, `activate` |
| `TenantLifecycleService` ✅ | Ciclo de vida (`tenant/service/`): `updateStatus` con cancel/suspend/reinstate, validación de transiciones |
| `TenantSchemaProvisioner` ✅ | CREATE SCHEMA + Flyway por tenant (`infrastructure/persistence/tenant`) |
| `ApiKeyService` ✅ | Creación de API keys (generación raw + hash SHA-256); revocación/validación pendientes |
| `SubscriptionService` | Gestión de suscripciones, cambio de plan, facturación |
| `PlanService` | CRUD de planes (solo SUPER_ADMIN) |
| `TenantIdentityProviderService` | CRUD de config OIDC por tenant — la actualización ya vive en `TenantService.updateIdentityProvider` |
| `PlatformUserService` | CRUD de staff, login, cambio de contraseña |
| `TenantMemberService` ✅ | Upsert de miembros desde JWT, activación/desactivación |
| `FolderService` | CRUD carpetas, mover, path materializado |
| `DocumentService` ✅ | CRUD documentos — `create`/`addVersion`/`get` implementados (metadata-only) |
| `DocumentVersionService` | Subida de versiones, descarga, verificación checksum — el alta de versiones vive en `DocumentService.addVersion` |
| `DocumentPermissionService` | CRUD permisos, verificación de acceso — el OWNER inicial lo crea el trigger DB |
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
- `max_users` limita los miembros activos por tenant (`tenant_member`); no aplica a `platform_user` ni `api_key`. **Enforcement pendiente**: no existe trigger en `tenant_usage.user_count` ni validación en app (ver BaseDatos)
- Los audit_log son insert-only

## Pendientes

- [x] Implementar `TenantService.create` (organización + aprovisionamiento de schema + API key inicial)
- [x] Implementar `TenantLifecycleService` (cancel/suspend/reinstate con validación de transiciones)
- [ ] Implementar enforcement de `max_users` (trigger `tenant_usage.user_count` o validación en app)
- [ ] Implementar resto de servicios del schema público
- [x] Implementar `DocumentService` del schema de tenant (`create`/`addVersion`/`get`); pendientes folder y permissions
- [ ] Implementar servicio de almacenamiento S3/MinIO (pendiente de ADR de storage)
- [ ] Implementar validación de reglas de negocio
- [ ] Implementar manejo de excepciones y mensajes de error
- [x] Definir transaccionalidad (@Transactional): etapas de onboarding (ADR-0004)

## Preguntas abiertas

- ¿Los servicios expondrán interfaces o serán clases directas?
- ¿Cómo se propagan las excepciones entre capas?
- ¿Cómo se reintenta un tenant con aprovisionamiento fallido (retry manual o automático)?
