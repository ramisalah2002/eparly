package com.eparly.api.user.repository;

import com.eparly.api.user.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour la gestion des préférences utilisateurs.
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    /**
     * Recherche les préférences par identifiant utilisateur.
     */
    Optional<UserSettings> findByUserId(UUID userId);
}
