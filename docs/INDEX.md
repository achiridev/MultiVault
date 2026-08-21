# docs/ — MultiVault Documentation Index

Punto de entrada único. Lee este archivo primero, luego los documentos relevantes a tu tarea.

---

## 00-Overview

| Documento | Cuándo leerlo |
|---|---|
| [Contexto](00-Overview/Contexto.md) | Primera sesión, o cuando necesites entender el estado del proyecto |
| [Roadmap](00-Overview/Roadmap.md) | Cuando planifiques nueva funcionalidad |
| [Objetivos](00-Overview/Objetivos.md) | Cuando necesites alinear una decisión con los objetivos del proyecto |

## 01-Arquitectura

| Documento | Cuándo leerlo |
|---|---|
| [Arquitectura](01-Arquitectura/Arquitectura.md) | Stack, capas, patrones clave |
| [MultiTenant](01-Arquitectura/MultiTenant.md) | Cuando trabajes con aislamiento de datos, aprovisionamiento de tenants |
| [Autenticacion](01-Arquitectura/Autenticacion.md) | Cuando implementes auth: OIDC, API keys, platform users |
| [Storage](01-Arquitectura/Storage.md) | Cuando trabajes con subida/descarga de documentos, S3/MinIO |
| [Redis](01-Arquitectura/Redis.md) | Cuando trabajes con caching (JWKS, API keys), sesiones o rate limiting |
| [Seguridad](01-Arquitectura/Seguridad.md) | Cuando implementes controles de seguridad, auditoría |

## 02-Backend

| Documento | Cuándo leerlo |
|---|---|
| [API](02-Backend/API.md) | Cuando implementes o modifiques endpoints REST |
| [BaseDatos](02-Backend/BaseDatos.md) | Cuando crees migraciones, entidades, o consultes el schema |
| [Entidades](02-Backend/Entidades.md) | Cuando crees o modifiques entidades JPA |
| [Servicios](02-Backend/Servicios.md) | Cuando implementes lógica de negocio |
| [Testing](02-Backend/Testing.md) | Cuando escribas tests |

## 03-Frontend (placeholder)

| [Frontend](03-Frontend/Frontend.md) | — |
| [Componentes](03-Frontend/Componentes.md) | — |

## 04-DevOps (placeholder)

| [Docker](04-DevOps/Docker.md) | — |
| [Deploy](04-DevOps/Deploy.md) | — |
| [CI-CD](04-DevOps/CI-CD.md) | — |

## 05-Bugs

| [Bugs](05-Bugs/Bugs.md) | Cuando reportes o investigues un bug |

## 06-Decisiones

| [ADR-0001](06-Decisiones/ADR-0001.md) | Schema-per-tenant |
| [ADR-0002](06-Decisiones/ADR-0002.md) | MapStruct para mapeo DTO ↔ Entidad |
| [ADR-0003](06-Decisiones/ADR-0003.md) | Auditoría vía eventos de aplicación (AFTER_COMMIT) |
| [ADR-0004](06-Decisiones/ADR-0004.md) | Aprovisionamiento de schema por tenant con Flyway programático |
| [ADR-0005](06-Decisiones/ADR-0005.md) | Testcontainers para tests de integración |
| [ADR-0006](06-Decisiones/ADR-0006.md) | `tenant_identity_provider` obligatorio en la creación de tenant |
| [ADR-0007](06-Decisiones/ADR-0007.md) | JJWT con codec JSON Jackson 3 propio (sin `jjwt-jackson`) |
| [ADR-0008](06-Decisiones/ADR-0008.md) | Conexión Redis local y Spring Cache con serialización Jackson 3 |
| [ADR-0009](06-Decisiones/ADR-0009.md) | Ciclo de vida de tenants (cancelación, suspensión, reactivación) |
| [ADR-0010](06-Decisiones/ADR-0010.md) | Streaming de uploads y transacción corta en documentos |

## 99-Templates

| [ADR](99-Templates/ADR-Template.md) | [Bug](99-Templates/Bug-Template.md) | [Endpoint](99-Templates/Endpoint-Template.md) |
|---|---|---|
