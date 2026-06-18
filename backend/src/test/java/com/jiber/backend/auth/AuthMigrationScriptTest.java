package com.jiber.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthMigrationScriptTest {

    @Test
    void authAccountMigrationFailsFastBeforeAddingEmailUniqueKeyWhenLegacyEmailsAreDuplicated() throws Exception {
        var migration = Files.readString(Path.of("..", "db", "004_auth_account_social_link.sql"));

        assertThat(migration).contains("SIGNAL SQLSTATE '45000'");
        assertThat(migration).contains("JIBER_AUTH_MIGRATION_DUPLICATE_EMAIL");
        assertThat(migration).contains("LOWER(TRIM(email))");
        assertThat(migration).contains("HAVING COUNT(*) > 1");
        assertThat(migration.indexOf("JIBER_AUTH_MIGRATION_DUPLICATE_EMAIL"))
                .isLessThan(migration.indexOf("ADD UNIQUE KEY uk_users_email"));
        assertThat(migration).doesNotContain("MESSAGE_TEXT = CONCAT");
    }

    @Test
    void authAccountMigrationDocumentsOneShotRerunRiskAndCleanupRunbook() throws Exception {
        var migration = Files.readString(Path.of("..", "db", "004_auth_account_social_link.sql"));
        var readme = Files.readString(Path.of("README.md"));

        assertThat(migration).contains("one-shot");
        assertThat(migration).contains("Do not re-run");
        assertThat(readme).contains("004 Auth Account Migration Runbook");
        assertThat(readme).contains("duplicate legacy email");
        assertThat(readme).contains("canonical user");
        assertThat(readme).contains("user_social_accounts");
        assertThat(readme).contains("refresh_sessions");
        assertThat(readme).contains("favorite_apartments");
        assertThat(readme).contains("favorite_areas");
        assertThat(readme).contains("notices");
        assertThat(readme).contains("apartment_price_predictions");
    }

    @Test
    void authSchemaPreflightScriptDocumentsRequiredChecksWithoutMutationSql() throws Exception {
        var script = Files.readString(Path.of("..", "scripts", "check-auth-schema.sh"));
        var readme = Files.readString(Path.of("README.md"));
        var rootReadme = Files.readString(Path.of("..", "README.md"));

        assertThat(script).contains("users.password_hash");
        assertThat(script).contains("uk_users_email");
        assertThat(script).contains("user_social_accounts");
        assertThat(script).contains("oauth_pending_social_sessions");
        assertThat(script).contains("004 migration");
        assertThat(script).contains("docker compose");
        assertThat(script).contains("docker exec jiber-mysql");
        assertThat(script).contains("INDEX_NAME = 'uk_users_email'");
        assertThat(script).doesNotContain("ALTER TABLE");
        assertThat(script).doesNotContain("DROP TABLE");
        assertThat(script).doesNotContain("TRUNCATE");
        assertThat(script).doesNotContain("DELETE FROM");
        assertThat(script).doesNotContain("INSERT INTO");
        assertThat(script).doesNotContain("UPDATE ");

        assertThat(readme).contains("Auth E2E Smoke Schema Preflight");
        assertThat(readme).contains("scripts/check-auth-schema.sh");
        assertThat(readme).contains("users.password_hash");
        assertThat(readme).contains("users.email unique");
        assertThat(readme).contains("user_social_accounts");
        assertThat(readme).contains("oauth_pending_social_sessions");
        assertThat(readme).contains("004는 one-shot migration");
        assertThat(readme).contains("backup");
        assertThat(readme).doesNotContain("provider callback 후 local user를 자동 생성/로그인");
        assertThat(readme).contains("linked social subject");
        assertThat(readme).contains("email/password 재인증");
        assertThat(rootReadme).contains("scripts/check-auth-schema.sh");
    }
}
