package com.jiber.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:refresh_session_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,USERS",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath:/mapper/**/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
class RefreshSessionMapperMyBatisTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T07:00:00Z"), ZoneOffset.UTC);
    private static final RefreshTokenProperties PROPERTIES = new RefreshTokenProperties(
            1209600,
            "local",
            new RefreshTokenProperties.Cookie("JIBER_REFRESH_TOKEN", "/api/v1/auth", "Lax", false)
    );

    @Autowired
    private RefreshSessionMapper refreshSessionMapper;

    @Autowired
    private AuthUserMapper authUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS refresh_sessions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    user_id BIGINT NOT NULL AUTO_INCREMENT,
                    email VARCHAR(320) NOT NULL,
                    password_hash VARCHAR(255),
                    display_name VARCHAR(100),
                    role VARCHAR(20) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    last_login_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id),
                    UNIQUE (email)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE refresh_sessions (
                    refresh_session_id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    refresh_token_hash CHAR(64) NOT NULL,
                    rotated_from_session_id BIGINT,
                    user_agent VARCHAR(500),
                    ip_address VARBINARY(16),
                    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    revoked_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (refresh_session_id),
                    UNIQUE (refresh_token_hash)
                )
                """);
    }

    @Test
    void insertFindAndRevokeMapRefreshSessionRecordThroughXml() {
        var expiresAt = OffsetDateTime.parse("2026-06-29T07:00:00Z");
        var tokenHash = "a".repeat(64);

        refreshSessionMapper.insert(new RefreshSessionInsertCommand(
                1L,
                tokenHash,
                null,
                "JUnit",
                new byte[]{127, 0, 0, 1},
                expiresAt
        ));

        var activeSession = refreshSessionMapper.findByTokenHash(tokenHash);

        assertThat(activeSession).isNotNull();
        assertThat(activeSession.refreshSessionId()).isNotNull();
        assertThat(activeSession.userId()).isEqualTo(1L);
        assertThat(activeSession.refreshTokenHash()).isEqualTo(tokenHash);
        assertThat(activeSession.rotatedFromSessionId()).isNull();
        assertThat(activeSession.userAgent()).isEqualTo("JUnit");
        assertThat(activeSession.ipAddress()).containsExactly(127, 0, 0, 1);
        assertThat(activeSession.expiresAt()).isNotNull();
        assertThat(activeSession.revokedAt()).isNull();
        assertThat(activeSession.createdAt()).isNotNull();
        assertThat(activeSession.updatedAt()).isNotNull();

        var revokedAt = OffsetDateTime.parse("2026-06-15T07:01:00Z");
        refreshSessionMapper.revokeBySessionId(activeSession.refreshSessionId(), revokedAt);

        var revokedSession = refreshSessionMapper.findByTokenHash(tokenHash);
        assertThat(revokedSession.revokedAt()).isNotNull();
    }

    @Test
    void refreshTokenServiceIssueAndRotateWorksWithDbBackedRefreshSessionMapper() {
        var service = RefreshTokenService.forTesting(
                PROPERTIES,
                refreshSessionMapper,
                new SecureRandom(new byte[]{1, 2, 3, 4}),
                FIXED_CLOCK
        );
        var context = new RefreshRequestContext("JUnit", "127.0.0.1");

        var issued = service.issue(1L, context);
        var rotation = service.rotate(issued.token(), context);

        assertThat(rotation.userId()).isEqualTo(1L);
        assertThat(rotation.token()).isNotBlank();
        assertThat(rotation.token()).isNotEqualTo(issued.token());

        var originalSession = refreshSessionMapper.findByTokenHash(service.hash(issued.token()));
        var rotatedSession = refreshSessionMapper.findByTokenHash(service.hash(rotation.token()));
        assertThat(originalSession.revokedAt()).isNotNull();
        assertThat(rotatedSession.rotatedFromSessionId()).isEqualTo(originalSession.refreshSessionId());
        assertThat(rotatedSession.revokedAt()).isNull();
    }

    @Test
    void authServiceRefreshWorksWithDbBackedUserAndRefreshSessionMappers() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    email,
                    password_hash,
                    display_name,
                    role,
                    enabled,
                    last_login_at
                ) VALUES (
                    'naver-user@example.com',
                    '$2a$10$testhashvaluefor mapper storage only',
                    '네이버 사용자',
                    'USER',
                    TRUE,
                    CURRENT_TIMESTAMP
                )
                """);
        var refreshTokenService = RefreshTokenService.forTesting(
                PROPERTIES,
                refreshSessionMapper,
                new SecureRandom(new byte[]{5, 6, 7, 8}),
                FIXED_CLOCK
        );
        var jwtTokenService = JwtTokenService.forTesting(
                new JwtTokenProperties("jiber-test", "test-secret-with-enough-entropy-for-hmac", 900, "test"),
                new ObjectMapper(),
                FIXED_CLOCK
        );
        var authService = new AuthService(jwtTokenService, refreshTokenService, authUserMapper);
        var context = new RefreshRequestContext("JUnit", "127.0.0.1");
        var issued = refreshTokenService.issue(1L, context);

        var result = authService.refresh(issued.token(), context);

        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().user().roles()).containsExactly("USER");
        assertThat(result.response().user().roles()).doesNotContain("ADMIN");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(issued.token());
    }
}
