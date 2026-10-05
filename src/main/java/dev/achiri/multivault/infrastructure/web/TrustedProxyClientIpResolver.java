package dev.achiri.multivault.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@Component
public class TrustedProxyClientIpResolver implements ClientIpResolver {

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxies;

    public TrustedProxyClientIpResolver(ClientIpProperties properties) {
        this.trustedProxies = properties.trustedProxies().stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    @Override
    public Optional<InetAddress> resolve(HttpServletRequest request) {
        return IpLiterals.parse(request.getRemoteAddr())
                .flatMap(peer -> clientAddressBehindProxies(request, peer));
    }

    private Optional<InetAddress> clientAddressBehindProxies(HttpServletRequest request, InetAddress peer) {
        if (isTrustedProxy(peer)) {
            return rightmostUntrustedHop(request).or(() -> Optional.of(peer));
        }
        return Optional.of(peer);
    }

    private Optional<InetAddress> rightmostUntrustedHop(HttpServletRequest request) {
        String header = request.getHeader(FORWARDED_FOR_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String[] hops = header.split(",");
        for (int index = hops.length - 1; index >= 0; index--) {
            Optional<InetAddress> hop = IpLiterals.parse(hops[index]);
            if (hop.isEmpty()) {
                return Optional.empty();
            }
            if (!isTrustedProxy(hop.get())) {
                return hop;
            }
        }
        return Optional.empty();
    }

    private boolean isTrustedProxy(InetAddress address) {
        String hostAddress = address.getHostAddress();
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(hostAddress));
    }
}