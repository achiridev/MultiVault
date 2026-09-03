package dev.achiri.multivault.tenant.usage;

import dev.achiri.multivault.common.exception.AlmacenamientoPlanExcedidoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantUsage;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final TenantUsageRepository tenantUsageRepository;

    @Transactional(readOnly = true)
    public void assertCapacity(UUID tenantId, long additionalBytes) {
        Tenant tenant = requireTenant(tenantId);
        Plan plan = requirePlan(tenant.getCurrentPlanId());
        long usedBytes = storageBytesUsed(tenantId);
        if (usedBytes + additionalBytes > plan.getMaxStorageBytes()) {
            throw new AlmacenamientoPlanExcedidoException(usedBytes + additionalBytes, plan.getMaxStorageBytes());
        }
    }

    @Transactional(readOnly = true)
    public long storageBytesUsed(UUID tenantId) {
        return tenantUsageRepository.findByTenantId(tenantId)
                .map(TenantUsage::getStorageBytesUsed)
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant_usage", tenantId));
    }

    @Transactional
    public void addStorageBytes(UUID tenantId, long addedBytes) {
        tenantUsageRepository.incrementStorageBytes(tenantId, addedBytes);
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant", tenantId));
    }

    private Plan requirePlan(UUID planId) {
        if (planId == null) {
            throw new RecursoNoEncontradoException("plan", planId);
        }
        return planRepository.findById(planId)
                .orElseThrow(() -> new RecursoNoEncontradoException("plan", planId));
    }
}
