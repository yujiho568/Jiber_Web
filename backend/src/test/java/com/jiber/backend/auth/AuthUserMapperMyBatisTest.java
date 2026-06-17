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
        "spring.datasource.url=jdbc:h2:mem:auth_user_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,USERS",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath:/mapper/**/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
class AuthUserMapperMyBatisTest {

    private static final String PASSWORD_HASH = "$2a$10$testhashvaluefor mapper storage only";

    @Autowired
    private AuthUserMapper authUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
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
    }

    @Test
    void emailUserInsertAndFindByIdAndEmailUsesRecordConstructorMapping() {
        var lastLoginAt = OffsetDateTime.parse("2026-06-15T07:00:00Z");

        authUserMapper.insertEmailUser(
                "user@example.com",
                PASSWORD_HASH,
                "사용자",
                "USER",
                true,
                lastLoginAt
        );

        var byEmail = authUserMapper.findByEmail("user@example.com");
        var byId = authUserMapper.findById(byEmail.userId());

        assertThat(byEmail).isNotNull();
        assertThat(byId).isEqualTo(byEmail);
        assertThat(byEmail.userId()).isNotNull();
        assertThat(byEmail.email()).isEqualTo("user@example.com");
        assertThat(byEmail.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(byEmail.passwordHash()).startsWith("$2a$");
        assertThat(byEmail.displayName()).isEqualTo("사용자");
        assertThat(byEmail.role()).isEqualTo("USER");
        assertThat(byEmail.enabled()).isTrue();
        assertThat(byEmail.lastLoginAt()).isNotNull();
        assertThat(byEmail.createdAt()).isNotNull();
        assertThat(byEmail.updatedAt()).isNotNull();
        assertThat(byEmail.toPrincipal().roles()).containsExactly("USER");
        assertThat(byEmail.toPrincipal().roles()).doesNotContain("ADMIN");
    }

    @Test
    void duplicateEmailIsRejectedByUniqueConstraint() {
        var lastLoginAt = OffsetDateTime.parse("2026-06-15T07:00:00Z");
        authUserMapper.insertEmailUser("user@example.com", PASSWORD_HASH, "사용자", "USER", true, lastLoginAt);

        assertThatThrownBy(() ->
                authUserMapper.insertEmailUser("user@example.com", PASSWORD_HASH, "다른 사용자", "USER", true, lastLoginAt)
        ).isInstanceOf(DuplicateKeyException.class);
    }
}
