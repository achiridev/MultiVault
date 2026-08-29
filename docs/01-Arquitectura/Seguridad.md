# Seguridad

## Propósito

Documentar las consideraciones de seguridad del sistema, controles implementados y pendientes.

## Estado actual

Existen controles de seguridad a nivel de base de datos (constraints, checks, índices parciales). Spring Security está configurado en `infrastructure/security/config/SecurityConfig`: CSRF deshabilitado, sesiones `STATELESS`, `POST /api/v1/tenants` público (onboarding self-serve) y el resto de endpoints autenticados. Autenticación por API key implementada (`ApiKeyAuthenticationFilter`): valida la key en cada request con caché Redis y responde `401` con `ErrorResponse` JSON (`RestAuthenticationEntryPoint`, en `infrastructure.security.handler`). Autenticación JWT multi-issuer implementada (`JwtAuthenticationFilter`): valida la firma contra el JWKS del tenant (cacheado en Redis), exige `iss`/`aud` configurados, hace upsert de `tenant_member` y **no autentica un JWT sin una key STANDARD del mismo tenant** (ADR-0011). Los scopes de la key se evalúan con `@EnableMethodSecurity` + `@PreAuthorize` (`SCOPE_<scope>`) en los controllers; scope insuficiente → `403` (`AccessDeniedException` mapeado en `GlobalExceptionHandler`). Login de platform_user pendiente.

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

### Auditoría de eventos

El paquete `dev.achiri.multivault.audit` implementa la auditoría con eventos de aplicación (ver ADR-0003): los servicios publican `AuditEvent` vía `AuditEventPublisher` y el listener persiste en `audit_log` solo cuando la transacción de negocio commiteó (`AFTER_COMMIT` + `REQUIRES_NEW`). El log queda así desacoplado del negocio y no revierte operaciones por fallos de auditoría.

### Soft deletes

- `folder.deleted_at` — borrado lógico de carpetas (no purge programado)
- `document.deleted_at` — borrado lógico de documentos

### Particionamiento de responsabilidades

- **TENANT_USER:** Usuario final autenticado vía JWT de su tenant
- **PLATFORM_STAFF:** Staff interno (SUPER_ADMIN, SUPPORT)
- **SYSTEM:** Acciones automáticas del sistema
- **API_KEY:** Integraciones machine-to-machine

## Pendientes

- [x] Configurar Spring Security con cadena de filtros (`SecurityConfig`)
- [ ] Implementar `SecurityFilterChain` con CORS, rate limiting — CSRF ya deshabilitado (API stateless)
- [x] Implementar autenticación por API keys (`ApiKeyAuthenticationFilter`) y JWT multi-issuer (`JwtAuthenticationFilter`) — login de platform_user pendiente
- [x] Definir `@PreAuthorize` / `@PostAuthorize` en los controladores (implementados: documentos y tenant settings; el resto de endpoints pendientes de implementar)
- [x] Implementar validación de scopes de API keys (`SCOPE_<scope>`, método-level; faltará constraint del catálogo al crear keys por API)
- [ ] Implementar rate limiting por tenant y por API key
- [ ] Aplicar REVOKE a nivel de base de datos para `audit_log`
- [x] Implementar infraestructura de auditoría de eventos (paquete `audit/` + ADR-0003) — falta cubrir eventos específicos de seguridad (logins fallidos, keys revocadas)
- [ ] Definir política de contraseñas para platform_user
- [ ] Configurar HTTPS/TLS
- [ ] Implementar protección contra ataques comunes (XSS, CSRF, SQL injection, etc.)

## Preguntas abiertas

- ¿Se requiere cumplimiento con SOC2, ISO 27001 o similares?
- ¿Se necesita un WAF (Web Application Firewall)?
- ¿Cómo se maneja la rotación de secrets (JWKS keys, API keys)?
- ¿Se implementa cifrado del lado del cliente para documentos sensibles?
- ¿Hay requerimientos de Data Residency / GDPR?
