package dev.achiri.multivault.tenant.mapper;

import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.dto.UpdateTenantIdentityProviderRequest;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TenantIdentityProviderMapper {

    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "allowedAlgorithms",
            expression = "java(dto.allowedAlgorithms() == null ? java.util.List.of(\"RS256\") : dto.allowedAlgorithms())")
    @Mapping(target = "clockSkewSeconds",
            expression = "java(dto.clockSkewSeconds() == null ? 60 : dto.clockSkewSeconds())")
    TenantIdentityProvider toEntity(CreateTenantRequest.TenantIdentityProviderDto dto);

    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "allowedAlgorithms",
            expression = "java(dto.allowedAlgorithms() == null ? java.util.List.of(\"RS256\") : dto.allowedAlgorithms())")
    @Mapping(target = "clockSkewSeconds",
            expression = "java(dto.clockSkewSeconds() == null ? 60 : dto.clockSkewSeconds())")
    TenantIdentityProvider toEntity(UpdateTenantIdentityProviderRequest dto);

    CreateTenantResponse.TenantIdentityProviderDto toDto(TenantIdentityProvider entity);
}
