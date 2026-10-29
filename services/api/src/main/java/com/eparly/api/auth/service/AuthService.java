package com.eparly.api.auth.service;

import com.eparly.api.auth.config.PasswordResetProperties;
import com.eparly.api.auth.domain.EmailVerificationToken;
import com.eparly.api.auth.domain.PasswordResetToken;
import com.eparly.api.auth.dto.LoginRequest;
import com.eparly.api.auth.dto.RegisterRequest;
import com.eparly.api.auth.dto.ResetPasswordRequest;
import com.eparly.api.auth.dto.TokenResponse;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.repository.EmailVerificationTokenRepository;
import com.eparly.api.auth.repository.PasswordResetTokenRepository;
import com.eparly.api.user.domain.ConsentEvent;
import com.eparly.api.user.domain.ConsentStatus;
import com.eparly.api.user.domain.ConsentType;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserSettings;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.ConsentEventRepository;
import com.eparly.api.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final ConsentEventRepository consentEventRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final VerificationEmailSender emailSender;
    private final PasswordResetEmailSender resetEmailSender;
    private final PasswordResetProperties passwordResetProperties;
    private final RateLimiter rateLimiter;
    private final Duration verificationTokenTtl;
    private final String termsVersion;
    private final String marketingNoticeVersion;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            ConsentEventRepository consentEventRepository,
            EmailVerificationTokenRepository verificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            VerificationEmailSender emailSender,
            PasswordResetEmailSender resetEmailSender,
            PasswordResetProperties passwordResetProperties,
            RateLimiter rateLimiter,
            @Value("${eparly.security.email-verification-ttl:PT24H}") Duration verificationTokenTtl,
            @Value("${eparly.legal.terms-version:v1}") String termsVersion,
            @Value("${eparly.legal.marketing-notice-version:v1}") String marketingNoticeVersion
    ) {
        this.userRepository = userRepository;
        this.consentEventRepository = consentEventRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailSender = emailSender;
        this.resetEmailSender = resetEmailSender;
        this.passwordResetProperties = passwordResetProperties;
        this.rateLimiter = rateLimiter;
        this.verificationTokenTtl = verificationTokenTtl;
        this.termsVersion = termsVersion;
        this.marketingNoticeVersion = marketingNoticeVersion;
        if (verificationTokenTtl.isZero() || verificationTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Email verification token duration must be positive");
        }
        this.clock = Clock.systemUTC();
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public void register(RegisterRequest request) {
        register(request, "127.0.0.1");
    }

    @Transactional
    public void register(RegisterRequest request, String clientIp) {
        rateLimiter.checkRegister(clientIp);

        if (!request.termsAccepted()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Terms acceptance is required");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        User user = new User(email, passwordEncoder.encode(request.password()));
        user.setSettings(new UserSettings(user));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        consentEventRepository.save(new ConsentEvent(
                user, ConsentType.TERMS_OF_SERVICE, termsVersion, ConsentStatus.GRANTED
        ));
        if (request.marketingConsent()) {
            consentEventRepository.save(new ConsentEvent(
                    user, ConsentType.MARKETING, marketingNoticeVersion, ConsentStatus.GRANTED
            ));
        }
        issueVerificationToken(user, false);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = verificationTokenRepository
                .findByTokenHashForUpdate(hashToken(rawToken))
                .filter(value -> !value.isUsed() && value.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Verification token is invalid or expired"));
        User user = token.getUser();
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verification token is invalid or expired");
        }
        Instant now = clock.instant();
        token.markUsed(now);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(now);
        verificationTokenRepository.save(token);
        userRepository.save(user);
    }

    public void resendVerification(String rawEmail) {
        resendVerification(rawEmail, "127.0.0.1");
    }

    @Transactional
    public void resendVerification(String rawEmail, String clientIp) {
        String email = normalizeEmail(rawEmail);
        rateLimiter.checkResendVerification(clientIp, email);

        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(user -> issueVerificationToken(user, true));
    }

    public TokenResponse login(LoginRequest request) {
        return login(request, "127.0.0.1");
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        rateLimiter.checkPreLogin(clientIp, email);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (!passwordMatches || user == null || user.getStatus() != UserStatus.ACTIVE) {
            rateLimiter.recordLoginFailure(clientIp, email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }

        rateLimiter.recordLoginSuccess(email);
        return refreshTokenService.createSession(user);
    }

    public TokenResponse refresh(String rawRefreshToken) {
        return refresh(rawRefreshToken, "127.0.0.1");
    }

    public TokenResponse refresh(String rawRefreshToken, String clientIp) {
        rateLimiter.checkRefresh(clientIp);
        return refreshTokenService.rotate(rawRefreshToken)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"));
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.logout(rawRefreshToken);
    }

    public void forgotPassword(String rawEmail) {
        forgotPassword(rawEmail, "127.0.0.1");
    }

    @Transactional
    public void forgotPassword(String rawEmail, String clientIp) {
        String email = normalizeEmail(rawEmail);
        rateLimiter.checkForgotPassword(clientIp, email);

        Optional<User> userOptional = userRepository.findByEmailIgnoreCase(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getStatus() == UserStatus.ACTIVE) {
                passwordResetTokenRepository.deleteByUser_IdAndUsedAtIsNull(user.getId());
                byte[] random = new byte[TOKEN_BYTES];
                secureRandom.nextBytes(random);
                String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                PasswordResetToken token = new PasswordResetToken(
                        user, hashToken(rawToken), clock.instant().plus(passwordResetProperties.ttl())
                );
                passwordResetTokenRepository.save(token);
                try {
                    resetEmailSender.sendResetToken(user.getEmail(), rawToken);
                } catch (MailException exception) {
                    LOGGER.warn("Password reset email delivery failed; continuing neutrally");
                }
            }
        } else {
            // Neutralité temporelle pour éviter l'énumération par analyse des temps de réponse
            byte[] dummy = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(dummy);
            hashToken(Base64.getUrlEncoder().withoutPadding().encodeToString(dummy));
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        resetPassword(request, "127.0.0.1");
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String clientIp) {
        rateLimiter.checkResetPassword(clientIp);

        String tokenHash = hashToken(request.token());
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .filter(value -> !value.isUsed() && value.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Password reset token is invalid or expired"));

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password reset token is invalid or expired");
        }

        Instant now = clock.instant();
        token.markUsed(now);
        passwordResetTokenRepository.saveAndFlush(token);

        // Invalider les autres jetons de récupération du compte
        passwordResetTokenRepository.invalidateOtherTokensForUser(user.getId(), token.getId(), now);

        // Mettre à jour le mot de passe avec Argon2id et invalider immédiatement les anciens JWT
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenInvalidBefore(now);
        user.incrementTokenVersion();
        userRepository.saveAndFlush(user);

        // Révoquer toutes les familles de refresh tokens de l'utilisateur
        refreshTokenService.revokeAllUserSessions(user.getId());

        // Envoyer un email de confirmation (sans mot de passe)
        try {
            resetEmailSender.sendResetConfirmation(user.getEmail());
        } catch (MailException exception) {
            LOGGER.warn("Password reset confirmation email delivery failed");
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void issueVerificationToken(User user, boolean neutralOnDeliveryFailure) {
        verificationTokenRepository.deleteByUser_IdAndUsedAtIsNull(user.getId());
        int code = secureRandom.nextInt(1_000_000);
        String rawToken = String.format("%06d", code);
        EmailVerificationToken token = new EmailVerificationToken(
                user, hashToken(rawToken), clock.instant().plus(verificationTokenTtl)
        );
        verificationTokenRepository.save(token);
        try {
            emailSender.send(user.getEmail(), rawToken);
        } catch (MailException exception) {
            if (neutralOnDeliveryFailure) {
                LOGGER.warn("Verification email resend failed; transaction rolled back");
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return;
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Verification email could not be sent");
        }
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
