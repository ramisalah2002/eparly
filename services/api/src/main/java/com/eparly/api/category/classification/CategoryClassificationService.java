package com.eparly.api.category.classification;

import com.eparly.api.category.domain.Category;
import com.eparly.api.category.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moteur de catégorisation automatique déterministe.
 *
 * Algorithme :
 * <ol>
 *   <li>Chargement des catégories système au démarrage (une seule fois, en cache)</li>
 *   <li>Application des règles par ordre décroissant de spécificité</li>
 *   <li>Première règle qui match retourne la catégorie</li>
 *   <li>Si aucune règle ne match, la transaction reste sans catégorie</li>
 * </ol>
 *
 * Types de règles (par ordre de priorité) :
 * <ul>
 *   <li><b>Exactes</b> (score 100) : correspondance complète du libellé normalisé</li>
 *   <li><b>Préfixe commerçant</b> (score 80) : le libellé commence par un motif connu</li>
 *   <li><b>Mots-clés</b> (score 50) : le libellé contient un mot-clé, avec blacklist pour éviter les faux positifs</li>
 * </ul>
 *
 * Aucune donnée financière n'est journalisée. Le moteur ne modifie jamais
 * une transaction dont {@code customCategoryFlag} est {@code true}.
 */
@Service
public class CategoryClassificationService {

    private static final Logger log = LoggerFactory.getLogger(CategoryClassificationService.class);

    private final Map<String, Category> categoryByName;
    private final List<CategoryRule> rules;

    public CategoryClassificationService(CategoryRepository categoryRepository) {
        this.categoryByName = loadSystemCategories(categoryRepository);
        this.rules = buildRules(this.categoryByName);
    }

    /**
     * Classifie une transaction à partir de son libellé normalisé.
     *
     * @param normalizedLabel le libellé normalisé (majuscules, sans accents, espaces réduits)
     * @return la catégorie suggérée, ou {@link Optional#empty()} si aucune règle fiable ne s'applique
     */
    @Transactional(readOnly = true)
    public Optional<Category> classify(String normalizedLabel) {
        if (normalizedLabel == null || normalizedLabel.isBlank()) {
            return Optional.empty();
        }
        for (CategoryRule rule : this.rules) {
            Optional<Category> result = rule.classify(normalizedLabel);
            if (result.isPresent()) {
                log.debug("Classified label with rule (specificity={}): category={}",
                        rule.specificity(), result.get().getName());
                return result;
            }
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Chargement des catégories système
    // -------------------------------------------------------------------------

    private Map<String, Category> loadSystemCategories(CategoryRepository categoryRepository) {
        List<Category> systemCategories = categoryRepository.findAllBySystemFlagTrue();
        Map<String, Category> map = new LinkedHashMap<>();
        for (Category cat : systemCategories) {
            map.put(cat.getName(), cat);
        }
        log.info("Loaded {} system categories for classification", map.size());
        return map;
    }

    // -------------------------------------------------------------------------
    // Construction des règles par ordre décroissant de spécificité
    // -------------------------------------------------------------------------

    private List<CategoryRule> buildRules(Map<String, Category> categories) {
        List<CategoryRule> rules = new ArrayList<>();

        // Récupération des catégories par nom
        Category logement = categories.get("Logement");
        Category courses = categories.get("Courses");
        Category restaurants = categories.get("Restaurants");
        Category transport = categories.get("Transport");
        Category abonnements = categories.get("Abonnements");
        Category shopping = categories.get("Shopping");
        Category sante = categories.get("Santé");
        Category loisirs = categories.get("Loisirs");
        Category fraisBancaires = categories.get("Frais bancaires");
        Category revenus = categories.get("Revenus");
        Category virements = categories.get("Virements");

        // -------------------------------------------------------------------------
        // 1. Règles EXACTES (score 100) – correspondance complète
        // -------------------------------------------------------------------------

        // Virements internes
        exact(rules, "VIREMENT INTERNE", virements);

        // Revenus
        exact(rules, "SALAIRE", revenus);
        exact(rules, "SALAIRE NET", revenus);
        exact(rules, "TRAITEMENT", revenus);
        exact(rules, "REMUNERATION", revenus);

        // -------------------------------------------------------------------------
        // 2. Règles par PREFIXE (score 80) – le libellé commence par un motif
        // -------------------------------------------------------------------------

        // Logement
        prefix(rules, logement,
                "LOYER", "CHARGE LOYER", "CHARGES CO", "CHARGES COLLECTIVES",
                "EHPAD", "URBANISME");

        // Courses / Supermarchés – préfixes les plus longs d'abord pour éviter les faux positifs
        prefix(rules, courses,
                "CARREFOUR CITY", "CARREFOUR EXPRESS", "CARREFOUR",
                "E.LECLERC", "LECLERC", "MUTUELLE DU FOREZ",
                "AUCHAN MARKET", "AUCHAN",
                "MONOPRIX MARKET", "MONOPRIX",
                "CASINO",
                "INTERMARCHE CENTER", "INTERMARCHE",
                "LIDL", "PENNY MARKET",
                "BIENVENUE", "NATURLAND",
                "SPAR",
                "FRUITIER");

        // Restaurants / Fast-food – préfixes les plus longs d'abord (tous en majuscules car les libellés sont normalisés)
        prefix(rules, restaurants,
                "MCDONALD",
                "BURGER KING",
                "KFC",
                "SUBWAY",
                "DOMINO'S", "DOMINO",
                "PIZZA HUT",
                "PAPA JOHN",
                "DELIVEROO", "UBER EATS", "JUST EAT",
                "FRIANDISE", "PATISSERIE");

        // Transport – préfixes les plus longs d'abord
        prefix(rules, transport,
                "SNCF", "TER ", "OUIGO", "TGV",
                "RATP",
                "VEOLIA", "KEOLIS",
                "CARRENT", "SIXT", "EUROPCAR", "HERTZ", "ADELACAR",
                "TOTAL ENERGIES", "TOTAL ", "SHELL", "ESSO", "AGIP",
                "BP ENERGIE", "SPOTON",
                "INDIGO PARK", "PARK24",
                "PESY", "LCV", "JCDECAUX",
                "BLABLACAR");

        // Abonnements – les services récurrents sont ici en priorité
        prefix(rules, abonnements,
                "ABONNEMENT", "SUBSCRIPTION",
                "NETFLIX", "DISNEY PLUS", "AMAZON PRIME", "CANAL ",
                "SPOTIFY", "DEEZER", "APPLE MUSIC",
                "GYM", "KINE", "FITNESS",
                "ADSL", "FIBRE", "BOX", "SFR ", "ORANGE ", "BOUYGUES TELECOM", "FREE ");

        // Shopping en ligne – préfixes les plus longs d'abord
        prefix(rules, shopping,
                "AMAZON", "FNAC", "DARTY", "BOUQUIK",
                "CDISCOUNT", "C DISCOUNT",
                "ZALANDO", "ASOS", "VINTED",
                "IKEA", "CONFORAMA", "LEROY MERLIN", "BRIXIO",
                "CAMELEO", "CAMELEON");

        // Santé
        prefix(rules, sante,
                "PHARMACIE", "OPTICIEN", "OPTIC",
                "DENTISTE", "MEDECIN", "DOCTEUR",
                "LABORATOIRE", "BILAN",
                "MUTUELLE", "COMPLEMENT SANTE",
                "BOURSOCCITE", "MUR MUR");

        // Loisirs – achats ponctuels (les abonnements sont gérés ci-dessus)
        prefix(rules, loisirs,
                "CINEMA", "UGC", "GAUMONT", "MEGAGAN", "PATHE",
                "STEAM", "PLAYSTATION", "XBOX", "NINTENDO",
                "DECATHLON", "INTERSPORT");

        // Frais bancaires – préfixes les plus longs d'abord
        prefix(rules, fraisBancaires,
                "FRAIS DE GESTION", "FRAIS BANCAIRES", "FRAIS DE TENUE",
                "ABONNEMENT COMPTE",
                "COMMISSION", "FRAIS PRELEVEMENT",
                "FRAIS CHANGE", "FRAIS VIREMENT");

        // Revenus – préfixes les plus longs d'abord
        prefix(rules, revenus,
                "REMUNERATION",
                "SALAIRE", "TRAITEMENT",
                "REMBOURSEMENT IMPOT", "IMPOT REVENU",
                "ALLOC", "PRESTATION", "INDEMNITE",
                "DIVIDENDE", "INTERET");

        // Virements
        prefix(rules, virements,
                "ORDRE VIREMENT", "VIREMENT",
                "PRELEVEMENT SEPA",
                "TRANSFERT", "ENVOI ARGENT");

        // -------------------------------------------------------------------------
        // 3. Règles par MOTS-CLES (score 50) – avec blacklist
        // -------------------------------------------------------------------------

        // Courses : mots-clés génériques mais avec blacklist pour éviter les faux positifs
        keyword(rules, courses,
                List.of("SUPERMARCHE", "EPICERIE", "MARCHE", "ALIMENTAIRE"),
                List.of());

        // Restaurants
        keyword(rules, restaurants,
                List.of("TRAITEUR", "CATERING", "LIVRAISON REPAS", "REPAS"),
                List.of("PREPARATION"));

        // Transport : carburant et stationnement
        keyword(rules, transport,
                List.of("CARBURANT", "GASOIL", "ESSENCE", "STATION SERVICE", "CARTES CARBURANT"),
                List.of());

        // Shopping : e-commerce générique
        keyword(rules, shopping,
                List.of("E-COMMERCE", "ACHAT EN LIGNE"),
                List.of());

        // Loisirs : culture et divertissement
        keyword(rules, loisirs,
                List.of("THEATRE", "CONCERT", "EXPOSITION", "MUSEE", "ZOO", "AQUARIUM"),
                List.of());

        // Santé : mutuelle et pharmacie
        keyword(rules, sante,
                List.of("REMBOURSEMENT MUTUELLE", "ORDONNANCE"),
                List.of());

        return rules;
    }

    // -------------------------------------------------------------------------
    // Constructeurs de règles
    // -------------------------------------------------------------------------

    private void exact(List<CategoryRule> rules, String label, Category category) {
        if (category == null) return;
        rules.add(new ExactMatchRule(label, category));
    }

    private void prefix(List<CategoryRule> rules, Category category, String... prefixes) {
        if (category == null) return;
        for (String prefix : prefixes) {
            rules.add(new PrefixMatchRule(prefix, category));
        }
    }

    private void keyword(List<CategoryRule> rules, Category category,
                         List<String> keywords, List<String> blacklist) {
        if (category == null) return;
        rules.add(new KeywordMatchRule(keywords, blacklist, category));
    }

    // -------------------------------------------------------------------------
    // Implémentations de règles
    // -------------------------------------------------------------------------

    /** Correspondance exacte du libellé normalisé. */
    private static class ExactMatchRule implements CategoryRule {
        private final String exactLabel;
        private final Category category;

        ExactMatchRule(String exactLabel, Category category) {
            this.exactLabel = exactLabel;
            this.category = category;
        }

        @Override
        public int specificity() {
            return 100;
        }

        @Override
        public Optional<Category> classify(String normalizedLabel) {
            if (normalizedLabel.equals(this.exactLabel)) {
                return Optional.of(this.category);
            }
            return Optional.empty();
        }
    }

    /** Le libellé normalisé commence par le préfixe donné. */
    private static class PrefixMatchRule implements CategoryRule {
        private final String prefix;
        private final Category category;

        PrefixMatchRule(String prefix, Category category) {
            this.prefix = prefix;
            this.category = category;
        }

        @Override
        public int specificity() {
            return 80;
        }

        @Override
        public Optional<Category> classify(String normalizedLabel) {
            if (normalizedLabel.startsWith(this.prefix)) {
                return Optional.of(this.category);
            }
            return Optional.empty();
        }
    }

    /** Le libellé contient au moins un mot-clé et aucun terme de la blacklist. */
    private static class KeywordMatchRule implements CategoryRule {
        private final List<String> keywords;
        private final List<String> blacklist;
        private final Category category;

        KeywordMatchRule(List<String> keywords, List<String> blacklist, Category category) {
            this.keywords = keywords;
            this.blacklist = blacklist;
            this.category = category;
        }

        @Override
        public int specificity() {
            return 50;
        }

        @Override
        public Optional<Category> classify(String normalizedLabel) {
            // Vérifier qu'aucun terme de blacklist n'est présent
            for (String black : this.blacklist) {
                if (normalizedLabel.contains(black)) {
                    return Optional.empty();
                }
            }
            // Vérifier qu'au moins un mot-clé est présent
            for (String keyword : this.keywords) {
                if (normalizedLabel.contains(keyword)) {
                    return Optional.of(this.category);
                }
            }
            return Optional.empty();
        }
    }
}
