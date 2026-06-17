package com.jiber.backend.auth;

import java.time.OffsetDateTime;
import java.util.Set;

public record AuthUserRecord(
        Long userId,
        String email,
        String passwordHash,
        String displayName,
        String role,
        Boolean enabled,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public AuthUserPrincipal toPrincipal() {
        return new AuthUserPrincipal(userId, email, displayName, Set.of(role == null ? "USER" : role));
    }
}
