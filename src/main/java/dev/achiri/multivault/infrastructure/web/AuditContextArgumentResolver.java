package dev.achiri.multivault.infrastructure.web;

import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.audit.event.AuditContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuditContextResolver auditContextResolver;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuditContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return auditContextResolver.resolve(ownerUserId(request), authentication, request);
    }

    private UUID ownerUserId(HttpServletRequest request) {
        String value = request.getParameter("ownerUserId");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ownerUserId inválido");
        }
    }
}
