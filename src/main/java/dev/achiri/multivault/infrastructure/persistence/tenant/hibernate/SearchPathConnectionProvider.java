package dev.achiri.multivault.infrastructure.persistence.tenant.hibernate;

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SearchPathConnectionProvider implements ConnectionProvider {

    private static final String RESET_SEARCH_PATH = "SET search_path TO public";

    private final DataSource dataSource;
    private final String schemaName;

    SearchPathConnectionProvider(DataSource dataSource, String schemaName) {
        this.dataSource = dataSource;
        this.schemaName = schemaName;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        if (schemaName != null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO \"" + escape(schemaName) + "\"");
            }
        }
        return connection;
    }

    @Override
    public void closeConnection(Connection connection) throws SQLException {
        try {
            if (schemaName != null) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(RESET_SEARCH_PATH);
                }
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean handlesConnectionSchema() {
        return schemaName != null;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <X> X unwrap(Class<X> unwrapType) {
        return null;
    }

    private String escape(String value) {
        return value.replace("\"", "\"\"");
    }
}
