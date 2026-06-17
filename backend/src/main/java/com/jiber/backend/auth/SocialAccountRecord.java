package com.jiber.backend.auth;

import java.time.OffsetDateTime;

public record SocialAccountRecord(
        Long socialAccountId,
        Long userId,
        String oauthProvider,
        String providerUserId,
        String providerEmail,
        String providerDisplayName,
        OffsetDateTime linkedAt,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
