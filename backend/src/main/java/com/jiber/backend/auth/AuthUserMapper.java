package com.jiber.backend.auth;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthUserMapper {

    AuthUserRecord findById(@Param("userId") Long userId);

    AuthUserRecord findByEmail(@Param("email") String email);

    int insertEmailUser(
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("displayName") String displayName,
            @Param("role") String role,
            @Param("enabled") Boolean enabled,
            @Param("lastLoginAt") OffsetDateTime lastLoginAt
    );

    int updateLastLoginAt(
            @Param("userId") Long userId,
            @Param("lastLoginAt") OffsetDateTime lastLoginAt
    );
}
