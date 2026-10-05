# Seguridad

## Propósito

Documentar las consideraciones de seguridad del sistema, controles implementados y pendientes.

## Estado actual

Existen controles de seguridad a nivel de base de datos (constraints, checks, índices parciales). Spring Security está configurado en `infrastructure/security/config/SecurityConfig`: CSRF deshabilitado, sesiones `STATELESS`, `POST /api/v1/tenants` público (onboarding self-serve) y el resto de endpoints autenticados. Autenticación por API key implementada (`ApiKeyAuthenticationFilter`): valida la key en cada request con caché Redis y responde `401` con `ErrorResponse` JSON (`RestAuthenticationEntryPoint`, en `infrastructure.security.handler`). Autenticación JWT multi-issuer implementada (`JwtAuthenticationFilter`): valida la firma contra el JWKS del tenant (cacheado en Redis), exige `iss`/`aud` configurados, resuelve la clave por match estricto de `kid` sin fallback a otra clave del JWKS (ADR-0014), hace upsert de `tenant_member` y **no autentica un JWT sin una key STANDARD del mismo tenant** (ADR-0011). Los scopes de la key se evalúan con `@EnableMethodSecurity` + `@PreAuthorize` (`SCOPE_<scope>`) en los controllers; scope insuficiente → `403` (`AccessDeniedException` mapeado en `GlobalExceptionHandler`). Los endpoints de configuración del tenant (`identity-provider`, `status`) son service-to-service: solo aceptan credenciales `SERVICE` y derivan el `tenantId` del principal autenticado (`CurrentTenant.serviceTenantId()`), sin path, cerrando el vector IDOR cross-tenant (ADR-0012). Login de platform_user pendiente.

### Control anti-SSRF sobre `jwks_uri` (ADR-0015)

`jwks_uri` es una URL de entrada controlada por el tenant y el backend la pide en cada verificación de firma, así que sin validación era un vector SSRF exploitable incluso sin autenticación (`POST /api/v1/tenants` es público y `verifyWithRetry` dispara un refetch ante cualquier `kid` desconocido, ADR-0014). Se implementaron dos capas:

**Capa 1 — `JwksUriPolicy`, antes de persistir.** Se ejecuta en `TenantService.create` y `TenantService.updateIdentityProvider`, antes de cualquier escritura (si la URL es inválida no se inserta nada, no se crea el schema y no queda un tenant en `SUSPENDED`):

| Control | Rechaza |
|---|---|
| Esquema | Todo distinto de `https` (configurable con `allow-http`) |
| Estructura | URI relativa, host vacío, `userInfo` embebido, fragmento, longitud > 500 (límite de la columna) |
| Puerto | Cualquiera fuera de `allowed-ports` (default `443`) |
| Allowlist | Host fuera de `allowed-hosts`, si se configura |
| Resolución | Cualquier IP resuelta que sea loopback, site-local, link-local, any-local, multicast, `0.0.0.0/8`, `100.64.0.0/10`, `198.18.0.0/15`, `224.0.0.0/4`+, ULA `fc00::/7`, `fe80::/10` o IPv4-mapped |
| Nombres | Sufijos `.localhost`, `.local`, `.internal` (rechazados por nombre, sin DNS) |
| DNS | `UnknownHostException` |

Esto cierra el caso grave —`http://169.254.169.254/latest/meta-data/...` y las redes internas— **sin emitir un solo paquete**, porque el rechazo ocurre en la resolución del host.

**Capa 2 — probe de alcanzabilidad.** `JwksProvider.probe` ejecuta la misma política y luego un GET con timeout que exige `200` y ≥1 clave RSA con `n`/`e` utilizables. Evita el tenant `ACTIVE` con IdP roto. Los fallos de red y de contenido devuelven un **único** mensaje (`jwks_uri rechazada: no alcanzable o sin un JWKS válido`) para no Regalarle al atacante un oráculo de tres estados; se mapea a `400`.

**Capa 3 — defensa en runtime.** `JwksProvider.fetch` vuelve a validar antes de cada `send`, lo que cubre las filas ya persistidas y cualquier camino futuro que escriba `tenant_identity_provider` sin pasar por el servicio. `MultiIssuerJwtDecoder` traduce el rechazo a `InvalidJwtException` → `401`, no `500`.

**Hardening del cliente HTTP.** `HttpClient.Redirect.NEVER` explícito (un `302` en el JWKS no se sigue) y `BodyHandlers.ofInputStream` con corte por `max-body-bytes` (256 KB), antes `ofString` no tenía techo y un endpoint hostil podía envenenar el caché Redis `jwks`.

Configuración en `multivault.jwks` (`JwksProperties`). `application-test.yaml` relaja `allow-http`, `allow-private-hosts`, `allowed-ports` y `verify-reachability` porque los tests sirven el JWKS con el `HttpServer` del JDK sobre `localhost`; `JwksUriProvisioningTest` y `JwksReachabilityProbeTest` revierten esos defaults para ejercitar la política en modo estricto.

**Riesgo residual (documentado en el ADR, no implementado):** validar antes de enviar no cierra la ventana de DNS rebinding entre `getAllByName` y el connect del `HttpClient`. La mitigación definitiva es un proxy de egreso con allowlist o un `HttpClient` que fije la IP validada preservando SNI.

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
- [ ] Implementar CORS en `SecurityFilterChain` — CSRF ya deshabilitado (API stateless); el rate limiting ya está implementado (ADR-0016)
- [x] Implementar autenticación por API keys (`ApiKeyAuthenticationFilter`) y JWT multi-issuer (`JwtAuthenticationFilter`) — login de platform_user pendiente
- [x] Validar `jwks_uri` (https, host público, JWKS alcanzable) en provisioning y en cada fetch — ADR-0015
- [x] Definir `@PreAuthorize` / `@PostAuthorize` en los controladores (implementados: documentos y tenant settings; el resto de endpoints pendientes de implementar)
- [x] Implementar validación de scopes de API keys (`SCOPE_<scope>`, método-level; faltará constraint del catálogo al crear keys por API)
- [x] Rate limiting distribuido por IP con corte global, `Idempotency-Key` y aprovisionamiento asíncrono en `POST /api/v1/tenants` — ADR-0016
- [x] Resolución de IP real detrás de proxies de confianza (`TrustedProxyClientIpResolver`), compartida por auditoría y rate limiting
- [ ] Implementar rate limiting por tenant y por API key (`max_requests_per_minute` del plan sigue sin enforcement)
- [ ] Aplicar REVOKE a nivel de base de datos para `audit_log`
- [x] Implementar infraestructura de auditoría de eventos (paquete `audit/` + ADR-0003) — falta cubrir eventos específicos de seguridad (logins fallidos, keys revocadas)
- [ ] Definir política de contraseñas para platform_user
- [ ] Configurar HTTPS/TLS
- [ ] Proteger el egress con allowlist de destinos (cierra el DNS rebinding que deja abierto la validación de `jwks_uri`)
- [ ] Implementar protección contra ataques comunes (XSS, CSRF, SQL injection, etc.)

## Preguntas abiertas

- ¿Se requiere cumplimiento con SOC2, ISO 27001 o similares?
- ¿Se necesita un WAF (Web Application Firewall)?
- ¿Cómo se maneja la rotación de secrets (JWKS keys, API keys)?
- ¿Se implementa cifrado del lado del cliente para documentos sensibles?
- ¿Hay requerimientos de Data Residency / GDPR?
- ~~¿Se aplica rate limiting a `POST /api/v1/tenants`?~~ **Resuelto por [ADR-0016](../06-Decisiones/ADR-0016.md)**: 3 tenants/hora por IP con corte global de 200/hora, `Idempotency-Key` y aprovisionamiento asíncrono, así que el request anónimo ya no paga `CREATE SCHEMA` ni Flyway.
- ¿Se aplica un límite por tenant y por API key a partir de `plan.max_requests_per_minute`? La columna está sembrada desde `V2__plan_seed.sql` (FREE 60, PRO 1000, BUSINESS 3000, ENTERPRISE 6000) pero nadie la lee. La decisión de no meter una lectura de `plan` en el camino caliente está en ADR-0016.
