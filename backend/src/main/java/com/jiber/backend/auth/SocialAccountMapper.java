package com.jiber.backend.auth;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SocialAccountMapper {

    int insert(SocialAccountInsertCommand command);

    SocialAccountRecord findByProvider(
            @Param("oauthProvider") String oauthProvider,
            @Param("providerUserId") String providerUserId
    );

    AuthUserRecord findLinkedUserByProvider(
            @Param("oauthProvider") String oauthProvider,
            @Param("providerUserId") String providerUserId
    );

    List<SocialAccountRecord> findByUserId(@Param("userId") Long userId);

    int updateLastLoginAt(
            @Param("oauthProvider") String oauthProvider,
            @Param("providerUserId") String providerUserId,
            @Param("lastLoginAt") OffsetDateTime lastLoginAt
    );
}
