package dev.achiri.multivault.tenant.provisioning;

import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.model.TenantMember;

public record OnboardingResult(
        Tenant tenant,
        Subscription subscription,
        TenantMember admin,
        TenantIdentityProvider identityProvider
) {
}
