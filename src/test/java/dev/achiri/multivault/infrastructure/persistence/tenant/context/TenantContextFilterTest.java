package dev.achiri.multivault.infrastructure.persistence.tenant.context;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.service.TenantService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextFilterTest extends BaseIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantContextFilter tenantContextFilter;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private String schemaName;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        if (schemaName != null) {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
        if (tenantId != null) {
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void resolvesTenantFromJwtPrincipal() throws ServletException, IOException {
        CreateTenantResponse response = createTenant("Acme Filter Jwt", "sub_filter_jwt");
        TenantUserPrincipal principal = new TenantUserPrincipal(UUID.randomUUID(), response.tenant().id(), "sub_filter_jwt");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        AtomicReference<String> schemaDuringRequest = new AtomicReference<>();
        tenantContextFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                            throws IOException, ServletException {
                        schemaDuringRequest.set(TenantContext.getSchema());
                        super.doFilter(request, response);
                    }
                });

        assertThat(schemaDuringRequest.get()).isEqualTo(response.tenant().schemaName());
        assertThat(TenantContext.getSchema()).isNull();
    }

    @Test
    void resolvesTenantFromApiKeyPrincipal() throws ServletException, IOException {
        CreateTenantResponse response = createTenant("Acme Filter Key", "sub_filter_key");
        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                UUID.randomUUID(), response.tenant().id(), "service-key", ApiKeyType.SERVICE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        AtomicReference<String> schemaDuringRequest = new AtomicReference<>();
        tenantContextFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                            throws IOException, ServletException {
                        schemaDuringRequest.set(TenantContext.getSchema());
                        super.doFilter(request, response);
                    }
                });

        assertThat(schemaDuringRequest.get()).isEqualTo(response.tenant().schemaName());
        assertThat(TenantContext.getSchema()).isNull();
    }

    @Test
    void leavesContextClearForAnonymousRequest() throws ServletException, IOException {
        AtomicReference<String> schemaDuringRequest = new AtomicReference<>();
        tenantContextFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                            throws IOException, ServletException {
                        schemaDuringRequest.set(TenantContext.getSchema());
                        super.doFilter(request, response);
                    }
                });

        assertThat(schemaDuringRequest.get()).isNull();
        assertThat(TenantContext.getSchema()).isNull();
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
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();
        return response;
    }
}
