# Redis

## Propósito

Documentar el uso de Redis en el sistema para caching, y en el futuro sesiones, rate limiting y colas.

## Estado actual

Redis está integrado como infraestructura de caché:

- **Dependencias:** `spring-boot-starter-data-redis` (cliente Lettuce) y `spring-boot-starter-cache` en `pom.xml`
- **Configuración:** `application.yaml` → `spring.data.redis.host=${REDIS_HOST:localhost}`, `spring.data.redis.port=${REDIS_PORT:6379}`, repositorios Redis desactivados
- **Local:** valkey 9.1.1 corriendo en `localhost:6379` sin contraseña (defaults de la app)
- **Caché:** `infrastructure/cache/RedisCacheConfig` con `@EnableCaching` y `RedisCacheManager` propio (ver ADR-0008)

## Información encontrada

### Configuración de la conexión

| Propiedad | Default | Descripción |
|---|---|---|
| `REDIS_HOST` / `spring.data.redis.host` | `localhost` | Host de Redis |
| `REDIS_PORT` / `spring.data.redis.port` | `6379` | Puerto de Redis |
| `cache.redis.time-to-live` | `10m` | TTL base de los caches |

Sin contraseña en local; si se agrega auth, usar `spring.data.redis.password` (vía variable de entorno, nunca hardcodeada).

### Serialización de caché

- Claves: `RedisSerializer.string()`
- Valores: `GenericJacksonJsonRedisSerializer` (Jackson 3, ver ADR-0007) con default typing restringido por `BasicPolymorphicTypeValidator` a `dev.achiri.multivault.*` y `java.util.*`
- `enableSpringCacheNullValueSupport()` para cachear `null`

Los `spring.cache.redis.*` de Boot no aplican: al definir un `RedisCacheManager` propio, la configuración (incluido el TTL) vive en `RedisCacheConfig`.

### Caches activos

| Cache | Clave | Valor | TTL | Usado por |
|---|---|---|---|---|
| `apiKeys` | hash SHA-256 de la raw key | `ApiKeyIdentity` | 5 min | `ApiKeyAuthenticator.findValidByHash` |
| `jwks` | `jwks_uri` del provider | `List<JwkEntry>` | 10 min | `JwksProvider.fetch` (con `evict` manual al fallar la firma) |

Los valores cacheables son records serializables con Jackson 3; se evita `Instant`/`Optional` en favor de tipos planos (ej. `long expiresAtEpochSecond` en `ApiKeyIdentity`).

### Tests

`BaseIntegrationTest` levanta Redis con Testcontainers (`GenericContainer` imagen `redis:7-alpine` + `@ServiceConnection`), igual que PostgreSQL. `RedisConnectionTest` verifica set/get reales. Requiere Docker.

## Posibles usos futuros

- **Rate limiting:** Almacenar contadores de requests por tenant/API key
- **Sesiones:** Almacenar sesiones de platform_user
- **Colas de tareas:** Procesamiento asíncrono de subida de documentos, generación de thumbnails, etc.
- **Caché de consultas frecuentes:** Datos de planes, configuración de tenants activos

## Pendientes

- [x] Agregar `spring-boot-starter-data-redis` y `spring-boot-starter-cache` a `pom.xml`
- [x] Configurar conexión Redis en `application.yaml`
- [x] Definir `RedisCacheManager` con serialización Jackson 3 y TTL base
- [x] Aplicar caché a casos reales (JWKS keys, API keys) con `@Cacheable` y caches nombrados
- [x] Definir política de expiración por caché (TTLs específicos por caché)

## Preguntas abiertas

- ¿Se usará Redis también como message broker (Redis Streams) para colas?
- ¿Se usará un servicio administrado (ElastiCache, Redis Cloud) en producción o la misma instancia local desplegada?
