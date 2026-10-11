package dev.achiri.multivault.apikey;

import dev.achiri.multivault.apikey.service.ApiKeyScopeValidator;
import dev.achiri.multivault.common.exception.ScopeInvalidoException;
import dev.achiri.multivault.infrastructure.security.Scopes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyScopeValidatorTest {

    private final ApiKeyScopeValidator validator = new ApiKeyScopeValidator();

    @Test
    void rejectsWildcard() {
        assertThatThrownBy(() -> validator.validateAndNormalize(List.of("*")))
                .isInstanceOf(ScopeInvalidoException.class)
                .hasMessageContaining("wildcard '*' no está permitido");
    }

    @Test
    void rejectsUnknownScope() {
        assertThatThrownBy(() -> validator.validateAndNormalize(List.of("documents:read", "invalid:scope")))
                .isInstanceOf(ScopeInvalidoException.class)
                .hasMessageContaining("Scopes no permitidos o no reconocidos");
    }

    @Test
    void rejectsEmptyOrNullList() {
        assertThatThrownBy(() -> validator.validateAndNormalize(Collections.emptyList()))
                .isInstanceOf(ScopeInvalidoException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize(null))
                .isInstanceOf(ScopeInvalidoException.class);
    }

    @Test
    void deduplicatesAndSortsScopes() {
        List<String> input = List.of(
                Scopes.DOCUMENTS_WRITE,
                Scopes.DOCUMENTS_READ,
                Scopes.DOCUMENTS_WRITE
        );

        List<String> result = validator.validateAndNormalize(input);

        assertThat(result).containsExactly(Scopes.DOCUMENTS_READ, Scopes.DOCUMENTS_WRITE);
    }

    @Test
    void acceptsAllCatalogScopes() {
        List<String> result = validator.validateAndNormalize(Scopes.all());

        assertThat(result).hasSize(Scopes.all().size());
    }
}
