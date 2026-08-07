package dev.achiri.multivault.tenant.mapper;

import dev.achiri.multivault.tenant.dto.CreateOrganizationRequest;
import dev.achiri.multivault.tenant.dto.CreateOrganizationResponse;
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
    TenantMember toEntity(CreateOrganizationRequest.AdminDto dto);

    @Mapping(target = "memberId", source = "id")
    CreateOrganizationResponse.AdminDto toDto(TenantMember member);
}
