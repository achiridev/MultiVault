package dev.achiri.multivault.infrastructure.persistence.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiTenantJpaConfig {

    @Bean
    HibernatePropertiesCustomizer multiTenantPropertiesCustomizer(
            MultiTenantConnectionProviderImpl connectionProvider,
            CurrentTenantIdentifierResolverImpl tenantIdentifierResolver) {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
