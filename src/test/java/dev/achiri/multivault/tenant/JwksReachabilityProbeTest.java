package dev.achiri.multivault.tenant;

import com.sun.net.httpserver.HttpServer;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import dev.achiri.multivault.common.exception.JwksUriInvalidaException;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.dto.UpdateTenantIdentityProviderRequest;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "multivault.jwks.verify-reachability=true")
class JwksReachabilityProbeTest extends BaseIntegrationTest {

    private static final String RSA_JWKS = """
            {"keys":[{"kid":"k1","kty":"RSA","alg":"RS256","n":"abc","e":"AQAB"}]}
            """;

    private static final String REJECTED_MESSAGE = "jwks_uri rechazada: no alcanzable o sin un JWKS válido";

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantIdentityProviderRepository identityProviderRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpServer idpServer;
    private String reachableUri;
    private String unreachableUri;
    private String nonJsonUri;
    private String errorStatusUri;
    private UUID tenantId;
    private String schemaName;

    @BeforeEach
    void setUp() {
        reachableUri = serve(RSA_JWKS);
        nonJsonUri = serve("<html>login required</html>");
        errorStatusUri = serve(null);
        unreachableUri = closedUri();
    }

    @AfterEach
    void tearDown() {
        if (idpServer != null) {
            idpServer.stop(0);
        }
        if (schemaName != null) {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
        if (tenantId != null) {
            auditLogRepository.deleteAll(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId));
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void createsTenantWhenJwksUriServesUsableRsaKey() {
        CreateTenantResponse response = tenantService.create(request("Acme Reachable IdP", reachableUri));

        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();
        awaitActiveTenant(tenantId);

        assertThat(response.identityProvider().jwksUri()).isEqualTo(reachableUri);
        assertThat(schemaExists(schemaName)).isTrue();
    }

    @Test
    void rejectsUnreachableJwksUriWithoutCreatingAnything() {
        assertThatThrownBy(() -> tenantService.create(request("Acme Unreachable IdP", unreachableUri)))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);

        assertThat(tenantRepository.findAll())
                .noneMatch(tenant -> "mv_acme_unreachable_idp".equals(tenant.getSchemaName()));
        assertThat(schemaExists("mv_acme_unreachable_idp")).isFalse();
    }

    @Test
    void rejectsJwksUriReturningNonJsonBody() {
        assertThatThrownBy(() -> tenantService.create(request("Acme Non Json IdP", nonJsonUri)))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);
    }

    @Test
    void rejectsJwksUriWithoutUsableRsaKey() {
        String ecOnlyUri = serve("{\"keys\":[{\"kid\":\"k1\",\"kty\":\"EC\",\"alg\":\"ES256\",\"n\":\"\",\"e\":\"\"}]}");

        assertThatThrownBy(() -> tenantService.create(request("Acme Ec Only IdP", ecOnlyUri)))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);
    }

    @Test
    void rejectsJwksUriRespondingWithErrorStatus() {
        assertThatThrownBy(() -> tenantService.create(request("Acme Error IdP", errorStatusUri)))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);
    }

    @Test
    void rejectsIdentityProviderUpdateWithUnreachableUriAndKeepsPreviousValue() {
        CreateTenantResponse response = tenantService.create(request("Acme Update Unreachable", reachableUri));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        assertThatThrownBy(() -> tenantService.updateIdentityProvider(tenantId,
                new UpdateTenantIdentityProviderRequest(
                        "https://idp.acme.com/v2",
                        unreachableUri,
                        "https://api.acme.com",
                        null,
                        null)))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);

        assertThat(identityProviderRepository.findById(tenantId).orElseThrow().getJwksUri())
                .isEqualTo(reachableUri);
    }

    @Test
    void mapsUnreachableJwksUriOnUpdateToBadRequest() throws Exception {
        CreateTenantResponse response = tenantService.create(request("Acme Update Http", reachableUri));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();
        awaitActiveTenant(tenantId);

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + response.apiKey().key())
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "issuer": "https://idp.acme.com/v2",
                                  "jwksUri": "%s",
                                  "audience": "https://api.acme.com"
                                }
                                """.formatted(unreachableUri)))
                .andExpect(status().isBadRequest());

        TenantIdentityProvider stored = identityProviderRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getJwksUri()).isEqualTo(reachableUri);
        assertThat(stored.getIssuer()).isEqualTo("https://idp.acme.com");
    }

    private String serve(String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/jwks", exchange -> {
                if (body == null) {
                    exchange.sendResponseHeaders(500, -1);
                    exchange.close();
                    return;
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
            return "http://localhost:" + server.getAddress().getPort() + "/jwks";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String closedUri() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            String uri = "http://localhost:" + server.getAddress().getPort() + "/jwks";
            server.stop(0);
            return uri;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Plan activePlan() {
        return planRepository.findAll().stream().filter(Plan::getIsActive).findFirst().orElseThrow();
    }

    private CreateTenantRequest request(String name, String jwksUri) {
        return new CreateTenantRequest(name, activePlan().getId(),
                new CreateTenantRequest.TenantAdminDto(
                        "sub_" + name.hashCode(), "admin@" + Math.abs(name.hashCode()) + ".acme.com", "Admin"),
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