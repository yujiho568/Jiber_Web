package com.jiber.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiber.backend.auth.AuthController;
import com.jiber.backend.auth.AuthService;
import com.jiber.backend.auth.AuthUserPrincipal;
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
import com.jiber.backend.chat.ChatController;
import com.jiber.backend.chat.ChatResponse;
import com.jiber.backend.chat.ChatService;
import com.jiber.backend.chat.RagConfigResponse;
import com.jiber.backend.favorite.FavoriteAreaInsertCommand;
import com.jiber.backend.favorite.FavoriteAreaRow;
import com.jiber.backend.favorite.FavoriteApartmentRow;
import com.jiber.backend.favorite.FavoriteController;
import com.jiber.backend.favorite.FavoriteMapper;
import com.jiber.backend.favorite.FavoriteService;
import com.jiber.backend.notice.AdminNoticeController;
import com.jiber.backend.notice.NoticeService;
import com.jiber.backend.property.PropertyType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        AuthController.class,
        FavoriteController.class,
        AdminNoticeController.class,
        ChatController.class
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

    @MockBean
    private ChatService chatService;

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

        mockMvc.perform(get("/api/v1/favorites/areas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void userCanAccessFavorites() throws Exception {
        mockMvc.perform(get("/api/v1/favorites/apartments")
                        .with(authentication(authPrincipal(1L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/api/v1/favorites/areas")
                        .with(authentication(authPrincipal(1L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void chatRequiresAuthentication() throws Exception {
        var body = """
                {
                  "question": "전세 계약 전에 무엇을 확인해야 하나요?"
                }
                """;

        mockMvc.perform(post("/api/v1/chat/real-estate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void userCanAccessChatSkeletonEndpoint() throws Exception {
        var body = """
                {
                  "question": "전세 계약 전에 무엇을 확인해야 하나요?"
                }
                """;

        when(chatService.ask(any())).thenReturn(new ChatResponse(
                false,
                "챗봇은 현재 skeleton 단계입니다.",
                List.of(),
                "chat-skeleton-v1",
                new RagConfigResponse("disabled", 0, 0, false, false)
        ));

        mockMvc.perform(post("/api/v1/chat/real-estate")
                        .with(authentication(authPrincipal(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.model").value("chat-skeleton-v1"))
                .andExpect(jsonPath("$.ragConfig.embedding").value("disabled"));
    }

    @Test
    void favoriteMutationsRequireAuthentication() throws Exception {
        var body = """
                {
                  "propertyId": 1001
                }
                """;

        mockMvc.perform(post("/api/v1/favorites/apartments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(delete("/api/v1/favorites/apartments/1001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        var areaBody = """
                {
                  "label": "강남구 역삼동",
                  "sido": "서울특별시",
                  "sigungu": "강남구",
                  "legalDong": "역삼동"
                }
                """;

        mockMvc.perform(post("/api/v1/favorites/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(areaBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(delete("/api/v1/favorites/areas/801"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void invalidFavoriteAreaPayloadReturnsValidationFailed() throws Exception {
        var body = """
                {
                  "label": "   ",
                  "centerLat": 37.5001
                }
                """;

        mockMvc.perform(post("/api/v1/favorites/areas")
                        .with(authentication(authPrincipal(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void oversizedFavoriteAreaLabelReturnsValidationFailed() throws Exception {
        var body = """
                {
                  "label": "%s",
                  "sido": "서울특별시"
                }
                """.formatted("가".repeat(121));

        mockMvc.perform(post("/api/v1/favorites/areas")
                        .with(authentication(authPrincipal(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void oversizedFavoriteAreaRegionFieldsReturnValidationFailed() throws Exception {
        var tooLong = "가".repeat(101);

        mockMvc.perform(post("/api/v1/favorites/areas")
                        .with(authentication(authPrincipal(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(areaBody("지역", tooLong, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/favorites/areas")
                        .with(authentication(authPrincipal(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(areaBody("지역", "서울특별시", tooLong, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/favorites/areas")
                        .with(authentication(authPrincipal(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(areaBody("지역", "서울특별시", "강남구", tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
        FavoriteService favoriteService(FavoriteMapper favoriteMapper) {
            return new FavoriteService(favoriteMapper);
        }

        @Bean
        FavoriteMapper favoriteMapper() {
            return new SecurityFavoriteMapper();
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

        private static class SecurityFavoriteMapper implements FavoriteMapper {

            @Override
            public Optional<PropertyType> findPropertyTypeById(Long propertyId) {
                return Optional.of(PropertyType.APARTMENT);
            }

            @Override
            public List<FavoriteApartmentRow> findFavoriteApartments(Long userId) {
                return List.of();
            }

            @Override
            public Optional<FavoriteApartmentRow> findFavoriteApartment(Long userId, Long propertyId) {
                var row = new FavoriteApartmentRow();
                row.setFavoriteId(1L);
                row.setPropertyId(propertyId);
                row.setPropertyType(PropertyType.APARTMENT);
                row.setName("보안 테스트 아파트");
                row.setCreatedAt(OffsetDateTime.parse("2026-06-15T16:00:00+09:00"));
                return Optional.of(row);
            }

            @Override
            public int insertFavoriteApartment(Long userId, Long propertyId) {
                return 1;
            }

            @Override
            public int deleteFavoriteApartment(Long userId, Long propertyId) {
                return 1;
            }

            @Override
            public boolean existsFavoriteApartment(Long userId, Long propertyId) {
                return false;
            }

            @Override
            public List<FavoriteAreaRow> findFavoriteAreas(Long userId) {
                return List.of();
            }

            @Override
            public Optional<FavoriteAreaRow> findFavoriteAreaByNormalizedKey(Long userId, String normalizedKey) {
                var row = new FavoriteAreaRow();
                row.setFavoriteAreaId(801L);
                row.setLabel("강남구 역삼동");
                row.setSido("서울특별시");
                row.setSigungu("강남구");
                row.setLegalDong("역삼동");
                row.setZoomLevel(5);
                row.setNormalizedKey(normalizedKey);
                row.setCreatedAt(OffsetDateTime.parse("2026-06-15T16:00:00+09:00"));
                return Optional.of(row);
            }

            @Override
            public int insertFavoriteArea(FavoriteAreaInsertCommand command) {
                return 1;
            }

            @Override
            public int deleteFavoriteArea(Long userId, Long favoriteAreaId) {
                return 1;
            }

            @Override
            public boolean existsFavoriteAreaByNormalizedKey(Long userId, String normalizedKey) {
                return false;
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

    private UsernamePasswordAuthenticationToken authPrincipal(Long userId, String role) {
        var principal = new AuthUserPrincipal(
                userId,
                "security-user-" + userId + "@example.com",
                "보안 사용자",
                Set.of(role)
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private String areaBody(String label, String sido, String sigungu, String legalDong) {
        var sidoField = sido == null ? "" : "\"sido\": \"" + sido + "\",";
        var sigunguField = sigungu == null ? "" : "\"sigungu\": \"" + sigungu + "\",";
        var legalDongField = legalDong == null ? "" : "\"legalDong\": \"" + legalDong + "\",";
        return """
                {
                  "label": "%s",
                  %s
                  %s
                  %s
                  "centerLat": 37.5001,
                  "centerLng": 127.0364
                }
                """.formatted(label, sidoField, sigunguField, legalDongField);
    }
}
