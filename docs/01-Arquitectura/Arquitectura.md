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
| Migraciones | Flyway (formato, pero sin dependencia en pom.xml) |
| Build | Maven 3.9.16 + Maven Wrapper |
| Procesamiento | Lombok (annotation processor) |
| Testing | JUnit 5 + Spring Boot Test slices |

### Capas previstas (por implementar)

```
Cliente HTTP
    ↓
Controladores REST (Spring MVC) — PARCIAL: `TenantController` (POST /api/v1/tenants)
    ↓
Servicios (Spring @Service) — PARCIAL: `TenantService` (creación transaccional de organización)
    ↓
Repositorios (Spring Data JPA) — PARCIAL: `TenantRepository`, `TenantIdentityProviderRepository`, `TenantMemberRepository`, `PlanRepository`, `SubscriptionRepository`
    ↓
Entidades JPA — PARCIAL: `Tenant`, `TenantIdentityProvider`, `TenantMember`, `Plan`, `Subscription`
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
- [x] Implementar capa de entidades JPA del schema público (resto: `api_key`, `platform_user`, `tenant_usage`, `audit_log`)
- [ ] Implementar capa de entidades JPA del schema por tenant
- [x] Implementar repositorios de los dominios tenant/plan/subscription
- [ ] Implementar repositorios del resto de dominios
- [x] Implementar `TenantService` (creación de organización)
- [ ] Implementar el resto de servicios
- [x] Implementar `TenantController` (POST /api/v1/tenants)
- [ ] Implementar el resto de controladores REST
- [ ] Configurar Spring Security
- [ ] Configurar Flyway
- [ ] Configurar DataSource y JPA

## Preguntas abiertas

- ¿Se usará una arquitectura hexagonal/clean architecture o una estructura por capas tradicional?
- ¿Se expondrá una API REST pura o se considerará GraphQL?
- ¿Habrá un API Gateway o los clientes consumirán directamente?
