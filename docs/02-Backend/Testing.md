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
| `AuditEventPublishingTest` | Integración | Auditoría AFTER_COMMIT (persiste en commit, no en rollback) |
| `ApiKeyServiceTest` | Unitario | Generación de api key (raw mostrada una vez, solo hash almacenado) |
| `ApiKeyFilterIntegrationTest` | Integración | Autenticación por API key: `SERVICE` autentica, `STANDARD` sin JWT/revocada/expirada/desconocida/JWT-like → 401 |
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

- `src/test/resources/application-test.yaml` — perfil `test` (datasource por defecto para resolver placeholders; el valor real lo aporta `@ServiceConnection`).
- `src/test/resources/docker-java.properties` — `api.version=1.44`: requerido porque docker-java de Testcontainers 1.21.x negocia API 1.32 y **Docker Engine ≥ 25 exige API ≥ 1.44** (ver ADR-0005).

## Correr tests

```sh
mvn test
```

Docker debe estar corriendo. Los tests crean y destruyen sus datos (schemas de tenant con `DROP SCHEMA ... CASCADE` en `@AfterEach`).

## Pendientes

- [ ] Configurar cobertura con JaCoCo
- [ ] Tests de controladores con MockMvc (por ahora validación de body y `PUT /tenants/{id}/identity-provider` en `TenantProvisioningTest`)
- [x] Implementar tests de seguridad (autenticación por API key en `ApiKeyFilterIntegrationTest`; autorización pendiente)

## Preguntas abiertas

- ¿Límite de cobertura deseado?
- ¿Se requiere CI con Docker + Testcontainers?
