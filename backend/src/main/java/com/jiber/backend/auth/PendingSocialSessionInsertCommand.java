package com.jiber.backend.auth;

import java.time.OffsetDateTime;

public record PendingSocialSessionInsertCommand(
        String pendingTokenHash,
        String oauthProvider,
        String providerUserId,
        String providerEmail,
        String providerDisplayName,
        String suggestedEmail,
        OffsetDateTime expiresAt
) {
}
