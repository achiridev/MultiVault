package dev.achiri.multivault.security;

import dev.achiri.multivault.infrastructure.security.ScopeAuthorities;
import dev.achiri.multivault.infrastructure.security.Scopes;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeAuthoritiesTest {

    @Test
    void expandsWildcardToWholeCatalog() {
        List<String> authorities = names(ScopeAuthorities.from(List.of(Scopes.WILDCARD)));

        assertThat(authorities).containsExactlyElementsOf(
                Scopes.all().stream().map(scope -> "SCOPE_" + scope).toList());
        assertThat(authorities).hasSize(Scopes.all().size());
    }

    @Test
    void keepsExplicitScopesInOrder() {
        List<String> authorities = names(ScopeAuthorities.from(
                List.of(Scopes.DOCUMENTS_WRITE, Scopes.DOCUMENTS_READ, Scopes.AUDIT_READ)));

        assertThat(authorities).containsExactly(
                "SCOPE_documents:write", "SCOPE_documents:read", "SCOPE_audit:read");
    }

    @Test
    void returnsEmptyForNoScopes() {
        assertThat(ScopeAuthorities.from(List.of())).isEmpty();
    }

    @Test
    void deduplicatesScopesCoveredByWildcard() {
        List<String> authorities = names(ScopeAuthorities.from(List.of(Scopes.WILDCARD, Scopes.DOCUMENTS_READ)));

        assertThat(authorities).hasSize(Scopes.all().size()).doesNotHaveDuplicates();
        assertThat(authorities).contains("SCOPE_documents:read");
    }

    @Test
    void deduplicatesRepeatedScopes() {
        List<String> authorities = names(ScopeAuthorities.from(List.of(Scopes.DOCUMENTS_READ, Scopes.DOCUMENTS_READ)));

        assertThat(authorities).containsExactly("SCOPE_documents:read");
    }

    private List<String> names(List<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
