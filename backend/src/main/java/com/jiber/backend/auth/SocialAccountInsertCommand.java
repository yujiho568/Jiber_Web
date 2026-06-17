package com.jiber.backend.auth;

import java.time.OffsetDateTime;

public record SocialAccountInsertCommand(
        Long userId,
        String oauthProvider,
        String providerUserId,
        String providerEmail,
        String providerDisplayName,
        OffsetDateTime linkedAt,
        OffsetDateTime lastLoginAt
) {
}
