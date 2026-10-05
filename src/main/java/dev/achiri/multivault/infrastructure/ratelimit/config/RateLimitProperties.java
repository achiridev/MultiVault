package dev.achiri.multivault.infrastructure.ratelimit.config;

import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "multivault.ratelimit")
public record RateLimitProperties(
        boolean enabled,
        String keyPrefix,
        String keySalt,
        FailMode failMode,
        List<Rule> rules
) {

    public enum FailMode {
        CLOSED,
        OPEN
    }

    public record Rule(
            String id,
            List<String> match,
            List<String> scopes,
            Spec spec
    ) {
    }

    public record Spec(
            long capacity,
            long refillTokens,
            Duration refillPeriod
    ) {
        public BucketSpec toBucketSpec() {
            return new BucketSpec(capacity, refillTokens, refillPeriod);
        }
    }

    private static final String DEFAULT_KEY_PREFIX = "mv:rl:";
    private static final FailMode DEFAULT_FAIL_MODE = FailMode.CLOSED;
    private static final List<Rule> NO_RULES = List.of();

    public RateLimitProperties {
        keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_KEY_PREFIX : keyPrefix;
        keySalt = keySalt == null ? "" : keySalt;
        failMode = failMode == null ? DEFAULT_FAIL_MODE : failMode;
        rules = rules == null ? NO_RULES : List.copyOf(rules);
    }

    public Duration longestRefillPeriod() {
        return rules.stream()
                .map(Rule::spec)
                .filter(Objects::nonNull)
                .map(Spec::refillPeriod)
                .filter(Objects::nonNull)
                .max(Duration::compareTo)
                .orElse(Duration.ofMinutes(1));
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (enabled && keySalt.isBlank()) {
            errors.add("multivault.ratelimit.key-salt es obligatorio cuando el rate limiting está habilitado");
        }
        rules.stream()
                .filter(rule -> rule.spec() == null)
                .forEach(rule -> errors.add("la regla de rate limit " + rule.id() + " necesita un spec"));
        return errors;
    }
}