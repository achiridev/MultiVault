package dev.achiri.multivault.infrastructure.ratelimit.resilience;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
public class FailModeRateLimiter implements RateLimiter {

    private final RateLimiter distributed;
    private final LocalRateLimiter local;
    private final RateLimitProperties.FailMode failMode;

    public FailModeRateLimiter(RateLimiter distributed, LocalRateLimiter local, RateLimitProperties.FailMode failMode) {
        this.distributed = distributed;
        this.local = local;
        this.failMode = failMode;
    }

    @Override
    public RateLimitDecision consume(String key, RateLimitScope scope, String ruleId, BucketSpec spec) {
        try {
            return distributed.consume(key, scope, ruleId, spec);
        } catch (DataAccessException e) {
            return degrade(key, scope, ruleId, spec, e);
        }
    }

    private RateLimitDecision degrade(String key, RateLimitScope scope, String ruleId, BucketSpec spec,
                                      RuntimeException cause) {
        if (failMode == RateLimitProperties.FailMode.OPEN) {
            log.warn("Rate limiting distribuido no disponible; fail-open aplicado (scope={}, rule={})",
                    scope, ruleId, cause);
            return RateLimitDecision.unlimited();
        }
        log.warn("Rate limiting distribuido no disponible; degradando a límite local por instancia "
                + "(scope={}, rule={})", scope, ruleId, cause);
        return local.consume(key, scope, ruleId, spec);
    }

    boolean usesLocalBackend() {
        return failMode == RateLimitProperties.FailMode.CLOSED;
    }
}