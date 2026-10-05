package dev.achiri.multivault.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "multivault.client-ip")
public record ClientIpProperties(
        List<String> trustedProxies
) {

    private static final List<String> NO_TRUSTED_PROXIES = List.of();

    public ClientIpProperties {
        if (trustedProxies == null) {
            trustedProxies = NO_TRUSTED_PROXIES;
        } else {
            trustedProxies = trustedProxies.stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }
}