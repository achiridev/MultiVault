# Arquitectura

## Propósito

Describir la arquitectura general del sistema MultiVault: componentes, capas, flujos y tecnologías.

## Estado actual

La arquitectura está definida a nivel de esquema de base de datos y stack tecnológico, pero no existe implementación de las capas de aplicación.

## Información encontrada

### Stack tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1.0 |
| ORM | Spring Data JPA / Hibernate |
| Seguridad | Spring Security |
| Validación | Spring Validation (Jakarta Validation) |
| API | Spring Web MVC (Servlet, no WebFlux) |
| Base de datos | PostgreSQL con extensión `pgcrypto` |
| Caché | Redis (valkey 9.1.1 local) + Spring Cache (`@EnableCaching`, `RedisCacheManager` — ADR-0008) |
| Migraciones | Flyway (schema público `db/migration`; por tenant vía instancia programática `db/tenant` — ADR-0004) |
| Build | Maven 3.9.16 + Maven Wrapper |
| Procesamiento | Lombok (annotation processor) |
| Testing | JUnit 5 + Spring Boot Test slices |

### Capas previstas (por implementar)

```
Cliente HTTP
    ↓
Controladores REST (Spring MVC) — PARCIAL: `TenantController` (POST /api/v1/tenants)
    ↓
Servicios (Spring @Service) — PARCIAL: `TenantService` (orquesta aprovisionamiento), `TenantProvisioningService`, `ApiKeyService`
    ↓
Mappers (MapStruct, generados en compile) — `tenant/mapper/`, `subscription/mapper/`
    ↓
Repositorios (Spring Data JPA) — PARCIAL: `TenantRepository`, `TenantIdentityProviderRepository`, `TenantMemberRepository`, `TenantUsageRepository`, `PlanRepository`, `SubscriptionRepository`, `ApiKeyRepository`
    ↓
Entidades JPA — PARCIAL: `Tenant`, `TenantIdentityProvider`, `TenantMember`, `TenantUsage`, `Plan`, `Subscription`, `ApiKey`
    ↓
PostgreSQL (público + esquemas por tenant)
```

### Estructura de paquetes actual

```
dev.achiri.multivault
    └── MultivaultApplication.java   (única clase)
```

### Decisiones arquitectónicas identificadas

- **Multi-tenancy:** Schema-per-tenant (aislamiento físico)
- **Versionado de documentos:** Inmutable, cada versión es un INSERT nuevo; `document.current_version_id` se repunta
- **ACL por recurso:** `document_permission` con niveles OWNER/EDITOR/VIEWER; un solo OWNER por documento
- **Auditoría:** WORM (write-once-read-many); REVOKE de UPDATE/DELETE a nivel DB
- **Soft deletes:** `folder.deleted_at`, `document.deleted_at` + queries con filtro `WHERE deleted_at IS NULL`
- **Trigger DB:** auto-inserción de permiso OWNER al insertar documento (no duplicar en app)
- **Partial unique indexes:** para reglas de negocio (single active subscription, single owner, non-revoked key hash, unique folder names)

## Pendientes

- [ ] Crear estructura de paquetes (`entity`, `repository`, `service`, `controller`, `config`, `security`, `dto`, `exception`)
- [x] Implementar capa de entidades JPA del schema público (resto: `platform_user`)
- [ ] Implementar capa de entidades JPA del schema por tenant
- [x] Implementar repositorios de los dominios tenant/plan/subscription/apikey
- [ ] Implementar repositorios del resto de dominios
- [x] Implementar `TenantService` (creación de organización + aprovisionamiento de schema + API key)
- [ ] Implementar el resto de servicios
- [x] Implementar `TenantController` (POST /api/v1/tenants)
- [ ] Implementar el resto de controladores REST
- [x] Configurar MapStruct (ADR-0002) — mappers de `tenant` y `subscription`
- [ ] Configurar Spring Security
- [x] Configurar Flyway (schema público + migraciones por tenant — ADR-0004)
- [ ] Configurar DataSource y JPA

## Preguntas abiertas

- ¿Se usará una arquitectura hexagonal/clean architecture o una estructura por capas tradicional?
- ¿Se expondrá una API REST pura o se considerará GraphQL?
- ¿Habrá un API Gateway o los clientes consumirán directamente?
