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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SocialLoginServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T07:00:00Z"), ZoneOffset.UTC);
    private static final String CREDENTIAL = "valid-credential-1";

    @Test
    void pendingPreviewReturnsSafeProviderHintsAndMatchingEmailFlag() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "social@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-user-1", " Social@Example.COM ", "소셜 사용자"));

        var response = fixture.socialLoginService.pending(issued.token());

        assertThat(response.provider()).isEqualTo("NAVER");
        assertThat(response.email()).isEqualTo("social@example.com");
        assertThat(response.displayName()).isEqualTo("소셜 사용자");
        assertThat(response.matchingEmailAccountExists()).isTrue();
    }

    @Test
    void missingOrExpiredPendingCookieReturnsSocialPendingNotFound() {
        var fixture = new Fixture();
        fixture.pendingSocialSessionMapper.insert(new PendingSocialSessionInsertCommand(
                "c".repeat(64),
                "NAVER",
                "expired-social-user",
                "expired@example.com",
                "만료 사용자",
                "expired@example.com",
                OffsetDateTime.now(FIXED_CLOCK).minusSeconds(1)
        ));

        assertThatThrownBy(() -> fixture.socialLoginService.pending(null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_PENDING_NOT_FOUND);
        assertThatThrownBy(() -> fixture.socialLoginService.pending("expired-cookie-token"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_PENDING_NOT_FOUND);
    }

    @Test
    void socialSignupCreatesUserLinksProviderConsumesPendingAndStartsSession() {
        var fixture = new Fixture();
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-user-2", "provider@example.com", "제공자 이름"));

        var result = fixture.socialLoginService.socialSignup(
                issued.token(),
                new SocialSignupRequest(" USER@Example.COM ", CREDENTIAL, " 소셜 가입자 "),
                RefreshRequestContext.empty()
        );

        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().user().email()).isEqualTo("user@example.com");
        assertThat(result.response().user().displayName()).isEqualTo("소셜 가입자");
        assertThat(result.response().user().roles()).containsExactly("USER");
        assertThat(result.response().user().roles()).doesNotContain("ADMIN");
        assertThat(result.refreshToken()).isNotBlank();

        var user = fixture.authUserMapper.findByEmail("user@example.com");
        assertThat(user).isNotNull();
        assertThat(user.passwordHash()).startsWith("$2");
        assertThat(user.passwordHash()).isNotEqualTo(CREDENTIAL);

        var socialAccount = fixture.socialAccountMapper.findByProvider("NAVER", "social-user-2");
        assertThat(socialAccount).isNotNull();
        assertThat(socialAccount.userId()).isEqualTo(user.userId());
        assertThat(socialAccount.providerEmail()).isEqualTo("provider@example.com");
        assertThat(socialAccount.providerDisplayName()).isEqualTo("제공자 이름");

        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).hasSize(64);
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isEqualTo(fixture.pendingSocialSessionService.hash(issued.token()));
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isNotEqualTo(issued.token());
        assertThat(fixture.refreshSessionMapper.insertedTokenHash).hasSize(64);
    }

    @Test
    void duplicateEmailDoesNotAutoLinkOrConsumePending() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "user@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-user-3", "user@example.com", "소셜 사용자"));

        assertThatThrownBy(() -> fixture.socialLoginService.socialSignup(
                issued.token(),
                new SocialSignupRequest(" USER@example.com ", CREDENTIAL, "소셜 가입자"),
                RefreshRequestContext.empty()
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        assertThat(fixture.socialAccountMapper.findByProvider("NAVER", "social-user-3")).isNull();
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isNull();
    }

    @Test
    void providerSubjectConflictReturnsSafeConflictWithoutCreatingAdmin() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "linked@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "연결 사용자", true);
        fixture.socialAccountMapper.insert(new SocialAccountInsertCommand(
                1L,
                "NAVER",
                "social-user-4",
                "linked-provider@example.com",
                "연결 제공자",
                OffsetDateTime.now(FIXED_CLOCK),
                OffsetDateTime.now(FIXED_CLOCK)
        ));
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-user-4", "new@example.com", "새 사용자"));

        assertThatThrownBy(() -> fixture.socialLoginService.socialSignup(
                issued.token(),
                new SocialSignupRequest("new@example.com", CREDENTIAL, "새 사용자"),
                RefreshRequestContext.empty()
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

        assertThat(fixture.authUserMapper.findByEmail("new@example.com")).isNull();
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isNull();
    }

    @Test
    void socialLinkAuthenticatesExistingUserLinksProviderConsumesPendingAndStartsSession() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "owner@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-link-user-1", "provider@example.com", "제공자 이름"));

        var result = fixture.socialLoginService.socialLink(
                issued.token(),
                new SocialLinkRequest(" OWNER@example.com ", CREDENTIAL),
                RefreshRequestContext.empty()
        );

        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().user().email()).isEqualTo("owner@example.com");
        assertThat(result.response().user().roles()).containsExactly("USER");
        assertThat(result.response().user().roles()).doesNotContain("ADMIN");
        assertThat(result.refreshToken()).isNotBlank();

        var socialAccount = fixture.socialAccountMapper.findByProvider("NAVER", "social-link-user-1");
        assertThat(socialAccount).isNotNull();
        assertThat(socialAccount.userId()).isEqualTo(1L);
        assertThat(socialAccount.providerEmail()).isEqualTo("provider@example.com");
        assertThat(socialAccount.providerDisplayName()).isEqualTo("제공자 이름");
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isEqualTo(fixture.pendingSocialSessionService.hash(issued.token()));
        assertThat(fixture.refreshSessionMapper.insertedTokenHash).hasSize(64);
    }

    @Test
    void socialLinkWrongPasswordReturnsInvalidCredentialsAndDoesNotConsumePending() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "owner@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-link-user-2", "owner@example.com", "제공자 이름"));

        assertThatThrownBy(() -> fixture.socialLoginService.socialLink(
                issued.token(),
                new SocialLinkRequest("owner@example.com", "invalid-credential-1"),
                RefreshRequestContext.empty()
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        assertThat(fixture.socialAccountMapper.findByProvider("NAVER", "social-link-user-2")).isNull();
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isNull();
    }

    @Test
    void socialLinkRequiresActivePendingSession() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "owner@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);

        assertThatThrownBy(() -> fixture.socialLoginService.socialLink(
                null,
                new SocialLinkRequest("owner@example.com", CREDENTIAL),
                RefreshRequestContext.empty()
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_PENDING_NOT_FOUND);
    }

    @Test
    void socialLinkProviderSubjectAlreadyLinkedReturnsSafeConflict() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "owner@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);
        fixture.authUserMapper.insertExistingUser(2L, "other@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "다른 사용자", true);
        fixture.socialAccountMapper.insert(new SocialAccountInsertCommand(
                2L,
                "NAVER",
                "social-link-user-3",
                "other-provider@example.com",
                "다른 제공자",
                OffsetDateTime.now(FIXED_CLOCK),
                OffsetDateTime.now(FIXED_CLOCK)
        ));
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-link-user-3", "provider@example.com", "제공자 이름"));

        assertThatThrownBy(() -> fixture.socialLoginService.socialLink(
                issued.token(),
                new SocialLinkRequest("owner@example.com", CREDENTIAL),
                RefreshRequestContext.empty()
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isNull();
    }

    @Test
    void socialLinkSameUserAlreadyHasSameProviderReturnsSafeConflict() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "owner@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);
        fixture.socialAccountMapper.insert(new SocialAccountInsertCommand(
                1L,
                "NAVER",
                "existing-provider-subject",
                "existing-provider@example.com",
                "기존 제공자",
                OffsetDateTime.now(FIXED_CLOCK),
                OffsetDateTime.now(FIXED_CLOCK)
        ));
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-link-user-4", "provider@example.com", "제공자 이름"));

        assertThatThrownBy(() -> fixture.socialLoginService.socialLink(
                issued.token(),
                new SocialLinkRequest("owner@example.com", CREDENTIAL),
                RefreshRequestContext.empty()
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

        assertThat(fixture.socialAccountMapper.findByProvider("NAVER", "social-link-user-4")).isNull();
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isNull();
    }

    @Test
    void matchingEmailDoesNotLinkBeforePasswordAuthentication() {
        var fixture = new Fixture();
        fixture.authUserMapper.insertExistingUser(1L, "owner@example.com", fixture.passwordEncoder.encode(CREDENTIAL), "기존 사용자", true);
        var issued = fixture.pendingSocialSessionService.issue(providerUser("social-link-user-5", "owner@example.com", "제공자 이름"));

        assertThatThrownBy(() -> fixture.socialLoginService.socialLink(
                issued.token(),
                new SocialLinkRequest("owner@example.com", "invalid-credential-1"),
                RefreshRequestContext.empty()
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        assertThat(fixture.socialAccountMapper.findByProvider("NAVER", "social-link-user-5")).isNull();
        assertThat(fixture.pendingSocialSessionMapper.consumedTokenHash).isNull();
    }

    private OAuth2ProviderUser providerUser(String providerUserId, String email, String displayName) {
        return new OAuth2ProviderUser(OAuth2Provider.NAVER, providerUserId, email, displayName);
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
        private final RecordingAuthUserMapper authUserMapper = new RecordingAuthUserMapper();
        private final RecordingSocialAccountMapper socialAccountMapper = new RecordingSocialAccountMapper();
        private final RecordingPendingSocialSessionMapper pendingSocialSessionMapper = new RecordingPendingSocialSessionMapper();
        private final RecordingRefreshSessionMapper refreshSessionMapper = new RecordingRefreshSessionMapper();
        private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        private final PendingSocialSessionService pendingSocialSessionService = PendingSocialSessionService.forTesting(
                pendingProperties,
                pendingSocialSessionMapper,
                new SecureRandom(new byte[]{1, 2, 3, 4}),
                FIXED_CLOCK,
                new EmailNormalizer()
        );
        private final SocialLoginService socialLoginService = SocialLoginService.forTesting(
                JwtTokenService.forTesting(
                        new JwtTokenProperties("jiber-test", "test-secret-with-enough-entropy-for-hmac", 900, "test"),
                        new ObjectMapper(),
                        FIXED_CLOCK
                ),
                RefreshTokenService.forTesting(
                        refreshProperties,
                        refreshSessionMapper,
                        new SecureRandom(new byte[]{5, 6, 7, 8}),
                        FIXED_CLOCK
                ),
                authUserMapper,
                socialAccountMapper,
                pendingSocialSessionService,
                passwordEncoder,
                new EmailNormalizer(),
                new PasswordPolicy(),
                FIXED_CLOCK
        );
    }

    private static class RecordingAuthUserMapper implements AuthUserMapper {

        private final Map<String, AuthUserRecord> usersByEmail = new LinkedHashMap<>();
        private final Map<Long, AuthUserRecord> usersById = new LinkedHashMap<>();
        private long nextUserId = 1L;

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
                throw new DuplicateKeyException("duplicate email");
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
            return 1;
        }

        void insertExistingUser(Long userId, String email, String passwordHash, String displayName, Boolean enabled) {
            var now = OffsetDateTime.now(FIXED_CLOCK);
            var user = new AuthUserRecord(userId, email, passwordHash, displayName, "USER", enabled, now, now, now);
            usersByEmail.put(email, user);
            usersById.put(userId, user);
            nextUserId = Math.max(nextUserId, userId + 1);
        }
    }

    private static class RecordingSocialAccountMapper implements SocialAccountMapper {

        private final Map<String, SocialAccountRecord> accountsByProvider = new LinkedHashMap<>();

        @Override
        public int insert(SocialAccountInsertCommand command) {
            var key = key(command.oauthProvider(), command.providerUserId());
            if (accountsByProvider.containsKey(key)) {
                throw new DuplicateKeyException("duplicate social account");
            }
            accountsByProvider.put(key, new SocialAccountRecord(
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
            ));
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
        public List<SocialAccountRecord> findByUserId(Long userId) {
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

    private static class RecordingRefreshSessionMapper implements RefreshSessionMapper {

        private String insertedTokenHash;

        @Override
        public int insert(RefreshSessionInsertCommand command) {
            insertedTokenHash = command.refreshTokenHash();
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
