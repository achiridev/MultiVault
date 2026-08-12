package dev.achiri.multivault.infrastructure.persistence.tenant;

import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantSchemaResolver {

    private final TenantRepository tenantRepository;

    public String resolve(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant", tenantId));
        return tenant.getSchemaName();
    }
}
