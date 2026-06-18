package com.jiber.backend.auth;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import com.jiber.backend.common.error.ErrorDetail;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SocialLoginService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthUserMapper authUserMapper;
    private final SocialAccountMapper socialAccountMapper;
    private final PendingSocialSessionService pendingSocialSessionService;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    @Autowired
    public SocialLoginService(
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            AuthUserMapper authUserMapper,
            SocialAccountMapper socialAccountMapper,
            PendingSocialSessionService pendingSocialSessionService,
            PasswordEncoder passwordEncoder,
            EmailNormalizer emailNormalizer,
            PasswordPolicy passwordPolicy
    ) {
        this(
                jwtTokenService,
                refreshTokenService,
                authUserMapper,
                socialAccountMapper,
                pendingSocialSessionService,
                passwordEncoder,
                emailNormalizer,
                passwordPolicy,
                Clock.systemUTC()
        );
    }

    public SocialLoginService(
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            AuthUserMapper authUserMapper,
            SocialAccountMapper socialAccountMapper,
            PendingSocialSessionService pendingSocialSessionService
    ) {
        this(
                jwtTokenService,
                refreshTokenService,
                authUserMapper,
                socialAccountMapper,
                pendingSocialSessionService,
                new BCryptPasswordEncoder(),
                new EmailNormalizer(),
                new PasswordPolicy(),
                Clock.systemUTC()
        );
    }

    static SocialLoginService forTesting(
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            AuthUserMapper authUserMapper,
            SocialAccountMapper socialAccountMapper,
            PendingSocialSessionService pendingSocialSessionService,
            PasswordEncoder passwordEncoder,
            EmailNormalizer emailNormalizer,
            PasswordPolicy passwordPolicy,
            Clock clock
    ) {
        return new SocialLoginService(
                jwtTokenService,
                refreshTokenService,
                authUserMapper,
                socialAccountMapper,
                pendingSocialSessionService,
                passwordEncoder,
                emailNormalizer,
                passwordPolicy,
                clock
        );
    }

    private SocialLoginService(
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            AuthUserMapper authUserMapper,
            SocialAccountMapper socialAccountMapper,
            PendingSocialSessionService pendingSocialSessionService,
            PasswordEncoder passwordEncoder,
            EmailNormalizer emailNormalizer,
            PasswordPolicy passwordPolicy,
            Clock clock
    ) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.authUserMapper = authUserMapper;
        this.socialAccountMapper = socialAccountMapper;
        this.pendingSocialSessionService = pendingSocialSessionService;
        this.passwordEncoder = passwordEncoder;
        this.emailNormalizer = emailNormalizer;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    public SocialLoginResult handleOAuthSuccess(OAuth2ProviderUser providerUser, RefreshRequestContext context) {
        validateProviderUser(providerUser);
        var linkedUser = socialAccountMapper.findLinkedUserByProvider(providerUser.provider().name(), providerUser.providerUserId());
        if (linkedUser != null) {
            if (Boolean.FALSE.equals(linkedUser.enabled())) {
                throw authRequired();
            }
            var now = OffsetDateTime.now(clock);
            socialAccountMapper.updateLastLoginAt(providerUser.provider().name(), providerUser.providerUserId(), now);
            authUserMapper.updateLastLoginAt(linkedUser.userId(), now);
            return SocialLoginResult.linked(startSession(linkedUser, context));
        }
        return SocialLoginResult.pending(pendingSocialSessionService.issue(providerUser));
    }

    public SocialPendingResponse pending(String pendingToken) {
        var session = pendingSocialSessionService.requireActive(pendingToken);
        var email = session.suggestedEmail();
        var matchingEmailAccountExists = StringUtils.hasText(email) && authUserMapper.findByEmail(email) != null;
        return new SocialPendingResponse(
                session.oauthProvider(),
                email,
                session.providerDisplayName(),
                matchingEmailAccountExists
        );
    }

    @Transactional
    public AuthRefreshResult socialSignup(String pendingToken, SocialSignupRequest request, RefreshRequestContext context) {
        var session = pendingSocialSessionService.requireActive(pendingToken);
        if (socialAccountMapper.findByProvider(session.oauthProvider(), session.providerUserId()) != null) {
            throw socialAccountAlreadyLinked();
        }

        var normalizedEmail = emailNormalizer.normalize(request.email());
        validateSignupEmail(normalizedEmail);
        passwordPolicy.validate(request.password());
        if (authUserMapper.findByEmail(normalizedEmail) != null) {
            throw emailAlreadyExists();
        }

        var now = OffsetDateTime.now(clock);
        try {
            authUserMapper.insertEmailUser(
                    normalizedEmail,
                    passwordEncoder.encode(request.password()),
                    request.displayName().trim(),
                    "USER",
                    true,
                    now
            );
        } catch (DuplicateKeyException exception) {
            throw emailAlreadyExists();
        }

        var user = authUserMapper.findByEmail(normalizedEmail);
        if (user == null || Boolean.FALSE.equals(user.enabled())) {
            throw authRequired();
        }

        try {
            socialAccountMapper.insert(new SocialAccountInsertCommand(
                    user.userId(),
                    session.oauthProvider(),
                    session.providerUserId(),
                    session.providerEmail(),
                    session.providerDisplayName(),
                    now,
                    now
            ));
        } catch (DuplicateKeyException exception) {
            throw socialAccountAlreadyLinked();
        }

        pendingSocialSessionService.consume(pendingToken);
        return startSession(user, context);
    }

    @Transactional
    public AuthRefreshResult socialLink(String pendingToken, SocialLinkRequest request, RefreshRequestContext context) {
        var session = pendingSocialSessionService.requireActive(pendingToken);
        var user = authenticateExistingUser(request);

        if (socialAccountMapper.findByProvider(session.oauthProvider(), session.providerUserId()) != null) {
            throw socialAccountAlreadyLinked();
        }
        if (userAlreadyLinkedProvider(user.userId(), session.oauthProvider())) {
            throw socialAccountAlreadyLinked();
        }

        var now = OffsetDateTime.now(clock);
        try {
            socialAccountMapper.insert(new SocialAccountInsertCommand(
                    user.userId(),
                    session.oauthProvider(),
                    session.providerUserId(),
                    session.providerEmail(),
                    session.providerDisplayName(),
                    now,
                    now
            ));
        } catch (DuplicateKeyException exception) {
            throw socialAccountAlreadyLinked();
        }

        pendingSocialSessionService.consume(pendingToken);
        authUserMapper.updateLastLoginAt(user.userId(), now);
        var updatedUser = authUserMapper.findById(user.userId());
        return startSession(updatedUser == null ? user : updatedUser, context);
    }

    private AuthUserRecord authenticateExistingUser(SocialLinkRequest request) {
        var normalizedEmail = emailNormalizer.normalize(request.email());
        var user = StringUtils.hasText(normalizedEmail) ? authUserMapper.findByEmail(normalizedEmail) : null;
        if (user == null || Boolean.FALSE.equals(user.enabled()) || !StringUtils.hasText(user.passwordHash())) {
            throw invalidCredentials();
        }
        if (!matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }
        return user;
    }

    private boolean userAlreadyLinkedProvider(Long userId, String oauthProvider) {
        return socialAccountMapper.findByUserId(userId).stream()
                .anyMatch(account -> account.oauthProvider().equals(oauthProvider));
    }

    private boolean matches(String candidate, String passwordHash) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        try {
            return passwordEncoder.matches(candidate, passwordHash);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private AuthRefreshResult startSession(AuthUserRecord user, RefreshRequestContext context) {
        var principal = user.toPrincipal();
        var refreshToken = refreshTokenService.issue(user.userId(), context);
        var accessToken = jwtTokenService.issueAccessToken(principal);
        return new AuthRefreshResult(AuthTokenResponse.of(accessToken, principal), refreshToken.token());
    }

    private void validateProviderUser(OAuth2ProviderUser providerUser) {
        if (providerUser == null || providerUser.provider() == null || !StringUtils.hasText(providerUser.providerUserId())) {
            throw authRequired();
        }
    }

    private void validateSignupEmail(String normalizedEmail) {
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    ErrorCode.VALIDATION_FAILED.defaultMessage(),
                    List.of(new ErrorDetail("email", "이메일 형식이 올바르지 않습니다."))
            );
        }
    }

    private ApiException authRequired() {
        return new ApiException(ErrorCode.AUTH_REQUIRED, ErrorCode.AUTH_REQUIRED.defaultMessage(), List.of());
    }

    private ApiException emailAlreadyExists() {
        return new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, ErrorCode.EMAIL_ALREADY_EXISTS.defaultMessage(), List.of());
    }

    private ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.defaultMessage(), List.of());
    }

    private ApiException socialAccountAlreadyLinked() {
        return new ApiException(
                ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED,
                ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED.defaultMessage(),
                List.of()
        );
    }
}
