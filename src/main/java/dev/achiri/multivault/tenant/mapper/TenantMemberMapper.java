package dev.achiri.multivault.tenant.mapper;

import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.model.TenantMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TenantMemberMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "firstSeenAt", ignore = true)
    @Mapping(target = "lastSeenAt", ignore = true)
    TenantMember toEntity(CreateTenantRequest.TenantAdminDto dto);

    @Mapping(target = "memberId", source = "id")
    CreateTenantResponse.TenantAdminDto toDto(TenantMember member);
}
