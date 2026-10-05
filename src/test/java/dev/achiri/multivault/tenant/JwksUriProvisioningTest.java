package dev.achiri.multivault.tenant;

import dev.achiri.multivault.common.exception.JwksUriInvalidaException;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksProvider;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "multivault.jwks.verify-reachability=false",
        "multivault.jwks.allow-http=false",
        "multivault.jwks.allow-private-hosts=false",
        "multivault.jwks.allowed-ports=443"
})
class JwksUriProvisioningTest extends BaseIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwksProvider jwksProvider;

    private UUID tenantId;
    private String schemaName;

    @AfterEach
    void tearDown() {
        if (schemaName != null) {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
        if (tenantId != null) {
            tenantRepository.deleteById(tenantId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
            "https://169.254.169.254/latest/meta-data/",
            "http://127.0.0.1/jwks",
            "http://10.0.0.5/jwks",
            "http://192.168.1.1/jwks",
            "http://172.16.0.9/jwks",
            "http://[::1]/jwks",
            "http://localhost:8080/jwks",
            "http://idp.internal/jwks",
            "http://metadata.google.internal/computeMetadata/v1/"
    })
    void rejectsInternalNetworkTargetsWithoutCreatingAnything(String jwksUri) {
        String name = "Acme Ssrf Probe";

        assertThatThrownBy(() -> tenantService.create(request(name, jwksUri)))
                .isInstanceOf(JwksUriInvalidaException.class);

        assertThat(tenantRepository.findAll())
                .noneMatch(tenant -> "mv_acme_ssrf_probe".equals(tenant.getSchemaName()));
        assertThat(schemaExists("mv_acme_ssrf_probe")).isFalse();
    }

    @Test
    void rejectsPublicHostWhenSchemeIsNotHttps() {
        assertThatThrownBy(() -> tenantService.create(request("Acme Plain Http", "http://93.184.216.34/jwks")))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("https");

        assertThat(schemaExists("mv_acme_plain_http")).isFalse();
    }

    @Test
    void rejectsDisallowedPort() {
        assertThatThrownBy(() -> tenantService.create(request("Acme Odd Port", "https://93.184.216.34:8443/jwks")))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("puerto");

        assertThat(schemaExists("mv_acme_odd_port")).isFalse();
    }

    @Test
    void runtimeFetchRejectsStoredInternalUriFromLegacyRows() {
        assertThatThrownBy(() -> jwksProvider.fetch("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(JwksUriInvalidaException.class);

        assertThatThrownBy(() -> jwksProvider.fetch("http://10.1.2.3/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class);
    }

    @Test
    void mapsRejectedJwksUriToBadRequest() throws Exception {
        String body = """
                {
                  "name": "Acme Rejected Metadata",
                  "planId": "%s",
                  "admin": { "subject": "sub_a", "email": "admin.rejected@acme.com" },
                  "identityProvider": {
                    "issuer": "https://idp.acme.com",
                    "jwksUri": "https://169.254.169.254/latest/meta-data/",
                    "audience": "https://api.acme.com"
                  }
                }
                """.formatted(activePlan().getId());

        mockMvc.perform(post("/api/v1/tenants")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("jwks_uri no puede apuntar a un host de red interna"));

        assertThat(schemaExists("mv_acme_rejected_metadata")).isFalse();
    }

    private Plan activePlan() {
        return planRepository.findAll().stream().filter(Plan::getIsActive).findFirst().orElseThrow();
    }

    private CreateTenantRequest request(String name, String jwksUri) {
        return new CreateTenantRequest(name, activePlan().getId(),
                new CreateTenantRequest.TenantAdminDto("sub_" + name.hashCode(), "admin@" + name.hashCode() + ".acme.com", "Admin"),
                new CreateTenantRequest.TenantIdentityProviderDto(
                        "https://idp.acme.com",
                        jwksUri,
                        "https://api.acme.com",
                        null,
                        null));
    }

    private boolean schemaExists(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, name);
        return count != null && count > 0;
    }
}