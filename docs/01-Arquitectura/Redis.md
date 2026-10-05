# Redis

## Propósito

Documentar el uso de Redis en el sistema: caché, rate limiting, idempotencia y cola de
aprovisionamiento. En el futuro, sesiones y colas de procesamiento asíncrono.

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

## Usos adicionales a caché

Desde [ADR-0016](../06-Decisiones/ADR-0016.md), Redis sostiene tres usos más. Todos acceden
por `StringRedisTemplate` o por un `ProxyManager` dedicado; ninguno usa Spring Cache.

### Rate limiting (Bucket4j)

- **Dependencias:** `bucket4j_jdk17-core`, `bucket4j_jdk17-redis-common`, `bucket4j_jdk17-lettuce`
- **Conexión:** `RedisRateLimitConfig` construye un `RedisClient` propio desde
  `RedisStandaloneConfiguration`, con codec `String` para claves y `byte[]` para valores.
  No reutiliza la conexión nativa de `LettuceConnectionFactory` porque
  `share-native-connection=false` abriría una conexión nueva por llamada.
- **Anatomía de clave:** `{key-prefix}{ruleId}:{scope}:{hmac-sha256-32-hex}`
  (por ejemplo `mv:rl:onboarding:ip:9f2c...`). La IP nunca se almacena en claro.
- **Expiración:** `fixedTimeToLive(longestRefillPeriod + 2min)`, calculado al arrancar
  desde la regla con el refill más largo. Sin esto, rotar direcciones escribiría una
  clave por IP indefinidamente.
- **Compatibilidad:** `bucket4j_jdk17-lettuce:8.21.0` declara `lettuce-core:6.1.8` en scope
  `provided`; el proyecto usa Lettuce 7.5.2. Funciona porque solo se usan
  `StatefulRedisConnection` y `eval`, API estable entre 6 y 7, verificado con test de
  integración real contra el contenedor. Solo soporta configuración **standalone**.
- **Degradación:** `fail-mode: CLOSED` ante un `DataAccessException` delega en
  `LocalRateLimiter`, un bucket en memoria por instancia.

### Idempotencia

- **Claves:** `{key-prefix}{uuid}`, valor JSON con estado, fingerprint y respuesta.
- **Claim:** `SET NX` con TTL `in-progress-ttl` (10 min).
- **Replay:** al completar, `SET` con TTL `completed-ttl` (24 h) sobre el mismo valor.

### Cola de aprovisionamiento (Redis Streams)

- **Stream:** `mv:provisioning:jobs`, consumer group `mv-provisioners`,
  `MAXLEN ~ {max-stream-length}` (10 000).
- **Campos por mensaje:** `tenantId`, `schemaName`, `planId`, `attempt`, `enqueuedAt`.
- **Dead letter:** `mv:provisioning:jobs:dlq`.
- **Consumo:** `XREADGROUP` para mensajes nuevos, `XPENDING` + `XCLAIM` por
  `visibility-timeout` para recuperar los de un worker caído, `XACK` al terminar.
- **Redis Streams no es una base de datos:** un `FLUSHDB` pierde caché *y* cola. La fuente
  de verdad es `tenant.status = PENDING_PROVISIONING` en Postgres, y
  `ProvisioningReconciler` reencola desde ahí cada `reconcile-interval`.

## Posibles usos futuros

- **Sesiones:** Almacenar sesiones de platform_user
- **Colas de tareas:** Procesamiento asíncrono de subida de documentos, generación de thumbnails, etc.
- **Caché de consultas frecuentes:** Datos de planes, configuración de tenants activos

## Pendientes

- [x] Agregar `spring-boot-starter-data-redis` y `spring-boot-starter-cache` a `pom.xml`
- [x] Configurar conexión Redis en `application.yaml`
- [x] Definir `RedisCacheManager` con serialización Jackson 3 y TTL base
- [x] Aplicar caché a casos reales (JWKS keys, API keys) con `@Cacheable` y caches nombrados
- [x] Definir política de expiración por caché (TTLs específicos por caché)
- [x] Rate limiting distribuido por IP con corte global (ADR-0016)
- [x] Almacén de idempotencia para el onboarding (ADR-0016)
- [x] Cola de aprovisionamiento asíncrono con Redis Streams (ADR-0016)
- [ ] Soportar Redis Cluster en el bucket limitador (hoy solo standalone)

## Preguntas abiertas

- ¿Se usará Redis también como message broker para el procesamiento asíncrono de
  subidas de documentos y generación de thumbnails, reutilizando el patrón de
  `ProvisioningWorker`?
- ¿Se usará un servicio administrado (ElastiCache, Redis Cloud) en producción o la misma
  instancia local desplegada? Al ser ahora Redis dependencia de disponibilidad y no solo
  de rendimiento, la pregunta cambia de prioridad.
- ¿Cómo se va a respaldar la instancia de caché sin perder la cola de aprovisionamiento?
  Hoy el reconciliador lo recupera, pero el rediseño sería mejor.
