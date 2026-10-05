package dev.achiri.multivault.infrastructure.persistence.tenant.context;

import dev.achiri.multivault.tenant.model.TenantStatus;

public record TenantSchemaRef(String schemaName, TenantStatus status) {
}