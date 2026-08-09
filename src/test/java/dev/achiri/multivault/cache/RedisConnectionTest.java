package dev.achiri.multivault.cache;

import dev.achiri.multivault.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConnectionTest extends BaseIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void writesAndReadsValue() {
        String key = "connection-test";
        stringRedisTemplate.opsForValue().set(key, "pong");
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("pong");
        stringRedisTemplate.delete(key);
    }
}
