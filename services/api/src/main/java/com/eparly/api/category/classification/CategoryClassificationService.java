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
     * Classifie une transaction à partir des catégories et sous-catégories fournies dans le CSV bancaire.
     */
    @Transactional(readOnly = true)
    public Optional<Category> classifyCategory(String categoryName, String subcategoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }
        String cat = categoryName.trim();
        String sub = subcategoryName != null ? subcategoryName.trim() : "";

        if (containsIgnoreCase(cat, "Loisir")) {
            if (containsIgnoreCase(sub, "Restaurant") || containsIgnoreCase(sub, "bar") || containsIgnoreCase(sub, "discotheque") || containsIgnoreCase(sub, "discothèque")) {
                return Optional.ofNullable(this.categoryByName.get("Restaurants"));
            }
            return Optional.ofNullable(this.categoryByName.get("Loisirs"));
        }
        if (containsIgnoreCase(cat, "Vie quotidienne")) {
            if (containsIgnoreCase(sub, "Alimentation")) {
                return Optional.ofNullable(this.categoryByName.get("Courses"));
            }
            if (containsIgnoreCase(sub, "Vetement") || containsIgnoreCase(sub, "Vêtement") || containsIgnoreCase(sub, "Electronique")
                    || containsIgnoreCase(sub, "Mobilier") || containsIgnoreCase(sub, "Livre")
                    || containsIgnoreCase(sub, "Equipement") || containsIgnoreCase(sub, "Bien-etre") || containsIgnoreCase(sub, "Bien-être")) {
                return Optional.ofNullable(this.categoryByName.get("Shopping"));
            }
            return Optional.ofNullable(this.categoryByName.get("Autres dépenses"));
        }
        if (containsIgnoreCase(cat, "Voyage") || containsIgnoreCase(cat, "Transport") || containsIgnoreCase(cat, "Auto")) {
            if (containsIgnoreCase(sub, "Assurance")) {
                return Optional.ofNullable(this.categoryByName.get("Abonnements"));
            }
            return Optional.ofNullable(this.categoryByName.get("Transport"));
        }
        if (containsIgnoreCase(cat, "Abonnement")) {
            return Optional.ofNullable(this.categoryByName.get("Abonnements"));
        }
        if (containsIgnoreCase(cat, "Revenu") || containsIgnoreCase(cat, "Allocation") || containsIgnoreCase(cat, "Salaire")) {
            return Optional.ofNullable(this.categoryByName.get("Revenus"));
        }
        if (containsIgnoreCase(cat, "Virement") || containsIgnoreCase(cat, "epargne") || containsIgnoreCase(cat, "épargne")) {
            return Optional.ofNullable(this.categoryByName.get("Virements"));
        }
        if (containsIgnoreCase(cat, "financier") || containsIgnoreCase(cat, "bancaire") || containsIgnoreCase(cat, "Emprunt")) {
            return Optional.ofNullable(this.categoryByName.get("Frais bancaires"));
        }
        if (containsIgnoreCase(cat, "Sante") || containsIgnoreCase(cat, "Santé")) {
            return Optional.ofNullable(this.categoryByName.get("Santé"));
        }
        if (containsIgnoreCase(cat, "Logement")) {
            return Optional.ofNullable(this.categoryByName.get("Logement"));
        }
        if (containsIgnoreCase(cat, "Restaurant")) {
            return Optional.ofNullable(this.categoryByName.get("Restaurants"));
        }
        if (containsIgnoreCase(cat, "Course") || containsIgnoreCase(cat, "Supermarche") || containsIgnoreCase(cat, "Supermarché")) {
            return Optional.ofNullable(this.categoryByName.get("Courses"));
        }
        if (containsIgnoreCase(cat, "Shopping")) {
            return Optional.ofNullable(this.categoryByName.get("Shopping"));
        }

        // Correspondance directe insensible aux majuscules / accents
        for (Map.Entry<String, Category> entry : this.categoryByName.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(cat) || containsIgnoreCase(cat, entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.ofNullable(this.categoryByName.get("Autres dépenses"));
    }

    private static boolean containsIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) return false;
        return str.toLowerCase(java.util.Locale.ROOT).contains(searchStr.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Catégorise une transaction en combinant le libellé et les informations CSV de la banque.
     * Le libellé prévaut pour les types d'opérations formels (ex: Virements, Salaires, Loyer),
     * évitant les erreurs de classification de la banque exportatrice (ex: VIR INSTANTANE marqué Loisirs/Restaurants).
     */
    @Transactional(readOnly = true)
    public Optional<Category> classify(String rawLabel, String normalizedLabel, String rawCategory, String rawSubcategory) {
        String labelToCheck = normalizedLabel != null && !normalizedLabel.isBlank() ? normalizedLabel : rawLabel;

        // 1. Si le libellé indique clairement un virement, priorité absolue au type virement
        if (isVirement(labelToCheck)) {
            if (isRevenu(labelToCheck)) {
                return Optional.ofNullable(this.categoryByName.get("Revenus"));
            }
            if (isLogement(labelToCheck)) {
                return Optional.ofNullable(this.categoryByName.get("Logement"));
            }
            return Optional.ofNullable(this.categoryByName.get("Virements"));
        }

        // 2. Si le libellé indique un revenu formel (salaire, allocation, etc.)
        if (isRevenu(labelToCheck)) {
            return Optional.ofNullable(this.categoryByName.get("Revenus"));
        }

        // 3. Catégorie CSV fournie par la banque si disponible
        if (rawCategory != null && !rawCategory.isBlank()) {
            Optional<Category> fromCsv = classifyCategory(rawCategory, rawSubcategory);
            if (fromCsv.isPresent()) {
                return fromCsv;
            }
        }

        // 4. Moteur de règles sur libellé normalisé
        return classify(normalizedLabel != null && !normalizedLabel.isBlank() ? normalizedLabel : rawLabel);
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

        // 0. Détection prioritaire des virements et transferts
        if (isVirement(normalizedLabel)) {
            if (isRevenu(normalizedLabel)) {
                return Optional.ofNullable(this.categoryByName.get("Revenus"));
            }
            if (isLogement(normalizedLabel)) {
                return Optional.ofNullable(this.categoryByName.get("Logement"));
            }
            return Optional.ofNullable(this.categoryByName.get("Virements"));
        }
        if (isRevenu(normalizedLabel)) {
            return Optional.ofNullable(this.categoryByName.get("Revenus"));
        }

        // 1. Essai direct sur le libellé brut
        for (CategoryRule rule : this.rules) {
            Optional<Category> result = rule.classify(normalizedLabel);
            if (result.isPresent()) {
                log.debug("Classified label with rule (specificity={}): category={}",
                        rule.specificity(), result.get().getName());
                return result;
            }
        }

        // 2. Essai après suppression des préfixes bancaires courants (ex: CARTE X6271 29/08 CARREFOUR -> CARREFOUR)
        String stripped = stripBankPrefix(normalizedLabel);
        if (!stripped.equals(normalizedLabel) && !stripped.isBlank()) {
            for (CategoryRule rule : this.rules) {
                Optional<Category> result = rule.classify(stripped);
                if (result.isPresent()) {
                    log.debug("Classified stripped label with rule (specificity={}): category={}",
                            rule.specificity(), result.get().getName());
                    return result;
                }
            }
        }

        return Optional.empty();
    }

    public static boolean isVirement(String label) {
        if (label == null || label.isBlank()) return false;
        String upper = label.toUpperCase(java.util.Locale.ROOT);
        return upper.contains("VIR INSTANTANE")
                || upper.contains("VIR EMIS")
                || upper.contains("VIR RECU")
                || upper.contains("VIREMENT")
                || upper.contains("ORDRE VIREMENT")
                || upper.contains("TRANSFERT")
                || upper.contains("LOGITEL")
                || upper.contains("WERO")
                || upper.matches(".*\\bVIR\\b.*")
                || upper.matches("^\\d{6}\\s+VIR.*");
    }

    public static boolean isRevenu(String label) {
        if (label == null || label.isBlank()) return false;
        String upper = label.toUpperCase(java.util.Locale.ROOT);
        return upper.contains("SALAIRE")
                || upper.contains("REMUNERATION")
                || upper.contains("TRAITEMENT")
                || upper.contains("ALLOCATION")
                || upper.contains("POLE EMPLOI")
                || upper.contains("FRANCE TRAVAIL")
                || upper.contains("CPAM")
                || upper.contains("CAF ");
    }

    public static boolean isLogement(String label) {
        if (label == null || label.isBlank()) return false;
        String upper = label.toUpperCase(java.util.Locale.ROOT);
        return upper.contains("LOYER") || upper.contains("CHARGE LOYER");
    }

    private String stripBankPrefix(String label) {
        // Préfixes de carte bancaire: "CARTE X1234 12/03 " ou "CARTE 12/03 "
        String cleaned = label.replaceFirst("^CARTE\\s+(?:X?\\d+\\s+)?\\d{2}/\\d{2}\\s+", "");
        // Préfixes de virement: "000001 VIR INSTANTANE EMIS LOGITEL POUR: ..."
        cleaned = cleaned.replaceFirst("^\\d{6}\\s+VIR\\s+(?:INSTANTANE\\s+)?(?:EMIS|RECU)\\s+(?:LOGITEL|WERO)?\\s*(?:POUR:|DE:)?\\s*", "");
        // Préfixes génériques de virement: "VIR RECU DE: " ou "VIR EMIS POUR: "
        cleaned = cleaned.replaceFirst("^VIR\\s+(?:EMIS|RECU)\\s*(?:POUR:|DE:)?\\s*", "");
        return cleaned.trim();
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
                "000001 VIR", "VIR INSTANTANE", "VIR EMIS", "VIR RECU", "VIR SEPA",
                "ORDRE VIREMENT", "VIREMENT",
                "PRELEVEMENT SEPA",
                "TRANSFERT", "ENVOI ARGENT");

        // -------------------------------------------------------------------------
        // 3. Règles par MOTS-CLES (score 50) – avec blacklist
        // -------------------------------------------------------------------------

        // Virements
        keyword(rules, virements,
                List.of("VIR INSTANTANE", "VIR EMIS", "VIR RECU", "VIREMENT", "VIR LOGITEL", "VIR WERO"),
                List.of("SALAIRE", "LOYER"));

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
