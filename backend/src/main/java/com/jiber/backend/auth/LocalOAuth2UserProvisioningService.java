package com.jiber.backend.auth;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocalOAuth2UserProvisioningService {

    private final AuthUserMapper authUserMapper;
    private final SocialAccountMapper socialAccountMapper;
    private final Clock clock;

    @Autowired
    public LocalOAuth2UserProvisioningService(AuthUserMapper authUserMapper, SocialAccountMapper socialAccountMapper) {
        this(authUserMapper, socialAccountMapper, Clock.systemUTC());
    }

    static LocalOAuth2UserProvisioningService forTesting(
            AuthUserMapper authUserMapper,
            SocialAccountMapper socialAccountMapper,
            Clock clock
    ) {
        return new LocalOAuth2UserProvisioningService(authUserMapper, socialAccountMapper, clock);
    }

    private LocalOAuth2UserProvisioningService(
            AuthUserMapper authUserMapper,
            SocialAccountMapper socialAccountMapper,
            Clock clock
    ) {
        this.authUserMapper = authUserMapper;
        this.socialAccountMapper = socialAccountMapper;
        this.clock = clock;
    }

    public AuthUserPrincipal provision(OAuth2ProviderUser providerUser) {
        validate(providerUser);
        var user = socialAccountMapper.findLinkedUserByProvider(providerUser.provider().name(), providerUser.providerUserId());
        if (user == null || Boolean.FALSE.equals(user.enabled())) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, ErrorCode.AUTH_REQUIRED.defaultMessage(), List.of());
        }
        var now = OffsetDateTime.now(clock);
        socialAccountMapper.updateLastLoginAt(providerUser.provider().name(), providerUser.providerUserId(), now);
        authUserMapper.updateLastLoginAt(user.userId(), now);
        return user.toPrincipal();
    }

    private void validate(OAuth2ProviderUser providerUser) {
        if (providerUser == null || providerUser.provider() == null || !StringUtils.hasText(providerUser.providerUserId())) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, ErrorCode.AUTH_REQUIRED.defaultMessage(), List.of());
        }
    }
}
