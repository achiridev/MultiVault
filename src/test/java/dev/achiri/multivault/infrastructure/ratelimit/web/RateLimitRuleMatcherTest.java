package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitRule;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitRuleMatcherTest {

    @Test
    void matchesOnMethodAndPath() {
        RateLimitRuleMatcher matcher = matcherWith(rule("onboarding", List.of("POST:/api/v1/tenants")));

        assertThat(matcher.findAll(post("/api/v1/tenants"))).hasSize(1);
        assertThat(matcher.findAll(get("/api/v1/tenants"))).isEmpty();
    }

    @Test
    void matchesPathOnlyWhenNoMethodIsDeclared() {
        RateLimitRuleMatcher matcher = matcherWith(rule("api-default", List.of("/api/**")));

        assertThat(matcher.findAll(post("/api/v1/documents"))).hasSize(1);
        assertThat(matcher.findAll(get("/api/v1/documents/abc"))).hasSize(1);
        assertThat(matcher.findAll(get("/actuator/health"))).isEmpty();
    }

    @Test
    void matchesAnyOfSeveralMethods() {
        RateLimitRuleMatcher matcher = matcherWith(rule("tenant-write", List.of("PUT,POST:/api/v1/tenants/**")));

        assertThat(matcher.findAll(put("/api/v1/tenants/status"))).hasSize(1);
        assertThat(matcher.findAll(post("/api/v1/tenants/identity-provider"))).hasSize(1);
        assertThat(matcher.findAll(get("/api/v1/tenants/status"))).isEmpty();
    }

    @Test
    void matchesWhenAnyOfTheRulePatternsMatch() {
        RateLimitRuleMatcher matcher = matcherWith(
                rule("mixed", List.of("GET:/api/v1/documents/*", "POST:/api/v1/documents")));

        assertThat(matcher.findAll(get("/api/v1/documents/abc"))).hasSize(1);
        assertThat(matcher.findAll(post("/api/v1/documents"))).hasSize(1);
    }

    @Test
    void returnsEveryMatchingRuleInDeclarationOrder() {
        RateLimitRuleMatcher matcher = matcherWith(
                rule("onboarding", List.of("POST:/api/v1/tenants"), List.of("IP")),
                rule("onboarding-global", List.of("POST:/api/v1/tenants"), List.of("GLOBAL")),
                rule("api-default", List.of("/api/**")));

        assertThat(matcher.findAll(post("/api/v1/tenants"))).map(RateLimitRule::id)
                .containsExactly("onboarding", "onboarding-global", "api-default");
        assertThat(matcher.findAll(get("/api/v1/documents"))).map(RateLimitRule::id)
                .containsExactly("api-default");
    }

    @Test
    void parsesScopesIgnoringCaseAndSpaces() {
        RateLimitRuleMatcher matcher = matcherWith(rule("scoped", List.of("/api/**"), List.of(" ip ", "GLOBAL")));

        assertThat(matcher.findAll(get("/api/x"))).map(RateLimitRule::scopes)
                .contains(List.of(RateLimitScope.IP, RateLimitScope.GLOBAL));
    }

    @Test
    void rejectsUnknownScope() {
        RateLimitProperties properties = properties(rule("bad", List.of("/api/**"), List.of("SOMEONE")));

        assertThatThrownBy(() -> new RateLimitRuleMatcher(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRuleWithoutPatterns() {
        RateLimitProperties properties = properties(rule("empty", List.of()));

        assertThatThrownBy(() -> new RateLimitRuleMatcher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsRuleWithoutSpec() {
        RateLimitProperties.Rule withoutSpec =
                new RateLimitProperties.Rule("nospec", List.of("/api/**"), List.of("IP"), null);

        assertThatThrownBy(() -> new RateLimitRuleMatcher(properties(withoutSpec)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nospec");
    }

    @Test
    void exposesConfiguredRules() {
        RateLimitRuleMatcher matcher = matcherWith(
                rule("a", List.of("/api/**")), rule("b", List.of("/other/**")));

        assertThat(matcher.rules()).extracting(RateLimitRule::id).containsExactly("a", "b");
    }

    private static RateLimitRuleMatcher matcherWith(RateLimitProperties.Rule... rules) {
        return new RateLimitRuleMatcher(properties(rules));
    }

    private static RateLimitProperties properties(RateLimitProperties.Rule... rules) {
        return new RateLimitProperties(true, "mv:rl:", "pepper",
                RateLimitProperties.FailMode.CLOSED, List.of(rules));
    }

    private static RateLimitProperties.Rule rule(String id, List<String> patterns) {
        return rule(id, patterns, List.of("IP"));
    }

    private static RateLimitProperties.Rule rule(String id, List<String> patterns, List<String> scopes) {
        return new RateLimitProperties.Rule(id, patterns, scopes,
                new RateLimitProperties.Spec(10, 10, Duration.ofMinutes(1)));
    }

    private static MockHttpServletRequest post(String path) {
        return request(HttpMethod.POST, path);
    }

    private static MockHttpServletRequest get(String path) {
        return request(HttpMethod.GET, path);
    }

    private static MockHttpServletRequest put(String path) {
        return request(HttpMethod.PUT, path);
    }

    private static MockHttpServletRequest request(HttpMethod method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method.name());
        request.setRequestURI(path);
        return request;
    }
}