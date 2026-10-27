package com.eparly.api;

import com.eparly.api.user.domain.ConsentEvent;
import com.eparly.api.user.domain.ConsentStatus;
import com.eparly.api.user.domain.ConsentType;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserSettings;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.ConsentEventRepository;
import com.eparly.api.user.repository.UserRepository;
import com.eparly.api.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class UserDomainIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private ConsentEventRepository consentEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("1. Création d'un utilisateur et vérification des champs d'audit")
    void shouldCreateUserSuccessfully() {
        User user = new User("jean.dupont@eparly.fr", "argon2id$v=19$m=65536,t=3,p=4$hashedSecret123");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());

        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("2. Unicité de l'email sans distinction de casse (LOWER index)")
    void shouldEnforceCaseInsensitiveEmailUniqueness() {
        String baseEmail = "unique." + UUID.randomUUID() + "@eparly.fr";
        User user1 = new User(baseEmail.toLowerCase(), "hash1");
        userRepository.saveAndFlush(user1);

        // Recherche insensible à la casse
        Optional<User> found = userRepository.findByEmailIgnoreCase(baseEmail.toUpperCase());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user1.getId());
        assertThat(userRepository.existsByEmailIgnoreCase(baseEmail.toUpperCase())).isTrue();

        // Tentative d'insertion d'un doublon avec une casse différente
        User duplicateUser = new User(baseEmail.toUpperCase(), "hash2");
        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("3. Relation 1-to-1 entre User et UserSettings")
    void shouldManageUserAndSettingsRelationship() {
        User user = new User("settings." + UUID.randomUUID() + "@eparly.fr", "hashSettings");
        UserSettings settings = new UserSettings(user);
        settings.setCurrency("EUR");
        settings.setCountry("FR");
        settings.setTimezone("Europe/Paris");
        settings.setMonthlySavingsTargetCents(35000L); // 350,00 EUR

        user.setSettings(settings);
        User savedUser = userRepository.saveAndFlush(user);

        Optional<UserSettings> retrievedSettings = userSettingsRepository.findByUserId(savedUser.getId());
        assertThat(retrievedSettings).isPresent();
        assertThat(retrievedSettings.get().getCurrency()).isEqualTo("EUR");
        assertThat(retrievedSettings.get().getCountry()).isEqualTo("FR");
        assertThat(retrievedSettings.get().getTimezone()).isEqualTo("Europe/Paris");
        assertThat(retrievedSettings.get().getMonthlySavingsTargetCents()).isEqualTo(35000L);
    }

    @Test
    @DisplayName("4. Persistance de l'objectif d'épargne et contrainte CHECK >= 0")
    void shouldPersistSavingsTargetAndEnforceNonNegativeCheck() {
        User user = new User("savings." + UUID.randomUUID() + "@eparly.fr", "hashSavings");
        UserSettings settings = new UserSettings(user);
        settings.setMonthlySavingsTargetCents(120000L); // 1 200,00 EUR
        user.setSettings(settings);
        User savedUser = userRepository.saveAndFlush(user);

        UserSettings retrieved = userSettingsRepository.findByUserId(savedUser.getId()).orElseThrow();
        assertThat(retrieved.getMonthlySavingsTargetCents()).isEqualTo(120000L);

        // Tentative d'insertion d'un montant négatif direct en SQL pour vérifier la contrainte CHECK PostgreSQL
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE user_settings SET monthly_savings_target_cents = -500 WHERE user_id = ?",
                savedUser.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("5. Enregistrement et audit des événements de consentement (RGPD)")
    void shouldRecordConsentEventsHistory() {
        User user = new User("consent." + UUID.randomUUID() + "@eparly.fr", "hashConsent");
        User savedUser = userRepository.saveAndFlush(user);

        ConsentEvent event1 = new ConsentEvent(savedUser, ConsentType.TERMS_OF_SERVICE, "2026-v1.0", ConsentStatus.GRANTED);
        event1.setIpAddress("127.0.0.1");
        event1.setUserAgent("EparlyMobileApp/1.0.0 iOS");
        consentEventRepository.saveAndFlush(event1);

        ConsentEvent event2 = new ConsentEvent(savedUser, ConsentType.MARKETING, "2026-v1.0", ConsentStatus.GRANTED);
        consentEventRepository.saveAndFlush(event2);

        ConsentEvent event3 = new ConsentEvent(savedUser, ConsentType.MARKETING, "2026-v1.0", ConsentStatus.REVOKED);
        consentEventRepository.saveAndFlush(event3);

        List<ConsentEvent> history = consentEventRepository.findByUserIdOrderByCreatedAtDesc(savedUser.getId());
        assertThat(history).hasSize(3);

        List<ConsentEvent> marketingHistory = consentEventRepository
                .findByUserIdAndConsentTypeOrderByCreatedAtDesc(savedUser.getId(), ConsentType.MARKETING);
        assertThat(marketingHistory).hasSize(2);
        assertThat(marketingHistory.get(0).getStatus()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(marketingHistory.get(1).getStatus()).isEqualTo(ConsentStatus.GRANTED);
    }

    @Test
    @DisplayName("6. Respect des contraintes de clés étrangères (ON DELETE RESTRICT)")
    void shouldEnforceForeignKeyConstraintsAndRestrictPolicy() {
        UUID nonExistentUserId = UUID.randomUUID();

        // Tentative d'insertion d'un événement de consentement pour un utilisateur inexistant
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO consent_events (id, user_id, consent_type, information_version, status, created_at) " +
                        "VALUES (gen_random_uuid(), ?, 'TERMS_OF_SERVICE', '1.0', 'GRANTED', CURRENT_TIMESTAMP)",
                nonExistentUserId
        )).isInstanceOf(DataIntegrityViolationException.class);

        // Tentative de suppression d'un utilisateur ayant des dépendances
        User user = new User("restrict." + UUID.randomUUID() + "@eparly.fr", "hashRestrict");
        User savedUser = userRepository.saveAndFlush(user);

        ConsentEvent event = new ConsentEvent(savedUser, ConsentType.TERMS_OF_SERVICE, "1.0", ConsentStatus.GRANTED);
        consentEventRepository.saveAndFlush(event);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                savedUser.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("7 & 8. Vérification que V1 et V2 sont appliquées et validées par Hibernate")
    void shouldVerifyMigrationsAndHistory() {
        Integer v1Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class
        );
        Integer v2Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
                Integer.class
        );

        assertThat(v1Count).isNotNull().isEqualTo(1);
        assertThat(v2Count).isNotNull().isEqualTo(1);
    }
}
