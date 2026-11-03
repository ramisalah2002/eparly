package com.eparly.api.auth.service;

import com.eparly.api.auth.domain.GoogleChallengePurpose;
import com.eparly.api.auth.domain.UserOAuthIdentity;
import com.eparly.api.auth.dto.GoogleChallengeResponse;
import com.eparly.api.auth.dto.GoogleLinkRequest;
import com.eparly.api.auth.dto.GoogleLinkResponse;
import com.eparly.api.auth.dto.GoogleLoginRequest;
import com.eparly.api.auth.dto.TokenResponse;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.repository.UserOAuthIdentityRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Connexion et liaison Google. La verification se fait hors transaction (acces reseau possible) ; la session
 * est emise par le meme RefreshTokenService que la connexion par mot de passe. Chaque tentative exige un challenge
 * serveur dont le nonce figure dans le jeton signe ; il est consomme dans la transaction du compte.
 */
@Service
public class GoogleAuthService {
    public static final String LINK_REQUIRED = "GOOGLE_ACCOUNT_LINK_REQUIRED";
    public static final String CONSENT_REQUIRED = "GOOGLE_CONSENT_REQUIRED";
    private static final int MAX_NAME_LENGTH = 100;

    private final GoogleIdTokenVerifier verifier;
    private final GoogleChallengeService challenges;
    private final UserOAuthIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final ConsentEventRepository consentEventRepository;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final String termsVersion;
    private final String marketingNoticeVersion;

    public GoogleAuthService(GoogleIdTokenVerifier verifier, GoogleChallengeService challenges,
                             UserOAuthIdentityRepository identityRepository,
                             UserRepository userRepository, ConsentEventRepository consentEventRepository,
                             RefreshTokenService refreshTokenService, RateLimiter rateLimiter,
                             PasswordEncoder passwordEncoder,
                             PlatformTransactionManager transactionManager,
                             @Value("${eparly.legal.terms-version:v1}") String termsVersion,
                             @Value("${eparly.legal.marketing-notice-version:v1}") String marketingNoticeVersion) {
        this.verifier = verifier;
        this.challenges = challenges;
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
        this.consentEventRepository = consentEventRepository;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
        this.termsVersion = termsVersion;
        this.marketingNoticeVersion = marketingNoticeVersion;
    }

    public GoogleChallengeResponse createChallenge(GoogleChallengePurpose purpose, UUID userId, String clientIp) {
        rateLimiter.checkGoogleChallenge(clientIp);
        verifier.requireAvailable();
        if (purpose == GoogleChallengePurpose.LINK && userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return challenges.create(purpose, purpose == GoogleChallengePurpose.LINK ? userId : null);
    }

    public TokenResponse login(GoogleLoginRequest request, String clientIp) {
        rateLimiter.checkGoogleAuth(clientIp);
        GoogleIdTokenVerifier.GoogleIdentity identity =
                verifiedIdentity(request.idToken(), request.challengeId(), GoogleChallengePurpose.LOGIN, null);
        User user;
        try {
            user = transaction.execute(status -> {
                challenges.consume(request.challengeId(), GoogleChallengePurpose.LOGIN, null);
                return findOrCreate(identity, request);
            });
        } catch (DataIntegrityViolationException concurrentCreation) {
            // Une requete concurrente a cree le meme compte ; l'echec a aussi annule la consommation du challenge.
            user = transaction.execute(status -> {
                challenges.consume(request.challengeId(), GoogleChallengePurpose.LOGIN, null);
                User linked = findLinked(identity);
                if (linked == null) throw new ApiException(HttpStatus.CONFLICT, LINK_REQUIRED);
                return linked;
            });
        }
        return refreshTokenService.createSession(user);
    }

    public GoogleLinkResponse link(UUID userId, GoogleLinkRequest request, String clientIp) {
        rateLimiter.checkGoogleAuth(clientIp);
        GoogleIdTokenVerifier.GoogleIdentity identity =
                verifiedIdentity(request.idToken(), request.challengeId(), GoogleChallengePurpose.LINK, userId);
        requireRecentProof(userId, request.currentPassword(), clientIp);
        try {
            return transaction.execute(status -> {
                challenges.consume(request.challengeId(), GoogleChallengePurpose.LINK, userId);
                return linkInTransaction(userId, identity);
            });
        } catch (DataIntegrityViolationException concurrentLink) {
            throw new ApiException(HttpStatus.CONFLICT, "This Google account cannot be linked");
        }
    }

    private GoogleIdTokenVerifier.GoogleIdentity verifiedIdentity(String idToken, UUID challengeId,
                                                                  GoogleChallengePurpose purpose, UUID userId) {
        verifier.requireAvailable();
        String expectedHash = challenges.requireUsable(challengeId, purpose, userId);
        GoogleIdTokenVerifier.GoogleIdentity identity = verifier.verify(idToken);
        challenges.requireNonce(expectedHash, identity.nonce());
        return identity;
    }

    /** Un JWT peut provenir d'un simple refresh : il ne prouve pas a lui seul un controle recent du compte. */
    private void requireRecentProof(UUID userId, String currentPassword, String clientIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
        if (user.getPasswordHash() == null) return;
        String email = AuthService.normalizeEmail(user.getEmail());
        rateLimiter.checkPreLogin(clientIp, email);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            rateLimiter.recordLoginFailure(clientIp, email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is required and must be valid");
        }
        rateLimiter.recordLoginSuccess(email);
    }

    private User findOrCreate(GoogleIdTokenVerifier.GoogleIdentity identity, GoogleLoginRequest request) {
        User linked = findLinked(identity);
        if (linked != null) return linked;

        String email = AuthService.normalizeEmail(identity.email());
        // Jamais de fusion sur la seule egalite d'email, y compris pour un compte non verifie.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Une requete concurrente pour le meme sub a pu valider entre les deux lectures.
            User justLinked = findLinked(identity);
            if (justLinked != null) return justLinked;
            throw new ApiException(HttpStatus.CONFLICT, LINK_REQUIRED);
        }
        // Rien n'est stocke avant le consentement : le client renvoie un ID Token avec termsAccepted=true.
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, CONSENT_REQUIRED);
        }
        User user = new User(email, null);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        if (identity.name() != null && !identity.name().isBlank()) {
            String name = identity.name().trim();
            user.setFullName(name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
        }
        user.setSettings(new UserSettings(user));
        userRepository.saveAndFlush(user);
        identityRepository.saveAndFlush(new UserOAuthIdentity(user, UserOAuthIdentity.GOOGLE,
                identity.subject(), email));
        consentEventRepository.save(new ConsentEvent(user, ConsentType.TERMS_OF_SERVICE, termsVersion,
                ConsentStatus.GRANTED));
        if (Boolean.TRUE.equals(request.marketingConsent())) {
            consentEventRepository.save(new ConsentEvent(user, ConsentType.MARKETING, marketingNoticeVersion,
                    ConsentStatus.GRANTED));
        }
        return user;
    }

    private User findLinked(GoogleIdTokenVerifier.GoogleIdentity identity) {
        return identityRepository.findByProviderAndSubject(UserOAuthIdentity.GOOGLE, identity.subject())
                .map(existing -> requireActive(existing.getUser()))
                .orElse(null);
    }

    private GoogleLinkResponse linkInTransaction(UUID userId, GoogleIdTokenVerifier.GoogleIdentity identity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
        requireActive(user);
        var existing = identityRepository.findByProviderAndSubject(UserOAuthIdentity.GOOGLE, identity.subject());
        if (existing.isPresent()) {
            if (!existing.get().getUser().getId().equals(userId)) {
                throw new ApiException(HttpStatus.CONFLICT, "This Google account cannot be linked");
            }
            return new GoogleLinkResponse(UserOAuthIdentity.GOOGLE, existing.get().getEmailAtLinkTime(), true);
        }
        if (identityRepository.existsByUser_IdAndProvider(userId, UserOAuthIdentity.GOOGLE)) {
            throw new ApiException(HttpStatus.CONFLICT, "This account is already linked to another Google account");
        }
        String email = AuthService.normalizeEmail(identity.email());
        identityRepository.saveAndFlush(new UserOAuthIdentity(user, UserOAuthIdentity.GOOGLE,
                identity.subject(), email));
        return new GoogleLinkResponse(UserOAuthIdentity.GOOGLE, email, false);
    }

    private static User requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is not available");
        }
        return user;
    }
}
