package dev.achiri.multivault.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyClientIpResolverTest {

    private static final String FORWARDED_FOR = TrustedProxyClientIpResolver.FORWARDED_FOR_HEADER;

    @Test
    void fallsBackToRemoteAddressWhenNoProxyIsTrusted() {
        ClientIpResolver resolver = resolverWith();

        MockHttpServletRequest request = request("198.51.100.7", "203.0.113.9");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("198.51.100.7");
    }

    @Test
    void resolvesRemoteAddressWhenNoForwardedForHeaderIsPresent() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(request("10.1.2.3", null))).map(InetAddress::getHostAddress)
                .contains("10.1.2.3");
    }

    @Test
    void takesRightmostUntrustedHopFromTrustedProxy() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        MockHttpServletRequest request = request("10.0.0.1", "203.0.113.5, 10.0.0.2, 10.0.0.3");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("203.0.113.5");
    }

    @Test
    void walksForwardedChainUntilUntrustedHopIsFound() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        MockHttpServletRequest request = request("10.0.0.1", "203.0.113.5, 10.0.0.2");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("203.0.113.5");
    }

    @Test
    void rejectsForwardedChainWhoseEveryHopIsTrusted() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        MockHttpServletRequest request = request("10.0.0.1", "10.0.0.2, 10.0.0.3");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("10.0.0.1");
    }

    @Test
    void rejectsForwardedChainContainingHostnamesInsteadOfResolvingThem() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        MockHttpServletRequest request = request("10.0.0.1", "203.0.113.5, attacker.example.com");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("10.0.0.1");
    }

    @Test
    void rejectsHexOnlyTokenThatWouldOtherwiseTriggerNameResolution() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        MockHttpServletRequest request = request("10.0.0.1", "203.0.113.5, deadbeef");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("10.0.0.1");
    }

    @Test
    void resolvesIpv6ClientBehindIpv6Proxy() {
        ClientIpResolver resolver = resolverWith("2001:db8:1::/48");

        MockHttpServletRequest request = request("2001:db8:1::5", "2001:db8:2::7");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("2001:db8:2:0:0:0:0:7");
    }

    @Test
    void stripsPortFromForwardedIpv4Hop() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        MockHttpServletRequest request = request("10.0.0.1", "203.0.113.5:54321");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("203.0.113.5");
    }

    @Test
    void stripsBracketsAndPortFromForwardedIpv6Hop() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        MockHttpServletRequest request = request("10.0.0.1", "[2001:db8:2::7]:443");

        assertThat(resolver.resolve(request)).map(InetAddress::getHostAddress)
                .contains("2001:db8:2:0:0:0:0:7");
    }

    @Test
    void resolvesNothingWhenRemoteAddressIsAnOctetOutOfRange() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(request("999.1.1.1", null))).isEmpty();
    }

    @Test
    void resolvesNothingWhenRemoteAddressIsANonNumericToken() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(request("localhost", null))).isEmpty();
    }

    @Test
    void resolvesNothingWhenRemoteAddressIsMissing() {
        ClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(request(null, null))).isEmpty();
    }

    private ClientIpResolver resolverWith(String... trustedProxies) {
        return new TrustedProxyClientIpResolver(new ClientIpProperties(List.of(trustedProxies)));
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader(FORWARDED_FOR, forwardedFor);
        }
        return request;
    }
}