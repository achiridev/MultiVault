package dev.achiri.multivault.infrastructure.security.jwt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "multivault.jwks")
@Getter
@Setter
public class JwksProperties {

    private boolean allowHttp;
    private boolean allowPrivateHosts;
    private List<String> allowedPorts = List.of("443");
    private List<String> allowedHosts = List.of();
    private boolean verifyReachability = true;
    private long probeTimeoutMillis = 5000;
    private long maxBodyBytes = 262144;
}