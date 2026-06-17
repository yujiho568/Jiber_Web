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
}
