package dev.achiri.multivault.infrastructure.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class IpLiterals {

    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9a-fA-F:]+$");

    private IpLiterals() {
    }

    static Optional<InetAddress> parse(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String candidate = rawValue.trim();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        if (candidate.startsWith("[")) {
            int closing = candidate.indexOf(']');
            return closing < 0 ? Optional.empty() : withoutPort(candidate.substring(1, closing));
        }
        return withoutPort(candidate);
    }

    private static Optional<InetAddress> withoutPort(String candidate) {
        if (isIpv4(candidate) || isIpv6(candidate)) {
            return literal(candidate);
        }
        if (candidate.indexOf(':') != candidate.lastIndexOf(':')) {
            return Optional.empty();
        }
        int separator = candidate.indexOf(':');
        if (separator > 0 && isIpv4(candidate.substring(0, separator))) {
            return literal(candidate.substring(0, separator));
        }
        return Optional.empty();
    }

    private static boolean isIpv4(String candidate) {
        String[] octets = candidate.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            for (int index = 0; index < octet.length(); index++) {
                if (!Character.isDigit(octet.charAt(index))) {
                    return false;
                }
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6(String candidate) {
        Matcher matcher = IPV6_LITERAL.matcher(candidate);
        return candidate.indexOf(':') >= 0 && matcher.matches();
    }

    private static Optional<InetAddress> literal(String candidate) {
        try {
            return Optional.of(InetAddress.getByName(candidate));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}