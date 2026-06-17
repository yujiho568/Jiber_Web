package com.jiber.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T07:00:00Z"), ZoneOffset.UTC);

    @Test
    void refreshRotatesCookieAndReturnsAccessTokenWithoutRefreshTokenInBody() {
        var fixture = new Fixture();
        fixture.refreshSessionMapper.byTokenHash = fixture.session(10L, fixture.refreshTokenService.hash("old-refresh-token"));
        var controller = fixture.controller();
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        var response = new MockHttpServletResponse();

        var body = controller.refresh("old-refresh-token", request, response);

        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(900);
        assertThat(body.user().roles()).containsExactly("USER");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("JIBER_REFRESH_TOKEN=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("old-refresh-token");
    }

    @Test
    void logoutRevokesHashedRefreshTokenAndClearsCookie() {
        var fixture = new Fixture();
        var controller = fixture.controller();
        var response = new MockHttpServletResponse();

        var body = controller.logout("raw-refresh-token", null, response);

        assertThat(body.message()).isEqualTo("로그아웃되었습니다.");
        assertThat(fixture.refreshSessionMapper.revokedTokenHash).hasSize(64);
        assertThat(fixture.refreshSessionMapper.revokedTokenHash).isNotEqualTo("raw-refresh-token");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("JIBER_REFRESH_TOKEN=")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    private static class Fixture {

        private final RefreshTokenProperties refreshProperties = new RefreshTokenProperties(
                1209600,
                "local",
                new RefreshTokenProperties.Cookie("JIBER_REFRESH_TOKEN", "/api/v1/auth", "Lax", false)
        );
        private final RecordingRefreshSessionMapper refreshSessionMapper = new RecordingRefreshSessionMapper();
        private final RefreshTokenService refreshTokenService = RefreshTokenService.forTesting(
                refreshProperties,
                refreshSessionMapper,
                new SecureRandom(new byte[]{5, 6, 7, 8}),
                FIXED_CLOCK
        );

        private AuthController controller() {
            var jwtTokenService = JwtTokenService.forTesting(
                    new JwtTokenProperties("jiber-test", "test-secret-with-enough-entropy-for-hmac", 900, "test"),
                    new ObjectMapper(),
                    FIXED_CLOCK
            );
            var authService = new AuthService(jwtTokenService, refreshTokenService, new RecordingAuthUserMapper());
            return new AuthController(authService, new RefreshTokenCookieService(refreshProperties));
        }

        private RefreshSessionRecord session(Long sessionId, String tokenHash) {
            return new RefreshSessionRecord(
                    sessionId,
                    1L,
                    tokenHash,
                    null,
                    "JUnit",
                    null,
                    OffsetDateTime.parse("2026-06-29T07:00:00Z"),
                    null,
                    OffsetDateTime.parse("2026-06-15T07:00:00Z"),
                    OffsetDateTime.parse("2026-06-15T07:00:00Z")
            );
        }
    }

    private static class RecordingAuthUserMapper implements AuthUserMapper {

        @Override
        public AuthUserRecord findById(Long userId) {
            return new AuthUserRecord(userId, "user@example.com", "$2a$10$testhashvaluefor mapper storage only", "사용자", "USER", true, null, null, null);
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
            return 0;
        }
    }

    private static class RecordingRefreshSessionMapper implements RefreshSessionMapper {

        private RefreshSessionRecord byTokenHash;
        private String revokedTokenHash;

        @Override
        public int insert(RefreshSessionInsertCommand command) {
            return 1;
        }

        @Override
        public RefreshSessionRecord findByTokenHash(String refreshTokenHash) {
            return byTokenHash;
        }

        @Override
        public RefreshSessionRecord findActiveByTokenHash(String refreshTokenHash, OffsetDateTime now) {
            return byTokenHash != null && byTokenHash.activeAt(now) ? byTokenHash : null;
        }

        @Override
        public int revokeBySessionId(Long refreshSessionId, OffsetDateTime revokedAt) {
            return 1;
        }

        @Override
        public int revokeByTokenHash(String refreshTokenHash, OffsetDateTime revokedAt) {
            this.revokedTokenHash = refreshTokenHash;
            return 1;
        }

        @Override
        public int revokeSessionFamily(Long refreshSessionId, OffsetDateTime revokedAt) {
            return 1;
        }
    }
}
