package com.jiber.backend.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiber.backend.auth.AuthController;
import com.jiber.backend.auth.AuthService;
import com.jiber.backend.auth.JwtAuthenticationFilter;
import com.jiber.backend.auth.JwtTokenProperties;
import com.jiber.backend.auth.JwtTokenService;
import com.jiber.backend.auth.RefreshTokenCookieService;
import com.jiber.backend.auth.RefreshTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorResponseWriter.class,
        JwtAuthenticationFilter.class,
        OAuth2AuthorizationEndpointDisabledTest.TestBeans.class
})
class OAuth2AuthorizationEndpointDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disabledOAuthAuthorizationEndpointReturnsSafeUnauthorized() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/oauth2/authorization/kakao"))
                .andExpect(content().string(not(containsString("INTERNAL_ERROR"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        JwtTokenService jwtTokenService(ObjectMapper objectMapper) {
            return new JwtTokenService(
                    new JwtTokenProperties("jiber-test", "test-secret-with-enough-entropy-for-hmac", 900, "test"),
                    objectMapper
            );
        }

        @Bean
        RefreshTokenCookieService refreshTokenCookieService() {
            return new RefreshTokenCookieService(
                    new RefreshTokenProperties(
                            1209600,
                            "local",
                            new RefreshTokenProperties.Cookie("JIBER_REFRESH_TOKEN", "/api/v1/auth", "Lax", false)
                    )
            );
        }

        @Bean
        AuthService authService(JwtTokenService jwtTokenService) {
            return new AuthService(jwtTokenService, null, null);
        }
    }
}
