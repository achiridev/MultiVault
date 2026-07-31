# Seguridad

## Propósito

Documentar las consideraciones de seguridad del sistema, controles implementados y pendientes.

## Estado actual

Existen controles de seguridad a nivel de base de datos (constraints, checks, índices parciales). No hay implementación de Spring Security ni configuración de seguridad en la capa de aplicación.

## Información encontrada

### Controles a nivel de base de datos

| Control | Ubicación | Descripción |
|---|---|---|
| Prohibición algoritmo 'none' | `tenant_identity_provider` | CHECK: `NOT ('none' = ANY(allowed_algorithms))` |
| Hash de API keys | `api_key.key_hash` | Solo se almacena el hash, la key raw se muestra una vez |
| Hash de contraseñas | `platform_user.password_hash` | Se almacena hash, no texto plano |
| Índice único parcial | `api_key.key_hash WHERE revoked_at IS NULL` | Evita duplicados de keys activas |
| CHECK en status | Varias tablas | Validación de valores permitidos en campos de estado |
| ON DELETE CASCADE | Varias FKs | Limpieza en cascada al eliminar entidades padre |

### WORM (Write-Once-Read-Many) en audit_log

El `audit_log` está diseñado como insert-only. La nota en el schema indica que se deben aplicar `REVOKE` a nivel de base de datos para prevenir UPDATE/DELETE por el rol de la aplicación.

### Soft deletes

- `folder.deleted_at` — borrado lógico de carpetas (no purge programado)
- `document.deleted_at` — borrado lógico de documentos

### Particionamiento de responsabilidades

- **TENANT_USER:** Usuario final autenticado vía JWT de su tenant
- **PLATFORM_STAFF:** Staff interno (SUPER_ADMIN, SUPPORT)
- **SYSTEM:** Acciones automáticas del sistema
- **API_KEY:** Integraciones machine-to-machine

## Pendientes

- [ ] Configurar Spring Security con cadena de filtros
- [ ] Implementar `SecurityFilterChain` con CORS, CSRF, rate limiting
- [ ] Definir `@PreAuthorize` / `@PostAuthorize` en los controladores
- [ ] Implementar validación de scopes de API keys
- [ ] Implementar rate limiting por tenant y por API key
- [ ] Aplicar REVOKE a nivel de base de datos para `audit_log`
- [ ] Implementar auditoría de eventos de seguridad (logins fallidos, keys revocadas)
- [ ] Definir política de contraseñas para platform_user
- [ ] Configurar HTTPS/TLS
- [ ] Implementar protección contra ataques comunes (XSS, CSRF, SQL injection, etc.)

## Preguntas abiertas

- ¿Se requiere cumplimiento con SOC2, ISO 27001 o similares?
- ¿Se necesita un WAF (Web Application Firewall)?
- ¿Cómo se maneja la rotación de secrets (JWKS keys, API keys)?
- ¿Se implementa cifrado del lado del cliente para documentos sensibles?
- ¿Hay requerimientos de Data Residency / GDPR?
