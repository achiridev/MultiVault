# Docker

## Propósito

Documentar la configuración de contenedores Docker para el proyecto.

## Estado actual

No existe Dockerfile ni docker-compose.yaml en el repositorio. No hay referencias a contenedores.

## Información encontrada

El proyecto tiene Maven Wrapper (`mvnw`), lo que permite construir sin tener Maven instalado.

## Pendientes

- [ ] Crear `Dockerfile` para la aplicación Spring Boot
- [ ] Crear `docker-compose.yaml` con PostgreSQL + app
- [ ] Configurar multi-stage build para optimizar tamaño de imagen

## Preguntas abiertas

- ¿Se usará JRE base distroless o alpine?
- ¿Se requiere dockerizar también MinIO para desarrollo local?
