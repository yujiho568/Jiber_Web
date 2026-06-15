-- Jiber Web Phase 1 MySQL schema draft.
-- Target: MySQL 8.x, utf8mb4, Spring Boot + MyBatis.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

CREATE TABLE IF NOT EXISTS properties (
    property_id BIGINT NOT NULL AUTO_INCREMENT,
    property_type ENUM('APARTMENT', 'OFFICETEL', 'VILLA', 'HOUSE') NOT NULL,
    name VARCHAR(255) NOT NULL,
    sido VARCHAR(100) NOT NULL,
    sigungu VARCHAR(100) NOT NULL,
    legal_dong VARCHAR(100) NOT NULL,
    road_address VARCHAR(500) NULL,
    jibun_address VARCHAR(500) NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    built_year SMALLINT NULL,
    household_count INT NULL,
    source_system VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (property_id),
    KEY idx_properties_type_bounds (property_type, latitude, longitude),
    KEY idx_properties_bounds (latitude, longitude),
    KEY idx_properties_region (sido, sigungu, legal_dong),
    KEY idx_properties_name (name),
    CONSTRAINT chk_properties_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_properties_longitude CHECK (longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS property_transactions (
    transaction_id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    transaction_type ENUM('SALE', 'JEONSE', 'MONTHLY_RENT') NOT NULL,
    exclusive_area_m2 DECIMAL(10, 4) NULL,
    floor INT NULL,
    deal_amount_krw BIGINT NULL,
    deposit_amount_krw BIGINT NULL,
    monthly_rent_krw BIGINT NULL,
    deal_date DATE NOT NULL,
    source_system VARCHAR(100) NULL,
    source_transaction_id VARCHAR(150) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (transaction_id),
    KEY idx_transactions_property_date (property_id, deal_date DESC),
    KEY idx_transactions_type_date (transaction_type, deal_date DESC),
    KEY idx_transactions_amount (deal_amount_krw),
    KEY idx_transactions_area (exclusive_area_m2),
    CONSTRAINT fk_transactions_property
        FOREIGN KEY (property_id) REFERENCES properties (property_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    oauth_provider ENUM('GOOGLE', 'KAKAO', 'NAVER') NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(320) NULL,
    display_name VARCHAR(100) NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_provider_subject (oauth_provider, provider_user_id),
    KEY idx_users_email (email),
    KEY idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS refresh_sessions (
    refresh_session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    rotated_from_session_id BIGINT NULL,
    user_agent VARCHAR(500) NULL,
    ip_address VARBINARY(16) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (refresh_session_id),
    UNIQUE KEY uk_refresh_sessions_token_hash (refresh_token_hash),
    KEY idx_refresh_sessions_user_active (user_id, revoked_at, expires_at),
    KEY idx_refresh_sessions_expires_at (expires_at),
    CONSTRAINT fk_refresh_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_refresh_sessions_rotated_from
        FOREIGN KEY (rotated_from_session_id) REFERENCES refresh_sessions (refresh_session_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS favorite_apartments (
    favorite_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_favorite_apartments_user_property (user_id, property_id),
    KEY idx_favorite_apartments_property (property_id),
    CONSTRAINT fk_favorite_apartments_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_favorite_apartments_property
        FOREIGN KEY (property_id) REFERENCES properties (property_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS favorite_areas (
    favorite_area_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    label VARCHAR(120) NOT NULL,
    sido VARCHAR(100) NULL,
    sigungu VARCHAR(100) NULL,
    legal_dong VARCHAR(100) NULL,
    center_lat DECIMAL(10, 7) NULL,
    center_lng DECIMAL(10, 7) NULL,
    zoom_level INT NULL,
    normalized_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (favorite_area_id),
    UNIQUE KEY uk_favorite_areas_user_normalized (user_id, normalized_key),
    KEY idx_favorite_areas_region (sido, sigungu, legal_dong),
    KEY idx_favorite_areas_center (center_lat, center_lng),
    CONSTRAINT fk_favorite_areas_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_favorite_areas_center_pair CHECK (
        (center_lat IS NULL AND center_lng IS NULL)
        OR (center_lat IS NOT NULL AND center_lng IS NOT NULL)
    ),
    CONSTRAINT chk_favorite_areas_scope CHECK (
        sido IS NOT NULL
        OR sigungu IS NOT NULL
        OR legal_dong IS NOT NULL
        OR (center_lat IS NOT NULL AND center_lng IS NOT NULL)
    ),
    CONSTRAINT chk_favorite_areas_latitude CHECK (center_lat IS NULL OR center_lat BETWEEN -90 AND 90),
    CONSTRAINT chk_favorite_areas_longitude CHECK (center_lng IS NULL OR center_lng BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notices (
    notice_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP(6) NOT NULL,
    created_by_user_id BIGINT NULL,
    updated_by_user_id BIGINT NULL,
    deleted_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (notice_id),
    KEY idx_notices_public_order (deleted_at, pinned, published_at DESC),
    KEY idx_notices_created_at (created_at DESC),
    FULLTEXT KEY ft_notices_title_content (title, content),
    CONSTRAINT fk_notices_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_notices_updated_by
        FOREIGN KEY (updated_by_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS apartment_price_predictions (
    prediction_id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    exclusive_area_m2 DECIMAL(10, 4) NOT NULL,
    floor INT NOT NULL,
    as_of_date DATE NOT NULL,
    supported BOOLEAN NOT NULL DEFAULT TRUE,
    estimated_price_krw BIGINT NULL,
    prediction_interval_lower_krw BIGINT NULL,
    prediction_interval_upper_krw BIGINT NULL,
    unsupported_reason VARCHAR(100) NULL,
    model_version VARCHAR(100) NOT NULL,
    baseline_date DATE NOT NULL,
    feature_set_version VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (prediction_id),
    KEY idx_predictions_property_created (property_id, created_at DESC),
    KEY idx_predictions_user_created (user_id, created_at DESC),
    KEY idx_predictions_model (model_version, feature_set_version, baseline_date),
    CONSTRAINT fk_predictions_property
        FOREIGN KEY (property_id) REFERENCES properties (property_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_predictions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS apartment_shap_values (
    shap_value_id BIGINT NOT NULL AUTO_INCREMENT,
    prediction_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    feature VARCHAR(150) NOT NULL,
    label_ko VARCHAR(150) NOT NULL,
    feature_value_json JSON NULL,
    shap_value_krw BIGINT NOT NULL,
    direction ENUM('UP', 'DOWN', 'NEUTRAL') NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (shap_value_id),
    KEY idx_shap_values_prediction (prediction_id),
    KEY idx_shap_values_property_feature (property_id, feature),
    CONSTRAINT fk_shap_values_prediction
        FOREIGN KEY (prediction_id) REFERENCES apartment_price_predictions (prediction_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_shap_values_property
        FOREIGN KEY (property_id) REFERENCES properties (property_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
