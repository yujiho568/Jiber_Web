package com.jiber.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiber.backend.auth.AuthController;
import com.jiber.backend.auth.AuthService;
import com.jiber.backend.auth.AuthUserMapper;
import com.jiber.backend.auth.AuthUserRecord;
import com.jiber.backend.auth.EmailNormalizer;
import com.jiber.backend.auth.JwtAuthenticationFilter;
import com.jiber.backend.auth.JwtTokenProperties;
import com.jiber.backend.auth.JwtTokenService;
import com.jiber.backend.auth.PasswordPolicy;
import com.jiber.backend.auth.PendingSocialCookieService;
import com.jiber.backend.auth.PendingSocialProperties;
import com.jiber.backend.auth.PendingSocialSessionInsertCommand;
import com.jiber.backend.auth.PendingSocialSessionMapper;
import com.jiber.backend.auth.PendingSocialSessionRecord;
import com.jiber.backend.auth.RefreshSessionInsertCommand;
import com.jiber.backend.auth.RefreshSessionMapper;
import com.jiber.backend.auth.RefreshSessionRecord;
import com.jiber.backend.auth.RefreshTokenCookieService;
import com.jiber.backend.auth.RefreshTokenProperties;
import com.jiber.backend.auth.RefreshTokenService;
import com.jiber.backend.auth.SocialAccountInsertCommand;
import com.jiber.backend.auth.SocialAccountMapper;
import com.jiber.backend.auth.SocialAccountRecord;
import com.jiber.backend.auth.SocialLoginService;
import com.jiber.backend.favorite.FavoriteController;
import com.jiber.backend.favorite.FavoriteService;
import com.jiber.backend.notice.AdminNoticeController;
import com.jiber.backend.notice.NoticeService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        AuthController.class,
        FavoriteController.class,
        AdminNoticeController.class
})
@Import({
        SecurityConfig.class,
        SecurityErrorResponseWriter.class,
        JwtAuthenticationFilter.class,
        SecurityRulesTest.TestBeans.class
})
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserMapper authUserMapper;

    @Test
    void getMeAllowsAnonymousAndReturnsUnauthenticatedBody() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").value(Matchers.nullValue()));
    }

    @Test
    void signupAndLoginAllowAnonymous() throws Exception {
        var signupBody = """
                {
                  "email": "security-signup@example.com",
                  "password": "valid-credential-1",
                  "displayName": "보안 테스트"
                }
                """;
        var loginBody = """
                {
                  "email": " SECURITY-SIGNUP@example.com ",
                  "password": "valid-credential-1"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("USER"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("security-signup@example.com"));
    }

    @Test
    void signupNormalizesWhitespaceEmailBeforeValidation() throws Exception {
        var body = """
                {
                  "email": " USER@Example.COM ",
                  "password": "valid-credential-1",
                  "displayName": "보안 테스트"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", Matchers.containsString("JIBER_REFRESH_TOKEN=")))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"));

        var stored = authUserMapper.findByEmail("user@example.com");
        assertThat(stored).isNotNull();
        assertThat(stored.role()).isEqualTo("USER");
    }

    @Test
    void duplicateSignupWithWhitespaceEmailReturnsEmailAlreadyExists() throws Exception {
        var firstBody = """
                {
                  "email": "duplicate@example.com",
                  "password": "valid-credential-1",
                  "displayName": "보안 테스트"
                }
                """;
        var duplicateBody = """
                {
                  "email": " DUPLICATE@example.com ",
                  "password": "valid-credential-1",
                  "displayName": "다른 사용자"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.code").value(Matchers.not("VALIDATION_FAILED")));
    }

    @Test
    void invalidSignupEmailStillReturnsValidationFailed() throws Exception {
        var body = """
                {
                  "email": "not-an-email",
                  "password": "valid-credential-1",
                  "displayName": "보안 테스트"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("email"));
    }

    @Test
    void socialEndpointsAllowAnonymousButRequirePendingCookie() throws Exception {
        var body = """
                {
                  "email": "social@example.com",
                  "password": "valid-credential-1",
                  "displayName": "소셜 가입자"
                }
                """;

        mockMvc.perform(get("/api/v1/auth/social/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_PENDING_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/auth/social/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_PENDING_NOT_FOUND"));

        var linkBody = """
                {
                  "email": "social@example.com",
                  "password": "valid-credential-1"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/social/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_PENDING_NOT_FOUND"));
    }

    @Test
    void favoritesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/favorites/apartments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void userCanAccessFavorites() throws Exception {
        mockMvc.perform(get("/api/v1/favorites/apartments")
                        .with(user("1").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void adminNoticeMutationRequiresAdminRole() throws Exception {
        var body = """
                {
                  "title": "공지",
                  "content": "내용",
                  "pinned": false,
                  "publishedAt": "2026-06-15T16:00:00+09:00"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/notices")
                        .with(user("1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanMutateNotices() throws Exception {
        var body = """
                {
                  "title": "공지",
                  "content": "내용",
                  "pinned": false,
                  "publishedAt": "2026-06-15T16:00:00+09:00"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/notices")
                        .with(user("1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noticeId").value(0));
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
        PendingSocialCookieService pendingSocialCookieService() {
            return new PendingSocialCookieService(
                    new PendingSocialProperties(
                            600,
                            "local",
                            new PendingSocialProperties.Cookie("JIBER_PENDING_SOCIAL", "/api/v1/auth", "Lax", false)
                    )
            );
        }

        @Bean
        AuthService authService(JwtTokenService jwtTokenService, AuthUserMapper authUserMapper) {
            var refreshProperties = new RefreshTokenProperties(
                    1209600,
                    "local",
                    new RefreshTokenProperties.Cookie("JIBER_REFRESH_TOKEN", "/api/v1/auth", "Lax", false)
            );
            return AuthService.forTesting(
                    jwtTokenService,
                    new RefreshTokenService(
                            refreshProperties,
                            new SecurityRefreshSessionMapper()
                    ),
                    authUserMapper,
                    new BCryptPasswordEncoder(4),
                    new EmailNormalizer(),
                    new PasswordPolicy(),
                    Clock.fixed(Instant.parse("2026-06-15T07:00:00Z"), ZoneOffset.UTC)
            );
        }

        @Bean
        SocialLoginService socialLoginService(JwtTokenService jwtTokenService, AuthUserMapper authUserMapper) {
            var refreshProperties = new RefreshTokenProperties(
                    1209600,
                    "local",
                    new RefreshTokenProperties.Cookie("JIBER_REFRESH_TOKEN", "/api/v1/auth", "Lax", false)
            );
            var pendingProperties = new PendingSocialProperties(
                    600,
                    "local",
                    new PendingSocialProperties.Cookie("JIBER_PENDING_SOCIAL", "/api/v1/auth", "Lax", false)
            );
            var emailNormalizer = new EmailNormalizer();
            return new SocialLoginService(
                    jwtTokenService,
                    new RefreshTokenService(
                            refreshProperties,
                            new SecurityRefreshSessionMapper()
                    ),
                    authUserMapper,
                    new SecuritySocialAccountMapper(),
                    new com.jiber.backend.auth.PendingSocialSessionService(
                            pendingProperties,
                            new SecurityPendingSocialSessionMapper(),
                            emailNormalizer
                    )
            );
        }

        @Bean
        AuthUserMapper authUserMapper() {
            return new SecurityAuthUserMapper();
        }

        @Bean
        FavoriteService favoriteService() {
            return new FavoriteService();
        }

        @Bean
        NoticeService noticeService() {
            return new NoticeService();
        }

        private static class SecurityAuthUserMapper implements AuthUserMapper {

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
        }

        private static class SecurityRefreshSessionMapper implements RefreshSessionMapper {

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

        private static class SecuritySocialAccountMapper implements SocialAccountMapper {

            @Override
            public int insert(SocialAccountInsertCommand command) {
                return 1;
            }

            @Override
            public SocialAccountRecord findByProvider(String oauthProvider, String providerUserId) {
                return null;
            }

            @Override
            public AuthUserRecord findLinkedUserByProvider(String oauthProvider, String providerUserId) {
                return null;
            }

            @Override
            public java.util.List<SocialAccountRecord> findByUserId(Long userId) {
                return java.util.List.of();
            }

            @Override
            public int updateLastLoginAt(String oauthProvider, String providerUserId, OffsetDateTime lastLoginAt) {
                return 0;
            }
        }

        private static class SecurityPendingSocialSessionMapper implements PendingSocialSessionMapper {

            @Override
            public int insert(PendingSocialSessionInsertCommand command) {
                return 1;
            }

            @Override
            public PendingSocialSessionRecord findByTokenHash(String pendingTokenHash) {
                return null;
            }

            @Override
            public PendingSocialSessionRecord findActiveByTokenHash(String pendingTokenHash, OffsetDateTime now) {
                return null;
            }

            @Override
            public int consume(String pendingTokenHash, OffsetDateTime consumedAt) {
                return 0;
            }
        }
    }
}
