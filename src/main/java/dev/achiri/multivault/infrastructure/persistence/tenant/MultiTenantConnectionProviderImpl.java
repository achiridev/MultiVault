package dev.achiri.multivault.infrastructure.persistence.tenant;

import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class MultiTenantConnectionProviderImpl extends AbstractMultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    @Override
    protected ConnectionProvider getAnyConnectionProvider() {
        return new SearchPathConnectionProvider(dataSource, null);
    }

    @Override
    protected ConnectionProvider selectConnectionProvider(String tenantIdentifier) {
        if (CurrentTenantIdentifierResolverImpl.PUBLIC_SCHEMA.equals(tenantIdentifier)) {
            return getAnyConnectionProvider();
        }
        return new SearchPathConnectionProvider(dataSource, tenantIdentifier);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }
}
