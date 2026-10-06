package dev.achiri.multivault.infrastructure.ratelimit.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionRateLimitRulesTest {

    private static final String PREFIX = "multivault.ratelimit";

    @Test
    void thePerIpOnboardingBucketMustNotAlsoCarryTheGlobalScope() {
        RateLimitProperties.Rule onboarding = rule("onboarding");

        assertThat(onboarding.scopes()).containsExactly("IP");
    }

    @Test
    void theGlobalOnboardingCircuitBreakerIsDeclaredSeparately() {
        RateLimitProperties.Rule onboardingGlobal = rule("onboarding-global");

        assertThat(onboardingGlobal.scopes()).containsExactly("GLOBAL");
        assertThat(onboardingGlobal.spec().capacity()).isEqualTo(200);
    }

    @Test
    void theDefaultApiRuleOnlyCarriesTheIpScope() {
        RateLimitProperties.Rule apiDefault = rule("api-default");

        assertThat(apiDefault.scopes()).containsExactly("IP");
        assertThat(apiDefault.spec().capacity()).isEqualTo(300);
    }

    @Test
    void noRuleDeclaresTheTenantOrApiKeyScopes() {
        assertThat(properties().rules())
                .flatMap(RateLimitProperties.Rule::scopes)
                .doesNotContain("TENANT", "API_KEY");
    }

    @Test
    void theGlobalScopeIsOnlyUsedByRulesThatAlreadyHaveAHighEnoughCapacity() {
        properties().rules().stream()
                .filter(rule -> rule.scopes().contains("GLOBAL"))
                .forEach(rule -> assertThat(rule.spec().capacity())
                        .as("regla %s con scope GLOBAL", rule.id())
                        .isGreaterThanOrEqualTo(200));
    }

    private static RateLimitProperties.Rule rule(String id) {
        return properties().rules().stream()
                .filter(rule -> rule.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la regla " + id + " no esta en application.yaml"));
    }

    private static RateLimitProperties properties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(propertySource());
        return Binder.get(environment)
                .bind(PREFIX, RateLimitProperties.class)
                .orElseThrow(() -> new AssertionError("no se pudo leer " + PREFIX + " de application.yaml"));
    }

    private static PropertySource<?> propertySource() {
        try {
            return new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                    .getFirst();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}