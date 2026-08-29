package dev.achiri.multivault.infrastructure.persistence.tenant;

import dev.achiri.multivault.audit.repository.AuditLogRepository;
import dev.achiri.multivault.document.model.Document;
import dev.achiri.multivault.document.model.DocumentStatus;
import dev.achiri.multivault.document.repository.DocumentRepository;
import dev.achiri.multivault.infrastructure.persistence.tenant.context.TenantContext;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSchemaIsolationTest extends BaseIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> tenantIds = new ArrayList<>();
    private final List<String> schemaNames = new ArrayList<>();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        schemaNames.forEach(schema -> jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"));
        tenantIds.forEach(tenantId -> {
            auditLogRepository.deleteAll(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId));
            tenantRepository.deleteById(tenantId);
        });
    }

    @Test
    void routesJpaQueriesToActiveTenantSchema() {
        CreateTenantResponse tenantA = createTenant("Acme Isolation A", "sub_isolation_a");
        CreateTenantResponse tenantB = createTenant("Acme Isolation B", "sub_isolation_b");

        TenantContext.setSchema(tenantA.tenant().schemaName());
        Document documentA = saveDocument(UUID.randomUUID());
        assertThat(documentRepository.count()).isEqualTo(1);
        assertThat(documentRepository.findById(documentA.getId())).isPresent();

        TenantContext.setSchema(tenantB.tenant().schemaName());
        assertThat(documentRepository.count()).isZero();
        assertThat(documentRepository.findById(documentA.getId())).isEmpty();

        TenantContext.setSchema(tenantA.tenant().schemaName());
        assertThat(documentRepository.count()).isEqualTo(1);

        Integer rowsInA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tenantA.tenant().schemaName() + ".document", Integer.class);
        Integer rowsInB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tenantB.tenant().schemaName() + ".document", Integer.class);
        assertThat(rowsInA).isEqualTo(1);
        assertThat(rowsInB).isZero();
    }

    @Test
    void publicTablesRemainQueryableWithoutTenantContext() {
        createTenant("Acme Isolation Public", "sub_isolation_public");

        TenantContext.clear();

        assertThat(planRepository.count()).isGreaterThan(0);
        assertThat(tenantRepository.count()).isGreaterThanOrEqualTo(1);
    }

    private CreateTenantResponse createTenant(String name, String subject) {
        Plan plan = planRepository.findAll().stream().filter(Plan::getIsActive).findFirst().orElseThrow();
        CreateTenantResponse response = tenantService.create(new CreateTenantRequest(
                name,
                plan.getId(),
                new CreateTenantRequest.TenantAdminDto(subject, subject + "@acme.com", "Admin"),
                new CreateTenantRequest.TenantIdentityProviderDto(
                        "https://idp.acme.com",
                        "https://idp.acme.com/.well-known/jwks.json",
                        "https://api.acme.com",
                        null,
                        null)));
        tenantIds.add(response.tenant().id());
        schemaNames.add(response.tenant().schemaName());
        return response;
    }

    private Document saveDocument(UUID ownerUserId) {
        Document document = new Document();
        document.setOwnerUserId(ownerUserId);
        document.setStatus(DocumentStatus.ACTIVE);
        return documentRepository.save(document);
    }
}
