# Redis

## Propósito

Documentar el uso de Redis en el sistema para caching, sesiones, rate limiting y colas.

## Estado actual

No existe referencia a Redis en el proyecto:
- No hay dependencia en `pom.xml`
- No hay configuración en `application.yaml`
- No hay schemas ni código que lo referencien

Este archivo es un placeholder para documentar decisiones futuras.

## Información encontrada

Sin información. Redis no está contemplado en el estado actual del proyecto.

## Posibles usos futuros

- **Caché de JWKS keys:** Cachear las claves públicas obtenidas de los JWKS URIs de cada tenant
- **Rate limiting:** Almacenar contadores de requests por tenant/API key
- **Sesiones:** Almacenar sesiones de platform_user
- **Colas de tareas:** Procesamiento asíncrono de subida de documentos, generación de thumbnails, etc.
- **Caché de consultas frecuentes:** Datos de planes, configuración de tenants activos

## Pendientes

- [ ] Evaluar necesidad de Redis para el MVP
- [ ] Si se requiere, agregar `spring-boot-starter-data-redis` a `pom.xml`
- [ ] Configurar conexión Redis en `application.yaml`
- [ ] Definir política de expiración de cachés

## Preguntas abiertas

- ¿Se necesita Redis para el MVP o se puede postergar?
- ¿Redis solo para caching o también como message broker (Redis Streams)?
- ¿Se usará Redis en local o servicio administrado (ElastiCache, Redis Cloud)?
