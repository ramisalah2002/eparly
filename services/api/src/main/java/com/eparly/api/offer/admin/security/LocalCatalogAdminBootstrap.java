package com.eparly.api.offer.admin.security;

import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserSettings;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * Crée le compte administrateur du catalogue pour le développement local.
 *
 * Actif uniquement avec le profil « local » et si LOCAL_ADMIN_EMAIL et LOCAL_ADMIN_PASSWORD
 * sont définis (infra/.env). Un compte existant n'est jamais modifié : seule la permission
 * CATALOG_ADMIN lui est accordée si elle manque.
 */
@Component
@Profile("local")
public class LocalCatalogAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalCatalogAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final String email;
    private final String password;

    public LocalCatalogAdminBootstrap(UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      JdbcTemplate jdbcTemplate,
                                      @Value("${eparly.local-admin.email:}") String email,
                                      @Value("${eparly.local-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        User admin = userRepository.findByEmailIgnoreCase(email).orElseGet(this::createAdmin);
        int granted = jdbcTemplate.update("""
                INSERT INTO catalog_admin_permissions (user_id)
                SELECT ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM catalog_admin_permissions
                    WHERE user_id = ? AND revoked_at IS NULL
                )
                """, admin.getId(), admin.getId());
        if (granted > 0) {
            log.info("Permission CATALOG_ADMIN accordée au compte local {}", email);
        }
    }

    private User createAdmin() {
        User user = new User(email, passwordEncoder.encode(password));
        user.setFullName("Admin Eparly");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user.setSettings(new UserSettings(user));
        log.info("Compte administrateur local créé : {}", email);
        return userRepository.saveAndFlush(user);
    }
}
