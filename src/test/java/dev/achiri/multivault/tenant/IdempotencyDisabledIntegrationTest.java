package dev.achiri.multivault.tenant;

import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con el flag apagado el filtro no debe hacer nada: ni validar la clave, ni
 * reclamar, ni responder 422 por fingerprint distinto. El mismo
 * Idempotency-Key con dos cuerpos distintos crea dos tenants.
 */
@TestPropertySource(properties = "multivault.idempotency.enabled=false")
@AutoConfigureMockMvc
class IdempotencyDisabledIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Test
    void reusingAKeyWithADifferentBodyCreatesASecondTenantInsteadOfFailing() throws Exception {
        String key = UUID.randomUUID().toString();
        long tenantsBefore = tenantRepository.count();

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithName("Disabled " + UUID.randomUUID())))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().doesNotExist("Idempotency-Replayed"));

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithName("Disabled " + UUID.randomUUID())))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().doesNotExist("Idempotency-Replayed"));

        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore + 2);
    }

    @Test
    void malformedKeysAreNotValidatedWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithName("Malformed " + UUID.randomUUID())))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().doesNotExist("Idempotency-Replayed"));
    }

    private String bodyWithName(String name) {
        return """
                {
                  "name": "%s",
                  "planId": "%s",
                  "admin": {
                    "subject": "sub_idem",
                    "email": "admin@idem.test"
                  },
                  "identityProvider": {
                    "issuer": "https://idp.idem.test",
                    "jwksUri": "https://idp.idem.test/v2/.well-known/jwks.json",
                    "audience": "https://api.idem.test",
                    "allowedAlgorithms": ["RS256"]
                  }
                }
                """.formatted(name, activePlanId());
    }

    private UUID activePlanId() {
        return planRepository.findAll().stream()
                .filter(Plan::getIsActive)
                .findFirst()
                .orElseThrow()
                .getId();
    }
}