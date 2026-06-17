-- Auth account migration: split Jiber users from OAuth provider identities.
-- Run after 001_phase1_schema.sql on existing local/dev databases.
-- Versioned one-shot migration: Do not re-run this file after it succeeds.
-- If it fails after DDL has started, restore the DB snapshot/volume or inspect
-- the partially applied schema before retrying. If the duplicate email preflight
-- below fails, no DDL in this file has run yet; clean the legacy rows first.
-- The preflight intentionally fails before adding uk_users_email because
-- duplicate provider-owned legacy users can share the same normalized email.
-- See backend/README.md "004 Auth Account Migration Runbook" for the sanitized
-- duplicate legacy email query and cleanup policy.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

DELIMITER //
DROP PROCEDURE IF EXISTS jiber_auth_004_assert_no_duplicate_emails//
CREATE PROCEDURE jiber_auth_004_assert_no_duplicate_emails()
BEGIN
    DECLARE duplicate_email_count BIGINT DEFAULT 0;

    SELECT COUNT(*)
    INTO duplicate_email_count
    FROM (
        SELECT LOWER(TRIM(email)) AS normalized_email
        FROM users
        WHERE email IS NOT NULL
          AND TRIM(email) <> ''
        GROUP BY LOWER(TRIM(email))
        HAVING COUNT(*) > 1
    ) duplicate_emails;

    IF duplicate_email_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'JIBER_AUTH_MIGRATION_DUPLICATE_EMAIL: clean duplicate legacy user emails before db/004_auth_account_social_link.sql';
    END IF;
END//
CALL jiber_auth_004_assert_no_duplicate_emails()//
DROP PROCEDURE IF EXISTS jiber_auth_004_assert_no_duplicate_emails//
DELIMITER ;

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER email;

ALTER TABLE users
    MODIFY oauth_provider ENUM('GOOGLE', 'KAKAO', 'NAVER') NULL,
    MODIFY provider_user_id VARCHAR(255) NULL;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_email (email);

CREATE TABLE IF NOT EXISTS user_social_accounts (
    social_account_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    oauth_provider ENUM('GOOGLE', 'KAKAO', 'NAVER') NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320) NULL,
    provider_display_name VARCHAR(100) NULL,
    linked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_login_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (social_account_id),
    UNIQUE KEY uk_user_social_accounts_provider_subject (oauth_provider, provider_user_id),
    KEY idx_user_social_accounts_user (user_id),
    CONSTRAINT fk_user_social_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO user_social_accounts (
    user_id,
    oauth_provider,
    provider_user_id,
    provider_email,
    provider_display_name,
    linked_at,
    last_login_at,
    created_at,
    updated_at
)
SELECT
    user_id,
    oauth_provider,
    provider_user_id,
    email,
    display_name,
    COALESCE(last_login_at, created_at),
    last_login_at,
    created_at,
    updated_at
FROM users
WHERE oauth_provider IS NOT NULL
  AND provider_user_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    provider_email = VALUES(provider_email),
    provider_display_name = VALUES(provider_display_name),
    last_login_at = VALUES(last_login_at),
    updated_at = CURRENT_TIMESTAMP(6);

CREATE TABLE IF NOT EXISTS oauth_pending_social_sessions (
    pending_social_session_id BIGINT NOT NULL AUTO_INCREMENT,
    pending_token_hash CHAR(64) NOT NULL,
    oauth_provider ENUM('GOOGLE', 'KAKAO', 'NAVER') NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320) NULL,
    provider_display_name VARCHAR(100) NULL,
    suggested_email VARCHAR(320) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (pending_social_session_id),
    UNIQUE KEY uk_oauth_pending_social_sessions_token_hash (pending_token_hash),
    KEY idx_oauth_pending_social_sessions_active (pending_token_hash, consumed_at, expires_at),
    KEY idx_oauth_pending_social_sessions_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
