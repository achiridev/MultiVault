# Storage

## Propósito

Documentar la estrategia de almacenamiento de documentos, que combina PostgreSQL para metadatos y un sistema de object storage (S3/MinIO) para el contenido binario.

## Estado actual

La referencia al almacenamiento externo existe únicamente en la tabla `document_version.storage_key`. No hay configuración de S3/MinIO en `application.yaml` ni dependencias en `pom.xml`.

## Información encontrada

### Modelo de almacenamiento

```sql
CREATE TABLE document_version (
    id                   UUID PRIMARY KEY,
    document_id          UUID NOT NULL,
    version_number       INTEGER NOT NULL,
    name                 VARCHAR(500) NOT NULL,
    storage_key          VARCHAR(1000) NOT NULL,  -- S3/MinIO object key
    mime_type            VARCHAR(150) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    checksum             VARCHAR(128) NOT NULL,    -- SHA-256 hex
    metadata             JSONB DEFAULT '{}',
    created_by           UUID NOT NULL,
    created_by_snapshot  JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- El contenido binario del documento se almacena en S3/MinIO
- `storage_key` es la clave del objeto en el bucket
- `checksum` (SHA-256) permite verificar integridad
- Las versiones son inmutables: nunca se hace UPDATE del contenido

### Versionado

- Cada nuevo upload crea una fila en `document_version` con `version_number` incremental
- `document.current_version_id` se repunta a la última versión
- Las versiones anteriores preservan su contenido inmutables

## Pendientes

- [ ] Agregar dependencia AWS SDK S3 o MinIO client a `pom.xml`
- [ ] Configurar propiedades de conexión a S3/MinIO en `application.yaml`
- [ ] Implementar `DocumentStorageService` para upload/download/delete
- [ ] Decidir estructura de buckets: ¿un bucket global o uno por tenant?
- [ ] Definir política de naming para `storage_key`
- [ ] Implementar verificación de checksum en descargas
- [ ] Definir TTL/limpieza de versiones eliminadas lógicamente

## Preguntas abiertas

- ¿Se usará AWS S3, MinIO, o ambos (S3 para producción, MinIO para desarrollo)?
- ¿Los archivos se sirven con redirect 302 o se descargan a través del backend?
- ¿Hay límite de tamaño por documento/versión?
- ¿Se implementa cifrado del lado del cliente antes de subir a S3?
