# API

## Propósito

Documentar los endpoints REST expuestos por el backend.

## Estado actual

Implementado: `POST /api/v1/tenants` (creación de organización), `PUT /api/v1/tenants/identity-provider` (solo SERVICE, ADR-0012), y el flujo de documentos (crear, subir versión, obtener). El resto de endpoints se infieren del modelo de datos. El proyecto incluye `spring-boot-starter-webmvc`, confirmando API REST sobre Servlet.

### POST `/api/v1/tenants` — Crear organización (implementado)

Crea el tenant con su schema físico: `tenant` (`PENDING_PROVISIONING` → `ACTIVE`), `subscription` (ACTIVE), `tenant_usage`, `tenant_member` (admin), `tenant_identity_provider` (obligatorio), schema PostgreSQL migrado vía Flyway (`db/tenant`) y la API key inicial del admin (raw mostrada una sola vez). `schema_name` se deriva de `name` (`mv_` + slug). Requiere `planId` existente y activo (`404` si no); `201 Created` en éxito; `409` si `schema_name` duplicado; `400` si `name` no genera slug válido. Si el aprovisionamiento del schema falla, el tenant queda `SUSPENDED` y el endpoint responde `500`.

Request:
```json
{
  "name": "Acme Inc",
  "planId": "a6d05cd9-9043-4a58-9015-7cf30831b0d8",
  "admin": { "subject": "sub_123", "email": "admin@acme.com", "displayName": "Jane Doe" },
  "identityProvider": {
    "issuer": "https://idp.acme.com",
    "jwksUri": "https://idp.acme.com/.well-known/jwks.json",
    "audience": "https://api.acme.com",
    "allowedAlgorithms": ["RS256"],
    "clockSkewSeconds": 90
  }
}
```

`admin.displayName` opcional; `identityProvider` **obligatorio** (`audience` obligatorio); `allowedAlgorithms` (default `RS256`) y `clockSkewSeconds` (default `60`) opcionales con override.

Response `201 Created`:
```json
{
  "tenant": { "id": "...", "name": "Acme Inc", "schemaName": "mv_acme_inc", "status": "ACTIVE" },
  "subscription": { "id": "...", "planId": "...", "planCode": "PRO", "status": "ACTIVE" },
  "admin": { "memberId": "...", "subject": "sub_123", "email": "admin@acme.com", "displayName": "Jane Doe" },
  "identityProvider": { "issuer": "...", "jwksUri": "...", "audience": "...", "allowedAlgorithms": ["RS256"], "clockSkewSeconds": 90 },
  "apiKey": { "id": "...", "name": "Initial Admin Key", "keyPrefix": "mv_live_a1b2", "keyType": "SERVICE", "key": "mv_live_..." }
}
```

`apiKey.key` es la key raw y se devuelve **una única vez**; solo el hash (`key_hash`) se almacena en BD.

## Autenticación

Todos los endpoints excepto `POST /api/v1/tenants` requieren autenticación.

- **M2M (SERVICE):** la API key viaja en `Authorization: Bearer mv_live_...` (o `X-API-Key`).
- **Miembro humano (STANDARD + JWT):** el JWT viaja en `Authorization: Bearer` y la key STANDARD en `X-API-Key`. El JWT **nunca autentica solo** (ADR-0011): sin key STANDARD del mismo tenant → `401`. Un futuro JWT de platform_user usará su propio mecanismo.

`ApiKeyAuthenticationFilter` distingue la key del JWT por el prefijo `mv_live_`. Key inválida/revocada/expirada o falta de credenciales → `401` con `ErrorResponse` JSON. La autorización por endpoint la define cada controller con `@PreAuthorize` sobre el scope de la key (`SCOPE_<scope>`, ver Autenticacion.md); scope insuficiente → `403`.

Scopes requeridos por los endpoints implementados:

| Endpoint | Scope |
|---|---|
| `POST /api/v1/documents`, `POST /api/v1/documents/{id}/versions` | `documents:write` |
| `GET /api/v1/documents/{id}` | `documents:read` |
| `PUT /api/v1/tenants/identity-provider`, `PUT /api/v1/tenants/status` | `tenant:settings:write` (solo credencial SERVICE, ADR-0012) |

### PUT `/api/v1/tenants/identity-provider` — Actualizar identity provider (implementado)

Solo acepta credenciales SERVICE (llave maestra del tenant). El tenant operado es el del principal autenticado, no hay `tenantId` en la ruta (ADR-0012). Actualiza (upsert) la configuración OIDC/JWT del tenant. `404` si el tenant no existe; `400` con body inválido; `403` si la credencial no es SERVICE (p. ej. STANDARD+JWT o JWT puro); `200` con el DTO actualizado. Registra auditoría `TENANT_IDENTITY_PROVIDER_UPDATED`.

Request:
```json
{
  "issuer": "https://idp.acme.com/v2",
  "jwksUri": "https://idp.acme.com/v2/.well-known/jwks.json",
  "audience": "https://api.acme.com",
  "allowedAlgorithms": ["RS256"],
  "clockSkewSeconds": 120
}
```

`allowedAlgorithms` y `clockSkewSeconds` opcionales con defaults (`RS256` / `60`).

### PUT `/api/v1/tenants/status` — Actualizar estado del tenant (implementado)

Solo acepta credenciales SERVICE; el tenant operado es el del principal autenticado (ADR-0012). Cambia el estado de un tenant. Valida transiciones permitidas (ADR-0009). `404` si el tenant no existe; `400` con body inválido; `403` si la credencial no es SERVICE; `409` si la transición no es válida; `200` con el DTO de estado actualizado.

**Transiciones válidas:**

| Desde | Hacia |
|---|---|
| `PENDING_PROVISIONING` | `CANCELLED`, `SUSPENDED` |
| `ACTIVE` | `CANCELLED`, `SUSPENDED` |
| `SUSPENDED` | `ACTIVE` (reinstate), `CANCELLED` |
| `CANCELLED` | *(ninguna — terminal)* |

**Efectos por operación:**

| Operación | Suscripción | API Keys | Miembros |
|---|---|---|---|
| `CANCEL` | `CANCELLED` + `cancelled_at` | Revocadas | Desactivados |
| `SUSPEND` | `PAST_DUE` | Revocadas | Desactivados |
| `REINSTATE` | `ACTIVE` | No reactivadas (crear nuevas) | No reactivados |

Request:
```json
{
  "status": "CANCELLED",
  "reason": "business_closed"
}
```

`reason` opcional. Para `REINSTATE` se ignora.

Response `200 OK`:
```json
{
  "id": "...",
  "name": "Acme Inc",
  "previousStatus": "ACTIVE",
  "currentStatus": "CANCELLED",
  "suspendedAt": null,
  "suspendedReason": null
}
```

Auditoría: `TENANT_CANCELLED`, `TENANT_SUSPENDED`, `TENANT_REINSTATED`.

### Documentos (scope del tenant) — implementado

Los endpoints de documentos operan sobre el schema del tenant resuelto desde el principal autenticado (JWT o API key SERVICE). Los endpoints aceptan `multipart/form-data` con el archivo binario. Checksum SHA-256 y sizeBytes se calculan server-side desde los bytes reales del archivo (el cliente no los envía). El contenido binario se sube a Backblaze B2 (compatible S3) via `DocumentStorageService`: el checksum se calcula en streaming y el upload ocurre fuera de la transacción de BD (ADR-0010).

Validación de upload (`UploadPolicy`, config `multivault.upload.*`): archivo vacío o ausente → `400`; tamaño > `multivault.upload.max-size-bytes` (default 100 MB) → `413`; MIME fuera de `multivault.upload.allowed-mime-types` (lista separada por comas vía env `UPLOAD_ALLOWED_MIME_TYPES`; vacía = permitir todo) → `415`. Request multipart malformado o part `file` faltante → `400`. Superar la cuota de almacenamiento del plan (`tenant_usage.storage_bytes_used + size_bytes > plan.max_storage_bytes`) → `409` (ADR-0013). Fallos de almacenamiento → `500`.

El actor (`owner_user_id` del documento y `created_by` de cada versión) se resuelve así: con JWT → `memberId` del principal; con API key `SERVICE` → `ownerUserId` **obligatorio** en los form params (400 si falta). Al crear el documento, el trigger DB `trg_document_owner_permission` crea automáticamente la fila OWNER en `document_permission`. Las escrituras registran auditoría en `public.audit_log` (patrón ADR-0003): `DOCUMENT_CREATED` (recurso `document`) y `DOCUMENT_VERSION_UPLOADED` (recurso `document_version`), con `actor_type`/`api_key_id` según el principal, IP y User-Agent del request, y metadata con nombre y `version_number`. Las lecturas no se auditan.

#### POST `/api/v1/documents` — Crear documento (implementado)

Crea el documento (`status = ACTIVE`) + su versión v1 + repunta `current_version_id`. Sube el archivo a B2. `201 Created`; `400` con archivo vacío, part `file` faltante, `name` > 500 chars o falta de `ownerUserId` con key SERVICE; `409` si supera la cuota de almacenamiento del plan; `413` si excede `multivault.upload.max-size-bytes`; `415` si el MIME no está en la allowlist.

Request (`multipart/form-data`):
| Part | Tipo | Requerido | Descripción |
|---|---|---|---|
| `file` | MultipartFile | Sí | Archivo binario |
| `name` | String | No | Nombre del documento (default: nombre original del archivo) |
| `mimeType` | String | No | MIME type (default: content-type del archivo) |
| `folderId` | UUID | No | ID de la carpeta padre |
| `ownerUserId` | UUID | Con SERVICE key | Owner del documento |

Response `201 Created`:
```json
{
  "id": "...",
  "name": "Contract.pdf",
  "status": "ACTIVE",
  "currentVersion": {
    "id": "...",
    "versionNumber": 1,
    "name": "Contract.pdf",
    "storageKey": "mv_acme/aaa.../1/<checksum>",
    "mimeType": "application/pdf",
    "sizeBytes": 2048,
    "checksum": "<sha256 server-side>",
    "createdBy": "...",
    "createdAt": "2026-08-12T17:49:16.120537Z"
  }
}
```

#### POST `/api/v1/documents/{documentId}/versions` — Subir nueva versión (implementado)

Crea una versión inmutable con `version_number = max + 1` y repunta `current_version_id`. Sube el archivo a B2. `404` si el documento no existe o está borrado (soft delete). `201 Created` con el DTO de la versión. `400` con archivo vacío o `name` > 500 chars; `409` si supera la cuota de almacenamiento del plan; `413` si excede el tamaño máximo; `415` si el MIME no está en la allowlist. `ownerUserId` opcional con JWT, obligatorio con key SERVICE (puebla `created_by`).

Request (`multipart/form-data`):
| Part | Tipo | Requerido | Descripción |
|---|---|---|---|
| `file` | MultipartFile | Sí | Archivo binario |
| `name` | String | No | Nombre de la versión (default: nombre original del archivo) |
| `mimeType` | String | No | MIME type (default: content-type del archivo) |
| `ownerUserId` | UUID | Con SERVICE key | Actor que sube la versión |

#### GET `/api/v1/documents/{documentId}` — Obtener documento (implementado)

Devuelve el documento con su versión actual. `404` si no existe o pertenece a otro tenant (el aislamiento lo da el routing por schema del request). `200` con la misma estructura de `POST /api/v1/documents`.

## Información encontrada

Existen controladores REST (`TenantController` en `/api/v1/tenants`) sobre Servlet (`spring-boot-starter-webmvc`).

### Posibles endpoints inferidos del schema

#### Tenant Management
| Método | Path | Descripción |
|---|---|---|
| POST | `/api/v1/tenants` | Crear nuevo tenant |
| PUT | `/api/v1/tenants/identity-provider` | ✅ Actualizar identity provider (solo SERVICE, ADR-0012) |
| PUT | `/api/v1/tenants/status` | ✅ Actualizar estado (cancel/suspend/reinstate; solo SERVICE, ADR-0012) |
| GET | `/api/v1/tenants/{id}` | Obtener tenant |
| GET | `/api/v1/tenants` | Listar tenants |
| PATCH | `/api/v1/tenants/{id}` | Actualizar tenant |
| DELETE | `/api/v1/tenants/{id}` | Eliminar tenant |

#### Autenticación
| Método | Path | Descripción |
|---|---|---|
| POST | `/api/v1/auth/login` | Login de platform_user |
| POST | `/api/v1/auth/api-keys` | Crear API key |
| GET | `/api/v1/auth/api-keys` | Listar API keys |
| DELETE | `/api/v1/auth/api-keys/{id}` | Revocar API key |

#### Documentos (scope del tenant)
| Método | Path | Descripción |
|---|---|---|
| POST | `/api/v1/documents` | ✅ Crear documento |
| GET | `/api/v1/documents/{id}` | ✅ Obtener documento |
| GET | `/api/v1/documents` | Listar documentos |
| PATCH | `/api/v1/documents/{id}` | Actualizar documento |
| DELETE | `/api/v1/documents/{id}` | Eliminar documento (soft delete) |
| POST | `/api/v1/documents/{id}/versions` | ✅ Subir nueva versión |
| GET | `/api/v1/documents/{id}/versions` | Listar versiones |
| GET | `/api/v1/documents/{id}/versions/{versionId}` | Descargar versión (pendiente de endpoint de descarga) |

#### Carpetas (scope del tenant)
| Método | Path | Descripción |
|---|---|---|
| POST | `/api/v1/folders` | Crear carpeta |
| GET | `/api/v1/folders/{id}` | Obtener carpeta |
| GET | `/api/v1/folders` | Listar carpetas |
| PATCH | `/api/v1/folders/{id}` | Mover/renombrar carpeta |
| DELETE | `/api/v1/folders/{id}` | Eliminar carpeta (soft delete) |

#### Permisos (scope del tenant)
| Método | Path | Descripción |
|---|---|---|
| POST | `/api/v1/documents/{id}/permissions` | Conceder permiso |
| GET | `/api/v1/documents/{id}/permissions` | Listar permisos |
| PATCH | `/api/v1/documents/{id}/permissions/{userId}` | Cambiar nivel de permiso |
| DELETE | `/api/v1/documents/{id}/permissions/{userId}` | Revocar permiso |

## Pendientes

- [ ] Definir convención de versionado de API (`/api/v1/...`)
- [ ] Implementar el resto de controladores REST
- [ ] Definir formato de respuesta estándar (envoltura, códigos de error) — POST /tenants ya usa estructura anidada por recurso
- [ ] Documentar con OpenAPI/Swagger
- [x] Implementar validación de parámetros y cuerpos de request (`@Valid` en DTOs; uploads con `UploadPolicy`: 400/413/415)

## Preguntas abiertas

- ¿Se usará Spring REST Docs o SpringDoc OpenAPI para generar documentación?
- ¿Formato de respuesta: envoltura estándar (`{ data, error, meta }`) o respuestas planas?
- ¿Paginación: page/limit, cursor-based o ambas?
