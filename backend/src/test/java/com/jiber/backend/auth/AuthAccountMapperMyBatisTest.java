package com.jiber.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_account_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,USERS",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath:/mapper/**/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
class AuthAccountMapperMyBatisTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-15T07:00:00Z");

    @Autowired
    private AuthUserMapper authUserMapper;

    @Autowired
    private SocialAccountMapper socialAccountMapper;

    @Autowired
    private PendingSocialSessionMapper pendingSocialSessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS oauth_pending_social_sessions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_social_accounts");
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
                CREATE TABLE user_social_accounts (
                    social_account_id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    oauth_provider VARCHAR(20) NOT NULL,
                    provider_user_id VARCHAR(255) NOT NULL,
                    provider_email VARCHAR(320),
                    provider_display_name VARCHAR(100),
                    linked_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    last_login_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (social_account_id),
                    UNIQUE (oauth_provider, provider_user_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE oauth_pending_social_sessions (
                    pending_social_session_id BIGINT NOT NULL AUTO_INCREMENT,
                    pending_token_hash CHAR(64) NOT NULL,
                    oauth_provider VARCHAR(20) NOT NULL,
                    provider_user_id VARCHAR(255) NOT NULL,
                    provider_email VARCHAR(320),
                    provider_display_name VARCHAR(100),
                    suggested_email VARCHAR(320),
                    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    consumed_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (pending_social_session_id),
                    UNIQUE (pending_token_hash)
                )
                """);
    }

    @Test
    void socialAccountInsertFindAndLinkedUserLookupUseXmlConstructorMappings() {
        var user = insertUser();
        socialAccountMapper.insert(new SocialAccountInsertCommand(
                user.userId(),
                "NAVER",
                "naver-user-1",
                "naver-user@example.com",
                "네이버 사용자",
                NOW,
                NOW
        ));

        var byProvider = socialAccountMapper.findByProvider("NAVER", "naver-user-1");
        var linkedUser = socialAccountMapper.findLinkedUserByProvider("NAVER", "naver-user-1");
        var byUserId = socialAccountMapper.findByUserId(user.userId());

        assertThat(byProvider).isNotNull();
        assertThat(byProvider.socialAccountId()).isNotNull();
        assertThat(byProvider.userId()).isEqualTo(user.userId());
        assertThat(byProvider.oauthProvider()).isEqualTo("NAVER");
        assertThat(byProvider.providerUserId()).isEqualTo("naver-user-1");
        assertThat(byProvider.providerEmail()).isEqualTo("naver-user@example.com");
        assertThat(byProvider.providerDisplayName()).isEqualTo("네이버 사용자");
        assertThat(byProvider.linkedAt()).isNotNull();
        assertThat(byProvider.lastLoginAt()).isNotNull();
        assertThat(byProvider.createdAt()).isNotNull();
        assertThat(byProvider.updatedAt()).isNotNull();

        assertThat(linkedUser.userId()).isEqualTo(user.userId());
        assertThat(linkedUser.role()).isEqualTo("USER");
        assertThat(linkedUser.toPrincipal().roles()).doesNotContain("ADMIN");
        assertThat(byUserId).containsExactly(byProvider);
    }

    @Test
    void duplicateSocialProviderSubjectIsRejectedByUniqueConstraint() {
        var user = insertUser();
        var command = new SocialAccountInsertCommand(
                user.userId(),
                "KAKAO",
                "kakao-user-1",
                "kakao-user@example.com",
                "카카오 사용자",
                NOW,
                NOW
        );

        socialAccountMapper.insert(command);

        assertThatThrownBy(() -> socialAccountMapper.insert(command))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void pendingSocialSessionInsertFindActiveAndConsumeUseOnlyTokenHash() {
        var tokenHash = "b".repeat(64);
        pendingSocialSessionMapper.insert(new PendingSocialSessionInsertCommand(
                tokenHash,
                "GOOGLE",
                "google-user-1",
                "google-user@example.com",
                "구글 사용자",
                "google-user@example.com",
                NOW.plusMinutes(10)
        ));

        var stored = pendingSocialSessionMapper.findByTokenHash(tokenHash);
        var active = pendingSocialSessionMapper.findActiveByTokenHash(tokenHash, NOW);

        assertThat(stored).isNotNull();
        assertThat(stored.pendingSocialSessionId()).isNotNull();
        assertThat(stored.pendingTokenHash()).isEqualTo(tokenHash);
        assertThat(stored.pendingTokenHash()).hasSize(64);
        assertThat(stored.oauthProvider()).isEqualTo("GOOGLE");
        assertThat(stored.providerUserId()).isEqualTo("google-user-1");
        assertThat(stored.providerEmail()).isEqualTo("google-user@example.com");
        assertThat(stored.providerDisplayName()).isEqualTo("구글 사용자");
        assertThat(stored.suggestedEmail()).isEqualTo("google-user@example.com");
        assertThat(stored.expiresAt()).isNotNull();
        assertThat(stored.consumedAt()).isNull();
        assertThat(active).isEqualTo(stored);

        pendingSocialSessionMapper.consume(tokenHash, NOW.plusMinutes(1));

        assertThat(pendingSocialSessionMapper.findByTokenHash(tokenHash).consumedAt()).isNotNull();
        assertThat(pendingSocialSessionMapper.findActiveByTokenHash(tokenHash, NOW.plusMinutes(2))).isNull();
    }

    private AuthUserRecord insertUser() {
        authUserMapper.insertEmailUser(
                "user@example.com",
                "$2a$10$testhashvaluefor mapper storage only",
                "사용자",
                "USER",
                true,
                NOW
        );
        return authUserMapper.findByEmail("user@example.com");
    }
}
