# Storage

## Propósito

Documentar la estrategia de almacenamiento de documentos, que combina PostgreSQL para metadatos y Backblaze B2 (compatible S3) para el contenido binario.

## Estado actual

**Implementado.** Backblaze B2 como backend de object storage via AWS SDK v2 S3. Los endpoints de documentos (`POST /api/v1/documents`, `POST /api/v1/documents/{id}/versions`) aceptan `multipart/form-data` con el archivo binario. Checksum SHA-256 y sizeBytes se calculan server-side desde los bytes reales del archivo. `DocumentService` valida la cuota de almacenamiento del plan antes de subir el archivo y mantiene `tenant_usage.storage_bytes_used` con un `UPDATE` atómico (ADR-0013).

## Arquitectura de storage

### Paquetes

```
infrastructure/storage/
├── DocumentStorageService.java          ← Interfaz del puerto
└── backblaze/
    ├── BackblazeB2Properties.java       ← Properties: storage.backblaze-b2.*
    ├── BackblazeB2Config.java           ← Bean S3Client/S3Presigner (condicional)
    └── BackblazeB2StorageService.java   ← Implementación: upload/download/delete
```

### Bean condicional

Los beans de storage (`S3Client`, `S3Presigner`, `BackblazeB2StorageService`) solo se crean cuando `storage.backblaze-b2.enabled=true`. Esto permite:
- **Tests:** Sin B2 configurado, `DocumentService` requiere un `DocumentStorageService` mock (`@MockitoBean`)
- **Desarrollo local:** Configurar `enabled=true` en `application-local.yml` con credenciales reales
- **Producción:** Configurar via variables de entorno `B2_ENABLED=true`, `B2_ENDPOINT`, etc.

### Modelo de almacenamiento

```sql
CREATE TABLE document_version (
    id                   UUID PRIMARY KEY,
    document_id          UUID NOT NULL,
    version_number       INTEGER NOT NULL,
    name                 VARCHAR(500) NOT NULL,
    storage_key          VARCHAR(1000) NOT NULL,  -- B2 object key
    mime_type            VARCHAR(150) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    checksum             VARCHAR(128) NOT NULL,    -- SHA-256 hex (calculado server-side)
    metadata             JSONB DEFAULT '{}',
    created_by           UUID NOT NULL,
    created_by_snapshot  JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Flujo de upload

1. Controller recibe `multipart/form-data`: `file` (MultipartFile) + `name`, `mimeType`, `folderId`, `ownerUserId` (form params)
2. Service lee bytes del archivo → calcula SHA-256 y sizeBytes server-side
3. Crea `Document` en DB (status = ACTIVE)
4. Calcula `storageKey`: `{schema}/{documentId}/{versionNumber}/{checksum}`
5. Sube archivo a B2 con `DocumentStorageService.upload()`
6. Guarda `DocumentVersion` en DB con el storageKey
7. Repunta `current_version_id`
8. Publica auditoría `DOCUMENT_CREATED`

Si falla la subida a B2 (paso 5), la transacción completa hace rollback.

### Cuota de almacenamiento por plan

`Plan.max_storage_bytes` limita el almacenamiento total del tenant (seed: FREE 1GB / PRO 100GB / BUSINESS 500GB / ENTERPRISE 1TB). `DocumentService` (`create` y `addVersion`) consulta `StorageQuotaService`:

1. `assertCapacity(tenantId, sizeBytes)` valida que `tenant_usage.storage_bytes_used + sizeBytes <= plan.max_storage_bytes` **antes** de subir a B2. Si excede → `AlmacenamientoPlanExcedidoException` → HTTP 409 y no se crea ni sube nada.
2. Dentro de la misma transacción que persiste la `document_version`, se ejecuta el `UPDATE` atómico `tenant_usage.storage_bytes_used = storage_bytes_used + ?`. Al ir en la misma TX, si falla la subida/guardado el rollback deja el contador sin desincronizar.

El plan del tenant se resuelve vía `Tenant.current_plan_id` → `plan.code`. Ver ADR-0013.

### Naming del storage key

```
{schema}/{documentId}/{versionNumber}/{checksum}
```

Ejemplo: `mv_acme_inc/a1b2c3d4-.../1/e3b0c44298fc...`

### Configuración

**application.yaml** (producción — variables de entorno):
```yaml
storage:
  backblaze-b2:
    enabled: ${B2_ENABLED:false}
    endpoint: ${B2_ENDPOINT:}
    region: ${B2_REGION:}
    access-key: ${B2_ACCESS_KEY:}
    secret-key: ${B2_SECRET_KEY:}
    bucket: ${B2_BUCKET:}
```

**application-local.yml** (desarrollo):
```yaml
storage:
  backblaze-b2:
    enabled: true
    endpoint: https://s3.<region>.backblazeb2.com
    region: us-west-004
    access-key: <your-b2-application-key-id>
    secret-key: <your-b2-application-key>
    bucket: <your-bucket-name>
```

## Pendientes

- [x] Agregar dependencia AWS SDK S3 a `pom.xml`
- [x] Configurar propiedades de conexión a B2 en `application.yaml`
- [x] Implementar `DocumentStorageService` para upload/download/delete
- [x] Definir política de naming para `storage_key`
- [x] Checksum server-side (SHA-256 calculado desde bytes reales)
- [ ] Implementar verificación de checksum en descargas
- [ ] Definir TTL/limpieza de versiones eliminadas lógicamente
- [ ] Endpoint de descarga (`GET /api/v1/documents/{id}/versions/{versionId}`)
