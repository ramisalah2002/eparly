package com.eparly.api.catalogsync.domain;

/**
 * Nature du prix observe. Un prix inconnu reste MISSING et n'est jamais ramene a zero ;
 * la periodicite (mensuelle, annuelle...) est portee separement par le billing period.
 */
public enum CatalogPriceKind {
    MISSING,
    FREE,
    FIXED,
    PROMOTIONAL,
    /** Le cout reel depend de l'usage (prix du kWh par exemple). */
    VARIABLE,
    /** Le prix depend du profil du client (devis, age, adresse...). */
    PROFILE_DEPENDENT
}
