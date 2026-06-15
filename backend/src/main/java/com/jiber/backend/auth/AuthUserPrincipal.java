package com.jiber.backend.auth;

import java.util.Set;

public record AuthUserPrincipal(
        Long userId,
        String email,
        String displayName,
        Set<String> roles
) {
}
