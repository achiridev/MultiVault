# Contexto

## Propósito

Documentar el contexto general del proyecto MultiVault, el problema que resuelve y el dominio en el que opera.

## Estado actual

El proyecto se encuentra en una fase temprana de desarrollo. Existen definiciones de base de datos (schemas SQL) y la configuración mínima de un proyecto Spring Boot 4.1.0. No hay lógica de negocio implementada.

## Información encontrada

- **Nombre del proyecto:** MultiVault (`dev.achiri.multivault`)
- **Descripción del dominio:** Sistema de gestión documental con aislamiento multi-tenant mediante esquemas físicos en PostgreSQL
- **Licencia:** Apache License 2.0
- **Origen:** Proyecto generado desde Spring Initializr con los starters: Data JPA, Security, Validation, Web MVC
- **Repositorio:** Git local, 2 commits hasta la fecha:
  - `add database models (schemas)` — creación de esquemas de base de datos
  - `docs: add license` — incorporación de licencia Apache 2.0

### Estado del proyecto: scaffolding inicial

| Existe | No existe |
|---|---|
| `MultivaultApplication.java` (entry point) | Entidades JPA |
| `V1__public_schema.sql` (9 tablas públicas) | Repositorios Spring Data |
| `tenant_schema.sql` (4 tablas + trigger por tenant) | Servicios (@Service) |
| `pom.xml` con dependencias del stack | Controladores REST |
| `application.yaml` (skeleton, 3 líneas) | Configuración Spring Security |
| Test de contexto vacío (`@SpringBootTest`) | Configuración DataSource/Flyway |
| `.gitignore`, `.gitattributes`, `LICENSE` | Dockerfile, CI/CD |
| `docs/` (vault Obsidian completo) | Frontend |

## Pendientes

- [ ] Definir la visión y el problema de negocio concreto que resuelve MultiVault
- [ ] Documentar el público objetivo (tipos de tenant, casos de uso)
- [ ] Establecer el alcance del MVP

## Preguntas abiertas

- ¿Cuál es el problema de negocio específico que resuelve MultiVault?
- ¿Quiénes son los usuarios target?
- ¿Qué diferenciación tiene frente a otros gestores documentales?
