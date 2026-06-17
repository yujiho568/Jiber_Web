package com.jiber.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class OAuth2LoginSuccessHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T07:00:00Z"), ZoneOffset.UTC);

    @Test
    void oauthSuccessSetsRefreshCookieAndRedirectsToCallbackWithoutUrlToken() throws Exception {
        var refreshProperties = new RefreshTokenProperties(
                1209600,
                "local",
                new RefreshTokenProperties.Cookie("JIBER_REFRESH_TOKEN", "/api/v1/auth", "Lax", false)
        );
        var handler = new OAuth2LoginSuccessHandler(
                new OAuth2ProviderUserResolver(),
                LocalOAuth2UserProvisioningService.forTesting(new RecordingAuthUserMapper(), new RecordingSocialAccountMapper(), FIXED_CLOCK),
                RefreshTokenService.forTesting(refreshProperties, new RecordingRefreshSessionMapper(), new SecureRandom(new byte[]{9, 10, 11, 12}), FIXED_CLOCK),
                new RefreshTokenCookieService(refreshProperties),
                new FrontendProperties("http://localhost:5173")
        );
        var oauthUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("sub", "google-1", "email", "user@example.com", "name", "사용자"),
                "sub"
        );
        var authentication = new OAuth2AuthenticationToken(oauthUser, oauthUser.getAuthorities(), "google");
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/login/callback");
        assertThat(response.getRedirectedUrl()).doesNotContain("token");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("JIBER_REFRESH_TOKEN=")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    private static class RecordingAuthUserMapper implements AuthUserMapper {

        @Override
        public AuthUserRecord findById(Long userId) {
            return null;
        }

        @Override
        public AuthUserRecord findByEmail(String email) {
            return null;
        }

        @Override
        public int insertEmailUser(String email, String passwordHash, String displayName, String role, Boolean enabled, OffsetDateTime lastLoginAt) {
            return 0;
        }

        @Override
        public int updateLastLoginAt(Long userId, OffsetDateTime lastLoginAt) {
            return 1;
        }
    }

    private static class RecordingSocialAccountMapper implements SocialAccountMapper {

        @Override
        public int insert(SocialAccountInsertCommand command) {
            return 0;
        }

        @Override
        public SocialAccountRecord findByProvider(String oauthProvider, String providerUserId) {
            return null;
        }

        @Override
        public AuthUserRecord findLinkedUserByProvider(String oauthProvider, String providerUserId) {
            return new AuthUserRecord(1L, "user@example.com", "$2a$10$testhashvaluefor mapper storage only", "사용자", "USER", true, null, null, null);
        }

        @Override
        public java.util.List<SocialAccountRecord> findByUserId(Long userId) {
            return java.util.List.of();
        }

        @Override
        public int updateLastLoginAt(String oauthProvider, String providerUserId, OffsetDateTime lastLoginAt) {
            return 1;
        }
    }

    private static class RecordingRefreshSessionMapper implements RefreshSessionMapper {

        @Override
        public int insert(RefreshSessionInsertCommand command) {
            return 1;
        }

        @Override
        public RefreshSessionRecord findByTokenHash(String refreshTokenHash) {
            return null;
        }

        @Override
        public RefreshSessionRecord findActiveByTokenHash(String refreshTokenHash, OffsetDateTime now) {
            return null;
        }

        @Override
        public int revokeBySessionId(Long refreshSessionId, OffsetDateTime revokedAt) {
            return 0;
        }

        @Override
        public int revokeByTokenHash(String refreshTokenHash, OffsetDateTime revokedAt) {
            return 0;
        }

        @Override
        public int revokeSessionFamily(Long refreshSessionId, OffsetDateTime revokedAt) {
            return 0;
        }
    }
}
