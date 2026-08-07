package dev.achiri.multivault.subscription.mapper;

import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriptionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "planId", source = "request.planId")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "startsAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "endsAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Subscription toEntity(CreateTenantRequest request);

    @Mapping(target = "id", source = "subscription.id")
    @Mapping(target = "planCode", source = "plan.code")
    CreateTenantResponse.SubscriptionDto toDto(Subscription subscription, Plan plan);
}
