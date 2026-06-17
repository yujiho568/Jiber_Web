package com.jiber.backend.auth;

import java.time.OffsetDateTime;

public record PendingSocialSessionRecord(
        Long pendingSocialSessionId,
        String pendingTokenHash,
        String oauthProvider,
        String providerUserId,
        String providerEmail,
        String providerDisplayName,
        String suggestedEmail,
        OffsetDateTime expiresAt,
        OffsetDateTime consumedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public boolean activeAt(OffsetDateTime now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
