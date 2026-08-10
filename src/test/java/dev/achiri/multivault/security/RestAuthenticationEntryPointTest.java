package dev.achiri.multivault.security;

import dev.achiri.multivault.infrastructure.security.handler.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    @Test
    void writesUnauthorizedErrorResponse() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(new JsonMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new BadCredentialsException("nope"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"mensaje\":\"Autenticación requerida\"");
    }
}
