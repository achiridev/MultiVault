package dev.achiri.multivault.infrastructure.persistence.config;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

import java.util.Set;

public class TenantSchemaFilterProvider implements SchemaFilterProvider {

    private static final Set<String> TENANT_TABLES = Set.of(
            "folder",
            "document",
            "document_version",
            "document_permission");

    private static final SchemaFilter FILTER = new SchemaFilter() {
        @Override
        public boolean includeNamespace(Namespace namespace) {
            return true;
        }

        @Override
        public boolean includeTable(Table table) {
            return !TENANT_TABLES.contains(table.getName());
        }

        @Override
        public boolean includeSequence(Sequence sequence) {
            return true;
        }
    };

    @Override
    public SchemaFilter getCreateFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getDropFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getTruncatorFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return FILTER;
    }
}
