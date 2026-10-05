package dev.achiri.multivault.infrastructure.ratelimit.config;

import dev.achiri.multivault.infrastructure.ratelimit.redis.RateLimitKeyHasher;
import dev.achiri.multivault.infrastructure.ratelimit.redis.RedisRateLimiter;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
@Slf4j
public class RedisRateLimitConfig {

    private static final Duration EXPIRATION_MARGIN = Duration.ofMinutes(2);
    private static final RedisCodec<String, byte[]> KEY_AND_VALUE_CODEC =
            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);

    @Bean
    RateLimitKeyHasher rateLimitKeyHasher(RateLimitProperties properties) {
        return new RateLimitKeyHasher(properties.keyPrefix(), properties.keySalt());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisConnectionFactory connectionFactory) {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
            throw new IllegalStateException("el rate limiting distribuido requiere LettuceConnectionFactory");
        }
        RedisClient client = RedisClient.create(redisUri(lettuce));
        client.setOptions(ClientOptions.builder().autoReconnect(true).build());
        return client.connect(KEY_AND_VALUE_CODEC);
    }

    @Bean
    ProxyManager<String> rateLimitProxyManager(StatefulRedisConnection<String, byte[]> rateLimitRedisConnection,
                                               RateLimitProperties properties) {
        Duration expiration = properties.longestRefillPeriod().plus(EXPIRATION_MARGIN);
        log.debug("Rate limiting distribuido activo; expiración de claves en {}", expiration);
        return Bucket4jLettuce.casBasedBuilder(rateLimitRedisConnection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.fixedTimeToLive(expiration))
                .build();
    }

    @Bean
    RedisRateLimiter redisRateLimiter(ProxyManager<String> rateLimitProxyManager,
                                     RateLimitKeyHasher rateLimitKeyHasher) {
        return new RedisRateLimiter(rateLimitProxyManager, rateLimitKeyHasher);
    }

    private static RedisURI redisUri(LettuceConnectionFactory connectionFactory) {
        RedisStandaloneConfiguration standalone = connectionFactory.getStandaloneConfiguration();
        if (standalone == null) {
            throw new IllegalStateException(
                    "el rate limiting distribuido requiere configuración Redis standalone; "
                            + "soportarlo en cluster es trabajo pendiente");
        }
        RedisURI uri = RedisURI.create(standalone.getHostName(), standalone.getPort());
        Duration commandTimeout = connectionFactory.getClientConfiguration().getCommandTimeout();
        if (commandTimeout != null) {
            uri.setTimeout(commandTimeout);
        }
        applyCredentials(uri, standalone.getUsername(), standalone.getPassword());
        return uri;
    }

    private static void applyCredentials(RedisURI uri, String username, RedisPassword password) {
        if (password.isPresent()) {
            uri.setAuthentication(username, password.get());
        } else if (username != null && !username.isBlank()) {
            uri.setAuthentication(username, new char[0]);
        }
    }
}