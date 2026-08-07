package dev.achiri.multivault.infrastructure.persistence.tenant;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class TenantSchemaProvisioner {

    private static final String SCHEMA_PATTERN = "^[a-z][a-z0-9_]*$";
    private static final String TENANT_MIGRATIONS_LOCATION = "classpath:db/tenant";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public void provision(String schemaName) {
        validateSchemaName(schemaName);
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(TENANT_MIGRATIONS_LOCATION)
                .load()
                .migrate();
    }

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || !schemaName.matches(SCHEMA_PATTERN)) {
            throw new IllegalArgumentException("schema_name inválido: " + schemaName);
        }
    }
}
