package dev.achiri.multivault.apikey.service;

import dev.achiri.multivault.common.exception.ScopeInvalidoException;
import dev.achiri.multivault.infrastructure.security.Scopes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ApiKeyScopeValidator {

    private static final Set<String> ALLOWED_SCOPES = new HashSet<>(Scopes.all());

    public List<String> validateAndNormalize(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new ScopeInvalidoException("La lista de scopes no puede estar vacía");
        }
        List<String> invalidScopes = new ArrayList<>();
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                invalidScopes.add(scope == null ? "null" : "");
                continue;
            }
            if ("*".equals(scope.trim())) {
                throw new ScopeInvalidoException("El wildcard '*' no está permitido como scope");
            }
            if (!ALLOWED_SCOPES.contains(scope.trim())) {
                invalidScopes.add(scope);
            }
        }
        if (!invalidScopes.isEmpty()) {
            throw new ScopeInvalidoException("Scopes no permitidos o no reconocidos: " + invalidScopes);
        }
        List<String> normalized = scopes.stream()
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        return Collections.unmodifiableList(normalized);
    }
}
