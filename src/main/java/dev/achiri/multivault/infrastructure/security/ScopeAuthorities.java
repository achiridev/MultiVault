package dev.achiri.multivault.infrastructure.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.stream.Stream;

public final class ScopeAuthorities {

    private static final String AUTHORITY_PREFIX = "SCOPE_";

    private ScopeAuthorities() {
    }

    public static List<SimpleGrantedAuthority> from(List<String> scopes) {
        return scopes.stream()
                .flatMap(scope -> Scopes.WILDCARD.equals(scope) ? Scopes.all().stream() : Stream.of(scope))
                .distinct()
                .map(scope -> new SimpleGrantedAuthority(AUTHORITY_PREFIX + scope))
                .toList();
    }
}
