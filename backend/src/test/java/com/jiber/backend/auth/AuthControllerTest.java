package com.jiber.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T07:00:00Z"), ZoneOffset.UTC);
    private static final String CREDENTIAL = "valid-credential-1";

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

    @Test
    void signupCreatesUserSetsRefreshCookieAndReturnsAccessToken() {
        var fixture = new Fixture();
        var controller = fixture.controller();
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        var response = new MockHttpServletResponse();

        var body = controller.signup(
                new EmailSignupRequest(" USER@Example.COM ", CREDENTIAL, " 사용자 "),
                request,
                response
        );

        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.user().email()).isEqualTo("user@example.com");
        assertThat(body.user().displayName()).isEqualTo("사용자");
        assertThat(body.user().roles()).containsExactly("USER");
        assertThat(body.user().roles()).doesNotContain("ADMIN");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("JIBER_REFRESH_TOKEN=")
                .contains("HttpOnly")
                .contains("SameSite=Lax");

        var stored = fixture.authUserMapper.findByEmail("user@example.com");
        assertThat(stored).isNotNull();
        assertThat(stored.passwordHash()).isNotBlank();
        assertThat(stored.passwordHash()).isNotEqualTo(CREDENTIAL);
        assertThat(stored.passwordHash()).startsWith("$2");
        assertThat(stored.role()).isEqualTo("USER");
    }

    @Test
    void signupDuplicateNormalizedEmailReturnsEmailAlreadyExists() {
        var fixture = new Fixture();
        var controller = fixture.controller();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        controller.signup(new EmailSignupRequest("user@example.com", CREDENTIAL, "사용자"), request, response);

        assertThatThrownBy(() ->
                controller.signup(new EmailSignupRequest(" USER@example.com ", CREDENTIAL, "다른 사용자"), request, new MockHttpServletResponse())
        )
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void loginWithNormalizedEmailSetsRefreshCookieAndReturnsAccessToken() {
        var fixture = new Fixture();
        var controller = fixture.controller();
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        controller.signup(new EmailSignupRequest("user@example.com", CREDENTIAL, "사용자"), request, new MockHttpServletResponse());
        var response = new MockHttpServletResponse();

        var body = controller.login(new EmailLoginRequest(" USER@example.com ", CREDENTIAL), request, response);

        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.user().email()).isEqualTo("user@example.com");
        assertThat(body.user().roles()).containsExactly("USER");
        assertThat(body.user().roles()).doesNotContain("ADMIN");
        assertThat(response.getHeader("Set-Cookie")).contains("JIBER_REFRESH_TOKEN=");
        assertThat(fixture.authUserMapper.lastLoginUpdatedUserId).isEqualTo(body.user().userId());
    }

    @Test
    void loginFailureCasesReturnSameInvalidCredentialsError() {
        var fixture = new Fixture();
        var controller = fixture.controller();
        var request = new MockHttpServletRequest();
        controller.signup(new EmailSignupRequest("user@example.com", CREDENTIAL, "사용자"), request, new MockHttpServletResponse());

        var wrongEmail = catchApiException(() ->
                controller.login(new EmailLoginRequest("missing@example.com", CREDENTIAL), request, new MockHttpServletResponse())
        );
        var wrongCredential = catchApiException(() ->
                controller.login(new EmailLoginRequest("user@example.com", "invalid-credential-1"), request, new MockHttpServletResponse())
        );

        assertThat(wrongEmail.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrongCredential.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrongEmail.getMessage()).isEqualTo(wrongCredential.getMessage());
    }

    @Test
    void disabledUserLoginReturnsInvalidCredentials() {
        var fixture = new Fixture();
        var controller = fixture.controller();
        fixture.authUserMapper.insertDisabledUser("disabled@example.com", fixture.passwordEncoder.encode(CREDENTIAL));

        var failure = catchApiException(() ->
                controller.login(new EmailLoginRequest("disabled@example.com", CREDENTIAL), new MockHttpServletRequest(), new MockHttpServletResponse())
        );

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void socialPendingReturnsSafePreviewFromPendingCookie() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(
                20L,
                "social@example.com",
                fixture.passwordEncoder.encode(CREDENTIAL),
                "기존 사용자",
                true
        );
        var issued = fixture.pendingSocialSessionService.issue(
                new OAuth2ProviderUser(OAuth2Provider.NAVER, "naver-user-1", " Social@Example.COM ", "네이버 사용자")
        );

        var body = fixture.controller().socialPending(issued.token());

        assertThat(body.provider()).isEqualTo("NAVER");
        assertThat(body.email()).isEqualTo("social@example.com");
        assertThat(body.displayName()).isEqualTo("네이버 사용자");
        assertThat(body.matchingEmailAccountExists()).isTrue();
    }

    @Test
    void socialPendingMissingCookieReturnsSocialPendingNotFound() {
        var fixture = new Fixture();

        var failure = catchApiException(() -> fixture.controller().socialPending(null));

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_PENDING_NOT_FOUND);
    }

    @Test
    void socialSignupSetsRefreshCookieClearsPendingCookieAndReturnsAccessToken() {
        var fixture = new Fixture();
        var issued = fixture.pendingSocialSessionService.issue(
                new OAuth2ProviderUser(OAuth2Provider.NAVER, "naver-user-2", "provider@example.com", "제공자 이름")
        );
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        var response = new MockHttpServletResponse();

        var body = fixture.controller().socialSignup(
                issued.token(),
                new SocialSignupRequest(" USER@Example.COM ", CREDENTIAL, " 소셜 가입자 "),
                request,
                response
        );

        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.user().email()).isEqualTo("user@example.com");
        assertThat(body.user().roles()).containsExactly("USER");
        assertThat(body.user().roles()).doesNotContain("ADMIN");
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).contains("JIBER_REFRESH_TOKEN=").contains("HttpOnly"))
                .anySatisfy(cookie -> assertThat(cookie).contains("JIBER_PENDING_SOCIAL=").contains("Max-Age=0").contains("HttpOnly"));
        assertThat(fixture.socialAccountMapper.findByProvider("NAVER", "naver-user-2")).isNotNull();
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isEqualTo(fixture.pendingSocialSessionService.hash(issued.token()));
    }

    @Test
    void socialLinkSetsRefreshCookieClearsPendingCookieAndReturnsAccessToken() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(
                20L,
                "owner@example.com",
                fixture.passwordEncoder.encode(CREDENTIAL),
                "기존 사용자",
                true
        );
        var issued = fixture.pendingSocialSessionService.issue(
                new OAuth2ProviderUser(OAuth2Provider.NAVER, "naver-user-3", "provider@example.com", "제공자 이름")
        );
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        var response = new MockHttpServletResponse();

        var body = fixture.controller().socialLink(
                issued.token(),
                new SocialLinkRequest(" OWNER@example.com ", CREDENTIAL),
                request,
                response
        );

        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.user().email()).isEqualTo("owner@example.com");
        assertThat(body.user().roles()).containsExactly("USER");
        assertThat(body.user().roles()).doesNotContain("ADMIN");
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).contains("JIBER_REFRESH_TOKEN=").contains("HttpOnly"))
                .anySatisfy(cookie -> assertThat(cookie).contains("JIBER_PENDING_SOCIAL=").contains("Max-Age=0").contains("HttpOnly"));
        assertThat(fixture.socialAccountMapper.findByProvider("NAVER", "naver-user-3").userId()).isEqualTo(20L);
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isEqualTo(fixture.pendingSocialSessionService.hash(issued.token()));
    }

    private ApiException catchApiException(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (ApiException exception) {
            return exception;
        }
        throw new AssertionError("Expected ApiException");
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static class Fixture {

        private final RefreshTokenProperties refreshProperties = new RefreshTokenProperties(
                1209600,
                "local",
                new RefreshTokenProperties.Cookie("JIBER_REFRESH_TOKEN", "/api/v1/auth", "Lax", false)
        );
        private final PendingSocialProperties pendingProperties = new PendingSocialProperties(
                600,
                "local",
                new PendingSocialProperties.Cookie("JIBER_PENDING_SOCIAL", "/api/v1/auth", "Lax", false)
        );
        private final RecordingRefreshSessionMapper refreshSessionMapper = new RecordingRefreshSessionMapper();
        private final RecordingAuthUserMapper authUserMapper = new RecordingAuthUserMapper();
        private final RecordingSocialAccountMapper socialAccountMapper = new RecordingSocialAccountMapper();
        private final RecordingPendingSocialSessionMapper pendingSocialSessionMapper = new RecordingPendingSocialSessionMapper();
        private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        private final RefreshTokenService refreshTokenService = RefreshTokenService.forTesting(
                refreshProperties,
                refreshSessionMapper,
                new SecureRandom(new byte[]{5, 6, 7, 8}),
                FIXED_CLOCK
        );
        private final PendingSocialSessionService pendingSocialSessionService = PendingSocialSessionService.forTesting(
                pendingProperties,
                pendingSocialSessionMapper,
                new SecureRandom(new byte[]{1, 2, 3, 4}),
                FIXED_CLOCK,
                new EmailNormalizer()
        );

        private Fixture() {
            authUserMapper.insertExistingUser(
                    1L,
                    "existing@example.com",
                    passwordEncoder.encode(CREDENTIAL),
                    "기존 사용자",
                    true
            );
        }

        private AuthController controller() {
            var jwtTokenService = JwtTokenService.forTesting(
                    new JwtTokenProperties("jiber-test", "test-secret-with-enough-entropy-for-hmac", 900, "test"),
                    new ObjectMapper(),
                    FIXED_CLOCK
            );
            var authService = AuthService.forTesting(
                    jwtTokenService,
                    refreshTokenService,
                    authUserMapper,
                    passwordEncoder,
                    new EmailNormalizer(),
                    new PasswordPolicy(),
                    FIXED_CLOCK
            );
            var socialLoginService = SocialLoginService.forTesting(
                    jwtTokenService,
                    refreshTokenService,
                    authUserMapper,
                    socialAccountMapper,
                    pendingSocialSessionService,
                    passwordEncoder,
                    new EmailNormalizer(),
                    new PasswordPolicy(),
                    FIXED_CLOCK
            );
            return new AuthController(
                    authService,
                    socialLoginService,
                    new RefreshTokenCookieService(refreshProperties),
                    new PendingSocialCookieService(pendingProperties)
            );
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

        private final Map<String, AuthUserRecord> usersByEmail = new LinkedHashMap<>();
        private final Map<Long, AuthUserRecord> usersById = new LinkedHashMap<>();
        private long nextUserId = 1L;
        private Long lastLoginUpdatedUserId;

        @Override
        public AuthUserRecord findById(Long userId) {
            return usersById.get(userId);
        }

        @Override
        public AuthUserRecord findByEmail(String email) {
            return usersByEmail.get(email);
        }

        @Override
        public int insertEmailUser(String email, String passwordHash, String displayName, String role, Boolean enabled, OffsetDateTime lastLoginAt) {
            if (usersByEmail.containsKey(email)) {
                throw new org.springframework.dao.DuplicateKeyException("duplicate email");
            }
            var user = new AuthUserRecord(nextUserId++, email, passwordHash, displayName, role, enabled, lastLoginAt, lastLoginAt, lastLoginAt);
            usersByEmail.put(email, user);
            usersById.put(user.userId(), user);
            return 1;
        }

        @Override
        public int updateLastLoginAt(Long userId, OffsetDateTime lastLoginAt) {
            var current = usersById.get(userId);
            if (current == null) {
                return 0;
            }
            var updated = new AuthUserRecord(
                    current.userId(),
                    current.email(),
                    current.passwordHash(),
                    current.displayName(),
                    current.role(),
                    current.enabled(),
                    lastLoginAt,
                    current.createdAt(),
                    lastLoginAt
            );
            usersById.put(userId, updated);
            usersByEmail.put(updated.email(), updated);
            lastLoginUpdatedUserId = userId;
            return 1;
        }

        void insertDisabledUser(String email, String passwordHash) {
            var now = OffsetDateTime.now(FIXED_CLOCK);
            var user = new AuthUserRecord(nextUserId++, email, passwordHash, "비활성 사용자", "USER", false, now, now, now);
            usersByEmail.put(email, user);
            usersById.put(user.userId(), user);
        }

        void insertExistingUser(Long userId, String email, String passwordHash, String displayName, Boolean enabled) {
            var now = OffsetDateTime.now(FIXED_CLOCK);
            var user = new AuthUserRecord(userId, email, passwordHash, displayName, "USER", enabled, now, now, now);
            usersByEmail.put(email, user);
            usersById.put(userId, user);
            nextUserId = Math.max(nextUserId, userId + 1);
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

    private static class RecordingSocialAccountMapper implements SocialAccountMapper {

        private final Map<String, SocialAccountRecord> accountsByProvider = new LinkedHashMap<>();

        @Override
        public int insert(SocialAccountInsertCommand command) {
            accountsByProvider.put(
                    key(command.oauthProvider(), command.providerUserId()),
                    new SocialAccountRecord(
                            (long) accountsByProvider.size() + 1,
                            command.userId(),
                            command.oauthProvider(),
                            command.providerUserId(),
                            command.providerEmail(),
                            command.providerDisplayName(),
                            command.linkedAt(),
                            command.lastLoginAt(),
                            command.linkedAt(),
                            command.linkedAt()
                    )
            );
            return 1;
        }

        @Override
        public SocialAccountRecord findByProvider(String oauthProvider, String providerUserId) {
            return accountsByProvider.get(key(oauthProvider, providerUserId));
        }

        @Override
        public AuthUserRecord findLinkedUserByProvider(String oauthProvider, String providerUserId) {
            return null;
        }

        @Override
        public java.util.List<SocialAccountRecord> findByUserId(Long userId) {
            return accountsByProvider.values().stream()
                    .filter(account -> account.userId().equals(userId))
                    .toList();
        }

        @Override
        public int updateLastLoginAt(String oauthProvider, String providerUserId, OffsetDateTime lastLoginAt) {
            return accountsByProvider.containsKey(key(oauthProvider, providerUserId)) ? 1 : 0;
        }

        private String key(String oauthProvider, String providerUserId) {
            return oauthProvider + ":" + providerUserId;
        }
    }

    private static class RecordingPendingSocialSessionMapper implements PendingSocialSessionMapper {

        private final Map<String, PendingSocialSessionRecord> sessionsByHash = new LinkedHashMap<>();
        private String consumedTokenHash;

        @Override
        public int insert(PendingSocialSessionInsertCommand command) {
            sessionsByHash.put(command.pendingTokenHash(), new PendingSocialSessionRecord(
                    (long) sessionsByHash.size() + 1,
                    command.pendingTokenHash(),
                    command.oauthProvider(),
                    command.providerUserId(),
                    command.providerEmail(),
                    command.providerDisplayName(),
                    command.suggestedEmail(),
                    command.expiresAt(),
                    null,
                    OffsetDateTime.now(FIXED_CLOCK),
                    OffsetDateTime.now(FIXED_CLOCK)
            ));
            return 1;
        }

        @Override
        public PendingSocialSessionRecord findByTokenHash(String pendingTokenHash) {
            return sessionsByHash.get(pendingTokenHash);
        }

        @Override
        public PendingSocialSessionRecord findActiveByTokenHash(String pendingTokenHash, OffsetDateTime now) {
            var session = sessionsByHash.get(pendingTokenHash);
            return session != null && session.activeAt(now) ? session : null;
        }

        @Override
        public int consume(String pendingTokenHash, OffsetDateTime consumedAt) {
            var session = sessionsByHash.get(pendingTokenHash);
            if (session == null || session.consumedAt() != null) {
                return 0;
            }
            consumedTokenHash = pendingTokenHash;
            sessionsByHash.put(pendingTokenHash, new PendingSocialSessionRecord(
                    session.pendingSocialSessionId(),
                    session.pendingTokenHash(),
                    session.oauthProvider(),
                    session.providerUserId(),
                    session.providerEmail(),
                    session.providerDisplayName(),
                    session.suggestedEmail(),
                    session.expiresAt(),
                    consumedAt,
                    session.createdAt(),
                    consumedAt
            ));
            return 1;
        }
    }
}
