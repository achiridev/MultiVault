package dev.achiri.multivault.tenant.mapper;

import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.model.Tenant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TenantMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "schemaName", ignore = true)
    @Mapping(target = "suspendedAt", ignore = true)
    @Mapping(target = "suspendedReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "currentPlanId", source = "planId")
    Tenant toEntity(CreateTenantRequest request);

    CreateTenantResponse.TenantDto toDto(Tenant tenant);
}
