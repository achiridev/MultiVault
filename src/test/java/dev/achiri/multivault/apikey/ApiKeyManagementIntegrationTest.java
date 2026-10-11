package dev.achiri.multivault.apikey;

import com.jayway.jsonpath.JsonPath;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.apikey.service.ApiKeyService;
import dev.achiri.multivault.infrastructure.security.Scopes;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApiKeyManagementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private dev.achiri.multivault.audit.repository.AuditLogRepository auditLogRepository;

    private UUID tenantId;
    private String rootRawKey;
    private UUID rootKeyId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Integration Tenant");
        tenant.setSchemaName("mv_test_" + UUID.randomUUID().toString().replace("-", ""));
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.saveAndFlush(tenant);
        tenantId = tenant.getId();

        ApiKeyResult rootResult = apiKeyService.createInitial(tenantId, UUID.randomUUID());
        rootRawKey = rootResult.rawKey();
        rootKeyId = rootResult.id();
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        apiKeyRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void endToEndApiKeyManagementLifecycle() throws Exception {
        String createStandardBody = """
                {
                  "name": "Standard Documents Key",
                  "keyType": "STANDARD",
                  "scopes": ["documents:read", "documents:write"]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStandardBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.key").exists())
                .andExpect(jsonPath("$.keyPrefix").exists())
                .andExpect(jsonPath("$.keyType").value("STANDARD"))
                .andExpect(jsonPath("$.scopes.length()").value(2))
                .andReturn();

        String createdStandardKeyId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$[0].key").doesNotExist());

        String createServiceBody = """
                {
                  "name": "Service Ops Key",
                  "keyType": "SERVICE",
                  "scopes": ["tenant:settings:write"]
                }
                """;

        MvcResult createServiceResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createServiceBody))
                .andExpect(status().isCreated())
                .andReturn();

        String serviceKeyId = JsonPath.read(createServiceResult.getResponse().getContentAsString(), "$.id");
        String serviceRawKey = JsonPath.read(createServiceResult.getResponse().getContentAsString(), "$.key");

        mockMvc.perform(put("/api/v1/tenants/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/api-keys/" + serviceKeyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/tenants/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        String wildcardBody = """
                {
                  "name": "Illegal Wildcard Key",
                  "keyType": "SERVICE",
                  "scopes": ["*"]
                }
                """;

        mockMvc.perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wildcardBody))
                .andExpect(status().isBadRequest());

        String secondAdminBody = """
                {
                  "name": "Second Admin Key",
                  "keyType": "SERVICE",
                  "scopes": ["api_keys:manage"]
                }
                """;

        mockMvc.perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondAdminBody))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/api-keys/" + rootKeyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey))
                .andExpect(status().isConflict());

        Tenant otherTenant = new Tenant();
        otherTenant.setName("Other Tenant");
        otherTenant.setSchemaName("mv_test_" + UUID.randomUUID().toString().replace("-", ""));
        otherTenant.setStatus(TenantStatus.ACTIVE);
        otherTenant = tenantRepository.saveAndFlush(otherTenant);

        ApiKeyResult otherResult = apiKeyService.createInitial(otherTenant.getId(), UUID.randomUUID());

        mockMvc.perform(delete("/api/v1/api-keys/" + otherResult.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootRawKey))
                .andExpect(status().isNotFound());
    }
}
