package com.eparly.api.imports.mapper;

import java.util.List;
import java.util.Optional;

/**
 * Point d'extension : un profil reconnait une structure d'en-tetes et propose un mapping.
 * Les profils sont des beans Spring ordonnes (@Order) ; le premier mapping complet l'emporte.
 * Un futur profil specifique a une banque implemente simplement cette interface, sans toucher au reste du moteur.
 */
public interface BankProfile {

    /**
     * @param headers en-tetes du fichier, tels que lus
     * @return un mapping (eventuellement partiel) ou vide si le profil ne reconnait pas ces en-tetes
     */
    Optional<ImportMapping> match(List<String> headers);
}
