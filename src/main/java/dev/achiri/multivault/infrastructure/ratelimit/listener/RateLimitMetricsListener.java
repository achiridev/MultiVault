package dev.achiri.multivault.infrastructure.ratelimit.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class RateLimitMetricsListener {

    private static final String METRIC_PREFIX = "multivault.ratelimit";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> consumed = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> rejected = new ConcurrentHashMap<>();

    public RateLimitMetricsListener(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable(() -> {
            log.debug("Sin MeterRegistry en el contexto; las métricas de rate limit no se exportan");
            return new SimpleMeterRegistry();
        });
    }

    public void recordConsumed(String ruleId) {
        counter(consumed, "consumed", ruleId).increment();
    }

    public void recordRejected(String ruleId) {
        counter(rejected, "rejected", ruleId).increment();
    }

    private Counter counter(ConcurrentMap<String, Counter> registry, String outcome, String ruleId) {
        return registry.computeIfAbsent(outcome + ':' + ruleId,
                key -> Counter.builder(METRIC_PREFIX + '.' + outcome)
                        .description("Tokens consumidos y rechazados por el rate limiting")
                        .tag("rule", ruleId)
                        .register(meterRegistry));
    }
}