package com.jiber.backend.security;

import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiber.backend.auth.AuthController;
import com.jiber.backend.auth.AuthService;
import com.jiber.backend.auth.AuthUserMapper;
import com.jiber.backend.auth.AuthUserRecord;
import com.jiber.backend.auth.FrontendProperties;
import com.jiber.backend.auth.JwtAuthenticationFilter;
import com.jiber.backend.auth.JwtTokenProperties;
import com.jiber.backend.auth.JwtTokenService;
import com.jiber.backend.auth.LocalOAuth2UserProvisioningService;
import com.jiber.backend.auth.OAuth2ClientRegistrationProperties;
import com.jiber.backend.auth.OAuth2LoginSuccessHandler;
import com.jiber.backend.auth.OAuth2ProviderUserResolver;
import com.jiber.backend.auth.RefreshTokenCookieService;
import com.jiber.backend.auth.RefreshTokenProperties;
import com.jiber.backend.auth.RefreshTokenService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.extension.ExtendWith;

@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorResponseWriter.class,
        JwtAuthenticationFilter.class,
        OAuth2AuthorizationEndpointTest.TestBeans.class
})
@ExtendWith(OutputCaptureExtension.class)
class OAuth2AuthorizationEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredProviderAuthorizationEndpointRedirectsToProviderAuthorizationUri() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://kauth.kakao.com/oauth/authorize?")))
                .andExpect(header().string("Location", not(containsString("access_token="))))
                .andExpect(header().string("Location", not(containsString("refresh_token="))))
                .andExpect(header().string("Location", not(containsString("provider_token="))));
    }

    @Test
    void missingProviderAuthorizationEndpointReturnsSafeUnauthorizedWithoutExceptionLeak(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/oauth2/authorization/naver"))
                .andExpect(content().string(not(containsString("InvalidClientRegistrationIdException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));

        org.assertj.core.api.Assertions.assertThat(output)
                .doesNotContain("InvalidClientRegistrationIdException");
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            var properties = new OAuth2ClientRegistrationProperties(
                    new OAuth2ClientRegistrationProperties.Provider("", "", ""),
                    new OAuth2ClientRegistrationProperties.Provider(
                            "dummy-kakao-client-id",
                            "dummy-kakao-client-secret",
                            "http://localhost:8080/login/oauth2/code/kakao"
                    ),
                    new OAuth2ClientRegistrationProperties.Provider("", "", "")
            );
            return new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(
                    properties.toClientRegistrations()
            );
        }

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

        @Bean
        OAuth2LoginSuccessHandler oauth2LoginSuccessHandler(RefreshTokenCookieService refreshTokenCookieService) {
            return new OAuth2LoginSuccessHandler(
                    new OAuth2ProviderUserResolver(),
                    new LocalOAuth2UserProvisioningService(new FakeAuthUserMapper()),
                    null,
                    refreshTokenCookieService,
                    new FrontendProperties("http://localhost:5173")
            );
        }

        private static class FakeAuthUserMapper implements AuthUserMapper {

            @Override
            public AuthUserRecord findById(Long userId) {
                return null;
            }

            @Override
            public AuthUserRecord findByProvider(String oauthProvider, String providerUserId) {
                return new AuthUserRecord(
                        1L,
                        oauthProvider,
                        providerUserId,
                        "oauth-user@example.test",
                        "OAuth User",
                        "USER",
                        true,
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                );
            }

            @Override
            public int upsertOAuthUser(
                    String oauthProvider,
                    String providerUserId,
                    String email,
                    String displayName,
                    OffsetDateTime lastLoginAt
            ) {
                return 1;
            }
        }
    }
}
