package dev.achiri.multivault.infrastructure.persistence.tenant.context;

import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantSchemaResolver {

    private final TenantRepository tenantRepository;

    public TenantSchemaRef resolve(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNoEncontradoException(tenantId));
        return new TenantSchemaRef(tenant.getSchemaName(), tenant.getStatus());
    }
}