# Testing

## Propósito

Documentar la estrategia de testing del proyecto, herramientas y cobertura.

## Estado actual

Tests de integración con **Testcontainers + PostgreSQL real** (postgres:16-alpine) en un contenedor compartido, más tests unitarios con Mockito. Todos corren con `mvn test` (requiere Docker corriendo).

### Tests existentes

| Test | Tipo | Cubre |
|---|---|---|
| `MultivaultApplicationTests` | Integración | Contexto Spring carga contra PostgreSQL de Testcontainers |
| `TenantProvisioningTest` | Integración | Onboarding completo: tenant ACTIVE, schema creado, api key, audit, idempotencia, trigger owner permission, validaciones |
| `TenantContextTest` | Unitario | `TenantContext` (ThreadLocal): default null, set/get/clear, aislamiento entre hilos |
| `TenantSchemaIsolationTest` | Integración | Aislamiento por schema: con `TenantContext` en schema A, las queries JPA de `document` caen en A (no en B); tablas públicas consultables sin contexto |
| `TenantContextFilterTest` | Integración | `TenantContextFilter`: resuelve el schema desde el principal JWT y desde API key; contexto limpio en requests anónimos y tras el request |
| `DocumentFlowIntegrationTest` | Integración | Flujo de documentos con API key SERVICE real (MockMvc): crear documento → fila `document` + versión v1 + `current_version_id` repuntado + fila OWNER (trigger) + `created_by` poblado; `addVersion` (v2 inmutable, repunte); aislamiento (GET con key de otro tenant → 404); `ownerSubject` obligatorio para SERVICE (400), creación del miembro para un `sub` que nunca ha hecho login, resolución del `sub` dentro del tenant del caller y rechazo del miembro inactivo (ADR-0017) |
| `AuditEventPublishingTest` | Integración | Auditoría AFTER_COMMIT (persiste en commit, no en rollback) |
| `ApiKeyServiceTest` | Unitario | Generación de api key (raw mostrada una vez, solo hash almacenado) |
| `ApiKeyAuthenticatorTest` | Unitario | Resolución de api key por hash: mapeo a `ApiKeyIdentity` (expiración → epoch, null → 0), no encontrada → null |
| `ApiKeyFilterIntegrationTest` | Integración | Autenticación por API key: `SERVICE` autentica (Bearer y `X-API-Key`), `STANDARD` sin JWT/revocada/expirada/desconocida/JWT-like → 401, cuerpo 401 como `ErrorResponse`, prioridad de `SERVICE` sobre JWT |
| `JwtAuthenticationFilterTest` | Unitario | Filtro JWT: scopes de key `STANDARD` → authorities `SCOPE_*`, scope `*` expandido al catálogo completo, JWT inválido limpia el contexto, token `mv_live_` y auth previa saltan el decode (sin key STANDARD ni con key SERVICE tampoco se decodifica), tenants distintos STANDARD/JWT → sin autenticar |
| `ScopeAuthoritiesTest` | Unitario | Mapeo scopes → authorities: `*` expandido a todo el catálogo, scopes explícitos en orden, lista vacía, deduplicación (wildcard + scope explícito, scope repetido) |
| `JwksProviderTest` | Unitario | Fetch JWKS contra `HttpServer` del JDK: parseo OK (simple y múltiple, con clave no RSA), HTTP ≠ 200, JSON inválido, sin campo `keys` → `IllegalStateException`, `302` **no** seguido (`Redirect.NEVER`), body sobre `max-body-bytes` → error, rechazo de loopback y de `http` en modo estricto, `probe` acepta clave RSA utilizable y rechaza EC-only con el mensaje genérico (ADR-0015) |
| `JwksUriPolicyTest` | Unitario | Política de `jwks_uri` (ADR-0015): acepta https público; rechaza `http`, esquemas no http(s), loopback/site-local/link-local/CGNAT/`198.18`/`0.0.0.0`/`224+`, IPv6 `::1`/ULA/link-local/IPv4-mapped, sufijos `.localhost`/`.local`/`.internal`, credenciales embebidas, fragmento, puerto fuera de allowlist, URI relativa, host vacío, longitud > 500, host no resoluble; valves `allow-http`/`allow-private-hosts` y allowlist `allowed-hosts` |
| `JwksUriProvisioningTest` | Integración | Onboarding con política estricta (`@TestPropertySource`): `http://169.254.169.254/latest/meta-data/...` y otros destinos internos → `400` **sin** crear tenant, schema ni fila de IdP; `http` a host público, puerto no permitido → `400`; mapeo HTTP a `ErrorResponse` (`status`/`mensaje`); `JwksProvider.fetch` en runtime rechaza URLs internas de filas de legado |
| `JwksReachabilityProbeTest` | Integración | Probe de alcanzabilidad (`verify-reachability=true`): JWKS alcanzable con clave RSA → tenant creado con schema; inalcanzable, HTTP 500, body no-JSON o JWKS solo-EC → `400` con el mensaje genérico y sin escrituras; `PUT /identity-provider` con URL inválida → `400` conservando la configuración anterior |
| `MultiIssuerJwtDecoderTest` | Unitario | Decoder: issuer no configurado, JWKS vacío, kty no RSA, kid desconocido → evict + reintento → 401 (ADR-0014), kid desconocido resuelto tras refetch con clave rotada, sin `kid` + 1 clave RSA → válido, sin `kid` + varias claves RSA → ambiguo, firma errónea → evict + reintento |
| `RestAuthenticationEntryPointTest` | Unitario | 401 como `ErrorResponse` JSON (`status`/`mensaje`) |
| `JwtFilterIntegrationTest` | Integración | Autenticación JWT multi-issuer contra JWKS local (HttpServer del JDK): JWT válido, issuer desconocido, firma inválida, expirado, audience errónea, algoritmo no permitido, malformado, sin claim `iss`, combinación STANDARD+JWT (mismo y distinto tenant), rotación de claves JWKS (evict+refetch), upsert de miembro en segundo login, cuerpo 401 como `ErrorResponse` |
| `audit/*` | Unitario | Eventos, publisher, listener, modelo de auditoría |

## Infraestructura de test

### Dependencias (pom.xml)

| Dependencia | Propósito |
|---|---|
| `spring-boot-testcontainers` | `@ServiceConnection` para conectar la app al contenedor |
| `org.testcontainers:postgresql` | Contenedor PostgreSQL |
| `org.testcontainers:junit-jupiter` | Integración con JUnit 5 |
| `spring-boot-starter-data-jpa-test`, `-security-test`, `-validation-test`, `-webmvc-test` | Test slices |

Versiones de Testcontainers vía `testcontainers-bom` (`testcontainers.version` en pom.xml).

### Clase base: `BaseIntegrationTest`

- `src/test/java/dev/achiri/multivault/support/BaseIntegrationTest.java`
- `@SpringBootTest` + `@ActiveProfiles("test")` + `@ServiceConnection` sobre un `PostgreSQLContainer` **estático**.
- El contenedor se inicia manualmente en un bloque `static` (no con la extensión `@Testcontainers`): así **un solo contenedor** es compartido por toda la suite y no se detiene entre clases (la extensión lo paraba tras la primera clase y rompía las siguientes).
- `@ServiceConnection` hace que la autoconfiguración del DataSource use el contenedor **ignorando** `spring.datasource.url` y las env vars (`DB_URL`, etc.). El Flyway de `public` y el provisioner de schema por tenant operan contra el contenedor.

### Configuración

- `src/test/resources/application-test.yaml` — perfil `test` (datasource por defecto para resolver placeholders; el valor real lo aporta `@ServiceConnection`). También relaja `multivault.jwks` (`allow-http`, `allow-private-hosts`, `allowed-ports: "*"`, `verify-reachability: false`) porque los tests sirven el JWKS con el `HttpServer` del JDK sobre `http://localhost:<puerto efímero>` y varios usan `https://idp.acme.com/...` sin red. `JwksUriProvisioningTest` y `JwksReachabilityProbeTest` revierten esos defaults con `@TestPropertySource` para ejercitar la política en modo estricto (ADR-0015).
- `src/test/resources/docker-java.properties` — `api.version=1.44`: requerido porque docker-java de Testcontainers 1.21.x negocia API 1.32 y **Docker Engine ≥ 25 exige API ≥ 1.44** (ver ADR-0005).

## Correr tests

```sh
mvn test
```

Docker debe estar corriendo. Los tests crean y destruyen sus datos (schemas de tenant con `DROP SCHEMA ... CASCADE` en `@AfterEach`).

## Pendientes

- [ ] Configurar cobertura con JaCoCo
- [x] Tests de controladores con MockMvc — `TenantProvisioningTest` (validación y PUT IdP) y `DocumentFlowIntegrationTest` (POST/GET documentos con key SERVICE real)
- [x] Implementar tests de seguridad (autenticación por API key y JWT en `ApiKeyFilterIntegrationTest` / `JwtFilterIntegrationTest`; autorización pendiente)

## Preguntas abiertas

- ¿Límite de cobertura deseado?
- ¿Se requiere CI con Docker + Testcontainers?
