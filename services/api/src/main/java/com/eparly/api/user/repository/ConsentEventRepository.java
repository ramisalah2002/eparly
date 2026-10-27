package com.eparly.api.user.repository;

import com.eparly.api.user.domain.ConsentEvent;
import com.eparly.api.user.domain.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository pour le journal d'audit des consentements.
 */
@Repository
public interface ConsentEventRepository extends JpaRepository<ConsentEvent, UUID> {

    /**
     * Récupère l'historique complet des événements de consentement pour un utilisateur.
     */
    List<ConsentEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Récupère l'historique d'un type de consentement spécifique pour un utilisateur.
     */
    List<ConsentEvent> findByUserIdAndConsentTypeOrderByCreatedAtDesc(UUID userId, ConsentType consentType);
}
