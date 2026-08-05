# API

## Propósito

Documentar los endpoints REST expuestos por el backend.

## Estado actual

Implementado: `POST /api/v1/tenants` (creación de organización). El resto de endpoints se infieren del modelo de datos. El proyecto incluye `spring-boot-starter-webmvc`, confirmando API REST sobre Servlet.

### POST `/api/v1/tenants` — Crear organización (implementado)

Crea de forma transaccional: `tenant`, `subscription` (ACTIVE), `tenant_identity_provider` y `tenant_member` (admin). `schema_name` se deriva de `name` (`mv_` + slug). Requiere `planId` existente y activo (`404` si no); `201 Created` en éxito. `audience` del identity provider es obligatorio (columna NOT NULL).

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

`admin.displayName` opcional; `allowedAlgorithms` (default `RS256`) y `clockSkewSeconds` (default `60`) opcionales con override.

Response `201 Created`:
```json
{
  "tenant": { "id": "...", "name": "Acme Inc", "schemaName": "mv_acme_inc", "status": "PENDING_PROVISIONING" },
  "subscription": { "id": "...", "planId": "...", "planCode": "PRO", "status": "ACTIVE" },
  "admin": { "memberId": "...", "subject": "sub_123", "email": "admin@acme.com", "displayName": "Jane Doe" },
  "identityProvider": { "issuer": "...", "jwksUri": "...", "audience": "...", "allowedAlgorithms": ["RS256"], "clockSkewSeconds": 90 }
}
```

`status` queda `PENDING_PROVISIONING` hasta que se implemente el aprovisionamiento físico del schema por tenant.

## Información encontrada

Sin controladores REST en el código fuente. El proyecto incluye `spring-boot-starter-webmvc` como dependencia, lo que confirma que la API será REST sobre Servlet.

### Posibles endpoints inferidos del schema

#### Tenant Management
| Método | Path | Descripción |
|---|---|---|
| POST | `/api/v1/tenants` | Crear nuevo tenant |
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
