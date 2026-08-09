# API

## Propósito

Documentar los endpoints REST expuestos por el backend.

## Estado actual

Implementado: `POST /api/v1/tenants` (creación de organización). El resto de endpoints se infieren del modelo de datos. El proyecto incluye `spring-boot-starter-webmvc`, confirmando API REST sobre Servlet.

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
  "apiKey": { "id": "...", "name": "Initial Admin Key", "keyPrefix": "mv_live_a1b2", "keyType": "STANDARD", "key": "mv_live_..." }
}
```

`apiKey.key` es la key raw y se devuelve **una única vez**; solo el hash (`key_hash`) se almacena en BD.

### PUT `/api/v1/tenants/{tenantId}/identity-provider` — Actualizar identity provider (implementado)

Actualiza (upsert) la configuración OIDC/JWT del tenant. `404` si el tenant no existe; `400` con body inválido; `200` con el DTO actualizado. Registra auditoría `TENANT_IDENTITY_PROVIDER_UPDATED`.

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

## Información encontrada

Existen controladores REST (`TenantController` en `/api/v1/tenants`) sobre Servlet (`spring-boot-starter-webmvc`).

### Posibles endpoints inferidos del schema

#### Tenant Management
| Método | Path | Descripción |
|---|---|---|
| POST | `/api/v1/tenants` | Crear nuevo tenant |
| PUT | `/api/v1/tenants/{id}/identity-provider` | Actualizar identity provider |
| GET | `/api/v1/tenants/{id}` | Obtener tenant |
| GET | `/api/v1/tenants` | Listar tenants |
| PATCH | `/api/v1/tenants/{id}` | Actualizar tenant |
| DELETE | `/api/v1/tenants/{id}` | Cancelar/suspender tenant |

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
| POST | `/api/v1/documents` | Crear documento |
| GET | `/api/v1/documents/{id}` | Obtener documento |
| GET | `/api/v1/documents` | Listar documentos |
| PATCH | `/api/v1/documents/{id}` | Actualizar documento |
| DELETE | `/api/v1/documents/{id}` | Eliminar documento (soft delete) |
| POST | `/api/v1/documents/{id}/versions` | Subir nueva versión |
| GET | `/api/v1/documents/{id}/versions` | Listar versiones |
| GET | `/api/v1/documents/{id}/versions/{versionId}` | Descargar versión |

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
- [ ] Implementar validación de parámetros y cuerpos de request

## Preguntas abiertas

- ¿Se usará Spring REST Docs o SpringDoc OpenAPI para generar documentación?
- ¿Formato de respuesta: envoltura estándar (`{ data, error, meta }`) o respuestas planas?
- ¿Paginación: page/limit, cursor-based o ambas?
