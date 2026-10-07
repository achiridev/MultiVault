package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.handler.JsonErrorWriter;
import dev.achiri.multivault.infrastructure.ratelimit.listener.RateLimitMetricsListener;
import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitRule;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimitKeyResolver;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final BucketSpec TEN_PER_MINUTE = new BucketSpec(10, 10, Duration.ofMinutes(1));

    @Test
    void letsRequestThroughWhenNoRuleMatches() throws Exception {
        RateLimitFilter filter = filterFor(rule("other", "/other/**", RateLimitScope.IP), RateLimitScope.IP);
        MockHttpServletRequest request = request("/api/v1/documents", "203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void letsRequestThroughWhenTheRuleHasNoScopeThisFilterHandles() throws Exception {
        RateLimitFilter filter = filterFor(rule("tenant-scoped", "/api/**", RateLimitScope.TENANT), RateLimitScope.IP);
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request("/api/v1/documents", "203.0.113.5"), new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void rejectsRequestWhenTheLimiterRejects() throws Exception {
        StubLimiter limiter = new StubLimiter(RateLimitDecision.rejected(
                "onboarding", RateLimitScope.IP, 3, Duration.ofSeconds(42)));
        RateLimitFilter filter = new RateLimitFilter(matcherFor(rule("onboarding", "/api/**", RateLimitScope.IP)),
                limiter, new StubKeyResolver(), metrics(), new JsonErrorWriter(new tools.jackson.databind.json.JsonMapper()),
                Set.of(RateLimitScope.IP), properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request("/api/v1/documents", "203.0.113.5"), response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("3");
        assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("RateLimit-Reset")).isEqualTo("42");
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("\"mensaje\":\"Demasiadas solicitudes. Reintenta más tarde.\"");
    }

    @Test
    void rejectsOnTheFirstScopeThatExhaustsTheBucket() throws Exception {
        StubKeyResolver keyResolver = new StubKeyResolver();
        StubLimiter limiter = new StubLimiter(
                RateLimitDecision.allowed("onboarding", RateLimitScope.IP, 3, 2),
                RateLimitDecision.rejected("onboarding", RateLimitScope.GLOBAL, 200, Duration.ofSeconds(60)));
        RateLimitFilter filter = new RateLimitFilter(
                matcherFor(rule("onboarding", "/api/**", RateLimitScope.IP, RateLimitScope.GLOBAL)),
                limiter, keyResolver, metrics(), new JsonErrorWriter(new tools.jackson.databind.json.JsonMapper()),
                Set.of(RateLimitScope.IP, RateLimitScope.GLOBAL), properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request("/api/v1/tenants", "203.0.113.5"), response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("200");
        assertThat(limiter.consumedScopes).containsExactly(RateLimitScope.IP, RateLimitScope.GLOBAL);
    }

    @Test
    void skipsScopesWithoutAResolvableKey() throws Exception {
        StubLimiter limiter = new StubLimiter(RateLimitDecision.allowed("onboarding", RateLimitScope.IP, 3, 2));
        RateLimitFilter filter = new RateLimitFilter(
                matcherFor(rule("onboarding", "/api/**", RateLimitScope.IP, RateLimitScope.TENANT)),
                limiter, (req, scope) -> Optional.empty(), metrics(),
                new JsonErrorWriter(new tools.jackson.databind.json.JsonMapper()), Set.of(RateLimitScope.IP),
                properties(true));
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request("/api/v1/documents", "203.0.113.5"), new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
        assertThat(limiter.consumedScopes).isEmpty();
    }

    @Test
    void consumesOneTokenPerMatchingScope() throws Exception {
        StubLimiter limiter = new StubLimiter(RateLimitDecision.allowed("onboarding", RateLimitScope.IP, 3, 2));
        RateLimitFilter filter = new RateLimitFilter(matcherFor(rule("onboarding", "/api/**", RateLimitScope.IP)),
                limiter, new StubKeyResolver(), metrics(),
                new JsonErrorWriter(new tools.jackson.databind.json.JsonMapper()), Set.of(RateLimitScope.IP),
                properties(true));
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request("/api/v1/documents", "203.0.113.5"), new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
        assertThat(limiter.consumedScopes).containsExactly(RateLimitScope.IP);
    }

    @Test
    void skipsEnforcementWhenRateLimitingIsDisabled() throws Exception {
        StubLimiter limiter = new StubLimiter(RateLimitDecision.rejected(
                "onboarding", RateLimitScope.IP, 3, Duration.ofSeconds(42)));
        RateLimitFilter filter = new RateLimitFilter(
                matcherFor(rule("onboarding", "/api/**", RateLimitScope.IP)),
                limiter, new StubKeyResolver(), metrics(),
                new JsonErrorWriter(new tools.jackson.databind.json.JsonMapper()),
                Set.of(RateLimitScope.IP), properties(false));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request("/api/v1/tenants", "203.0.113.5"), response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(limiter.consumedScopes).isEmpty();
    }

    @Test
    void enforcesRulesWhenRateLimitingIsEnabled() throws Exception {
        StubLimiter limiter = new StubLimiter(RateLimitDecision.rejected(
                "onboarding", RateLimitScope.IP, 3, Duration.ofSeconds(42)));
        RateLimitFilter filter = new RateLimitFilter(
                matcherFor(rule("onboarding", "/api/**", RateLimitScope.IP)),
                limiter, new StubKeyResolver(), metrics(),
                new JsonErrorWriter(new tools.jackson.databind.json.JsonMapper()),
                Set.of(RateLimitScope.IP), properties(true));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filter.doFilter(request("/api/v1/tenants", "203.0.113.5"), response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(limiter.consumedScopes).containsExactly(RateLimitScope.IP);
    }

    private static RateLimitFilter filterFor(RateLimitProperties.Rule rule, RateLimitScope... scopes) {
        return new RateLimitFilter(matcherFor(rule), new StubLimiter(RateLimitDecision.allowed("r", RateLimitScope.IP, 3, 2)),
                new StubKeyResolver(), metrics(),
                new JsonErrorWriter(new tools.jackson.databind.json.JsonMapper()), Set.of(scopes), properties(true));
    }

    private static RateLimitProperties properties(boolean enabled) {
        return new RateLimitProperties(enabled, "mv:rl:", "pepper",
                RateLimitProperties.FailMode.CLOSED, List.of());
    }

    private static RateLimitRuleMatcher matcherFor(RateLimitProperties.Rule rule) {
        return new RateLimitRuleMatcher(new RateLimitProperties(true, "mv:rl:", "pepper",
                RateLimitProperties.FailMode.CLOSED, List.of(rule)));
    }

    private static RateLimitProperties.Rule rule(String id, String path, RateLimitScope... scopes) {
        List<String> scopeNames = java.util.Arrays.stream(scopes).map(Enum::name).toList();
        return new RateLimitProperties.Rule(id, List.of(path), scopeNames,
                new RateLimitProperties.Spec(TEN_PER_MINUTE.capacity(),
                        TEN_PER_MINUTE.refillTokens(), TEN_PER_MINUTE.refillPeriod()));
    }

    private static RateLimitMetricsListener metrics() {
        return new RateLimitMetricsListener(new StubMeterRegistryProvider());
    }

    private static final class StubMeterRegistryProvider
            implements org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> {

        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        @Override
        public io.micrometer.core.instrument.MeterRegistry getObject(Object... args) {
            return registry;
        }

        @Override
        public io.micrometer.core.instrument.MeterRegistry getIfAvailable() {
            return registry;
        }

        @Override
        public io.micrometer.core.instrument.MeterRegistry getIfUnique() {
            return registry;
        }
    }

    private static MockHttpServletRequest request(String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(HttpMethod.GET.name());
        request.setRequestURI(path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static final class StubKeyResolver implements RateLimitKeyResolver {

        @Override
        public Optional<String> resolve(jakarta.servlet.http.HttpServletRequest request, RateLimitScope scope) {
            return Optional.of("client");
        }
    }

    private static final class StubLimiter implements RateLimiter {

        private final List<RateLimitDecision> decisions;
        private final List<RateLimitScope> consumedScopes = new java.util.ArrayList<>();
        private int index;

        private StubLimiter(RateLimitDecision... decisions) {
            this.decisions = List.of(decisions);
        }

        @Override
        public RateLimitDecision consume(String key, RateLimitScope scope, String ruleId, BucketSpec spec) {
            consumedScopes.add(scope);
            RateLimitDecision decision = decisions.get(Math.min(index, decisions.size() - 1));
            index++;
            return decision;
        }
    }

    private static final class RecordingChain implements FilterChain {

        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }
}