package dev.achiri.multivault.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class IpLiteralsTest {

    @Test
    void parsesIpv4() {
        assertThat(IpLiterals.parse("203.0.113.5")).map(InetAddress::getHostAddress)
                .contains("203.0.113.5");
    }

    @Test
    void parsesIpv4WithPort() {
        assertThat(IpLiterals.parse("203.0.113.5:8080")).map(InetAddress::getHostAddress)
                .contains("203.0.113.5");
    }

    @Test
    void parsesBracketedIpv4WithPort() {
        assertThat(IpLiterals.parse("[203.0.113.5]:8080")).map(InetAddress::getHostAddress)
                .contains("203.0.113.5");
    }

    @Test
    void parsesIpv6() {
        assertThat(IpLiterals.parse("2001:db8::7")).map(InetAddress::getHostAddress)
                .contains("2001:db8:0:0:0:0:0:7");
    }

    @Test
    void parsesBracketedIpv6WithPort() {
        assertThat(IpLiterals.parse("[2001:db8::7]:443")).map(InetAddress::getHostAddress)
                .contains("2001:db8:0:0:0:0:0:7");
    }

    @Test
    void parsesSurroundedByWhitespace() {
        assertThat(IpLiterals.parse("  203.0.113.5  ")).map(InetAddress::getHostAddress)
                .contains("203.0.113.5");
    }

    @Test
    void rejectsHostname() {
        assertThat(IpLiterals.parse("example.com")).isEmpty();
    }

    @Test
    void rejectsHexOnlyToken() {
        assertThat(IpLiterals.parse("deadbeef")).isEmpty();
    }

    @Test
    void rejectsOctetOutOfRange() {
        assertThat(IpLiterals.parse("203.0.113.256")).isEmpty();
    }

    @Test
    void rejectsShortIpv4() {
        assertThat(IpLiterals.parse("203.0.113")).isEmpty();
    }

    @Test
    void rejectsEmptyOctet() {
        assertThat(IpLiterals.parse("203.0.113.")).isEmpty();
    }

    @Test
    void rejectsNonNumericOctet() {
        assertThat(IpLiterals.parse("203.0.113.a")).isEmpty();
    }

    @Test
    void rejectsUnclosedBracket() {
        assertThat(IpLiterals.parse("[203.0.113.5")).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(IpLiterals.parse(null)).isEmpty();
    }

    @Test
    void rejectsBlank() {
        assertThat(IpLiterals.parse("   ")).isEmpty();
    }

    @Test
    void rejectsTrailingContentAfterPort() {
        assertThat(IpLiterals.parse("203.0.113.5:80:90")).isEmpty();
    }
}