package com.geosun.tms.auth.service;

import com.geosun.tms.auth.config.AppEmailProperties;
import com.geosun.tms.auth.domain.EmailNormalizer;
import com.geosun.tms.auth.domain.token.EmailVerificationToken;
import com.geosun.tms.auth.domain.token.RefreshToken;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.mapper.UserDtoMapper;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.dto.request.ResendVerificationRequest;
import com.geosun.tms.auth.dto.request.VerifyEmailRequest;
import com.geosun.tms.auth.dto.response.AuthTokensResponse;
import com.geosun.tms.auth.dto.response.LogoutResponse;
import com.geosun.tms.auth.dto.response.OperationSuccessResponse;
import com.geosun.tms.auth.dto.response.RegisterResponse;
import com.geosun.tms.auth.dto.response.UserPublicDto;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.mail.VerificationMailSender;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.repository.EmailVerificationTokenRepository;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.auth.security.config.JwtProperties;
import com.geosun.tms.auth.security.crypto.OpaqueTokenGenerator;
import com.geosun.tms.auth.security.crypto.TokenHasher;
import com.geosun.tms.auth.security.jwt.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Реєстрація, вхід, верифікація email, refresh/logout та профіль.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AppEmailProperties appEmailProperties;
    private final VerificationMailSender verificationMailSender;
    private final RateLimitService rateLimitService;

    public AuthService(UserRepository userRepository,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       AppEmailProperties appEmailProperties,
                       VerificationMailSender verificationMailSender,
                       RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.appEmailProperties = appEmailProperties;
        this.verificationMailSender = verificationMailSender;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, String clientIp) {
        rateLimitService.checkRegister(clientIp);
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailAndDeletedFalse(email)) {
            throw ApiException.conflict("Email is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setEmailVerified(false);
        userRepository.save(user);

        String rawVerification = OpaqueTokenGenerator.generate();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(TokenHasher.sha256Hex(rawVerification));
        token.setExpiresAt(Instant.now().plusSeconds(appEmailProperties.getVerificationExpiresSeconds()));
        emailVerificationTokenRepository.save(token);

        try {
            verificationMailSender.sendVerificationEmail(email, rawVerification);
        } catch (MailException ex) {
            log.error("Failed to send verification email after registration");
        }

        return UserDtoMapper.toRegisterResponse(user);
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request, String clientIp) {
        String email = EmailNormalizer.normalize(request.email());
        rateLimitService.checkLoginNotBlocked(clientIp, email);

        User user = userRepository.findTopByEmailOrderByDeletedAsc(email).orElse(null);
        if (user == null) {
            rateLimitService.recordLoginFailure(clientIp, email);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimitService.recordLoginFailure(clientIp, email);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");
        }

        rateLimitService.clearLoginFailures(clientIp, email);

        if (user.isDeleted()) {
            throw ApiException.forbidden("USER_DELETED", "User account has been deleted");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
        }
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Email is not verified");
        }

        return issueTokens(user);
    }

    @Transactional
    public OperationSuccessResponse verifyEmail(VerifyEmailRequest request) {
        String raw = request.token() != null ? request.token().trim() : "";
        if (raw.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Token is required");
        }
        String hash = TokenHasher.sha256Hex(raw);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "Invalid or expired verification token"));

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("INVALID_TOKEN", "Invalid or expired verification token");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        token.setUsedAt(Instant.now());
        userRepository.save(user);
        emailVerificationTokenRepository.save(token);

        return new OperationSuccessResponse(true, "Email verified successfully");
    }

    @Transactional
    public OperationSuccessResponse resendVerification(ResendVerificationRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        rateLimitService.checkResend(email);

        User user = userRepository.findByEmailAndDeletedFalse(email).orElse(null);
        if (user == null || user.isEmailVerified()) {
            return new OperationSuccessResponse(true, "Verification email sent");
        }

        emailVerificationTokenRepository.deletePendingByUserId(user.getId());
        emailVerificationTokenRepository.flush();

        String raw = OpaqueTokenGenerator.generate();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plusSeconds(appEmailProperties.getVerificationExpiresSeconds()));
        emailVerificationTokenRepository.save(token);

        try {
            verificationMailSender.sendVerificationEmail(email, raw);
        } catch (MailException ex) {
            log.error("Failed to resend verification email");
            throw ApiException.serviceUnavailable("EMAIL_DELIVERY_FAILED", "Email delivery failed");
        }

        return new OperationSuccessResponse(true, "Verification email sent");
    }

    @Transactional
    public AuthTokensResponse refresh(RefreshRequest request, String clientIp) {
        rateLimitService.checkRefresh(clientIp);
        String raw = request.refreshToken() != null ? request.refreshToken().trim() : "";
        if (raw.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Refresh token is required");
        }
        String hash = TokenHasher.sha256Hex(raw);
        RefreshToken current = refreshTokenRepository.findByTokenHashWithUser(hash)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid refresh token"));

        User user = current.getUser();

        if (current.getRevokedAt() != null) {
            log.warn("Possible refresh token reuse for user {}", user.getId());
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
            throw ApiException.unauthorized("INVALID_SESSION", "Invalid refresh token");
        }

        if (!current.getExpiresAt().isAfter(Instant.now())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid refresh token");
        }

        if (user.isDeleted()) {
            throw ApiException.forbidden("USER_DELETED", "User account has been deleted");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
        }

        String newRaw = OpaqueTokenGenerator.generate();
        RefreshToken next = new RefreshToken();
        next.setUser(user);
        next.setTokenHash(TokenHasher.sha256Hex(newRaw));
        next.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshExpiresSeconds()));
        refreshTokenRepository.save(next);
        refreshTokenRepository.flush();

        current.setRevokedAt(Instant.now());
        current.setReplacedBy(next);
        refreshTokenRepository.save(current);

        String access = jwtService.createAccessToken(user.getId(), next.getId());
        return new AuthTokensResponse(
                access,
                newRaw,
                "Bearer",
                jwtProperties.getExpiresSeconds(),
                UserDtoMapper.toPublicDto(user)
        );
    }

    @Transactional
    public LogoutResponse logout(UserPrincipal principal) {
        RefreshToken session = refreshTokenRepository.findByIdWithUser(principal.getRefreshSessionId())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_SESSION", "Refresh session is invalid or expired"));

        if (!session.getUser().getId().equals(principal.getUserId())) {
            throw ApiException.unauthorized("INVALID_SESSION", "Refresh session is invalid or expired");
        }
        if (session.getRevokedAt() != null) {
            throw ApiException.unauthorized("INVALID_SESSION", "Refresh session is invalid or expired");
        }

        session.setRevokedAt(Instant.now());
        refreshTokenRepository.save(session);
        return new LogoutResponse(true, "Logged out successfully");
    }

    public UserPublicDto me(UserPrincipal principal) {
        return new UserPublicDto(principal.getUserId(), principal.getEmail(), principal.getRole().name());
    }

    private AuthTokensResponse issueTokens(User user) {
        String raw = OpaqueTokenGenerator.generate();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(TokenHasher.sha256Hex(raw));
        refresh.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshExpiresSeconds()));
        refreshTokenRepository.save(refresh);
        refreshTokenRepository.flush();

        String access = jwtService.createAccessToken(user.getId(), refresh.getId());
        return new AuthTokensResponse(
                access,
                raw,
                "Bearer",
                jwtProperties.getExpiresSeconds(),
                UserDtoMapper.toPublicDto(user)
        );
    }
}
