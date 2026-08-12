package dev.achiri.multivault.infrastructure.persistence.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String> {

    public static final String PUBLIC_SCHEMA = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getSchema();
        return schema != null ? schema : PUBLIC_SCHEMA;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(String tenantIdentifier) {
        return PUBLIC_SCHEMA.equals(tenantIdentifier);
    }
}
