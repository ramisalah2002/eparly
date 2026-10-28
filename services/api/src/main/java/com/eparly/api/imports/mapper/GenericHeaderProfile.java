package com.eparly.api.imports.mapper;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Profil par defaut : dictionnaire de synonymes d'en-tetes (francais/anglais), sans aucun nom de banque.
 * Les synonymes sont compares apres normalisation (casse, accents, ponctuation).
 * Les colonnes de type "reference" ne sont volontairement pas proposees comme identifiant source :
 * elles ne sont pas toujours uniques, et un identifiant non fiable ferait echouer l'import.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericHeaderProfile implements BankProfile {

    private static final List<String> DATE = List.of(
            "date operation", "date de l operation", "date comptable", "date de comptabilisation",
            "date transaction", "date de transaction", "date op", "booking date", "transaction date", "date");
    private static final List<String> VALUE_DATE = List.of(
            "date valeur", "date de valeur", "value date", "date val");
    private static final List<String> LABEL = List.of(
            "libelle", "libelle de l operation", "libelle operation", "libelle simplifie", "intitule",
            "description", "label", "designation", "details", "detail", "motif", "objet");
    private static final List<String> AMOUNT = List.of(
            "montant", "montant eur", "montant euros", "montant en eur", "montant de l operation", "amount", "somme");
    private static final List<String> DEBIT = List.of(
            "debit", "montant debit", "debit eur", "debit euros", "retrait");
    private static final List<String> CREDIT = List.of(
            "credit", "montant credit", "credit eur", "credit euros", "depot");
    private static final List<String> CURRENCY = List.of(
            "devise", "currency", "monnaie");
    private static final List<String> SOURCE_ID = List.of(
            "id transaction", "identifiant transaction", "transaction id", "id operation",
            "identifiant operation", "identifiant unique", "unique id", "uid", "numero operation");

    @Override
    public Optional<ImportMapping> match(List<String> headers) {
        Set<Integer> used = new HashSet<>();
        String date = find(headers, DATE, used);
        String valueDate = find(headers, VALUE_DATE, used);
        String label = find(headers, LABEL, used);
        String amount = find(headers, AMOUNT, used);
        String debit = null;
        String credit = null;
        if (amount == null) {
            debit = find(headers, DEBIT, used);
            credit = find(headers, CREDIT, used);
            if (debit == null || credit == null) {
                debit = null;
                credit = null;
            }
        }
        String currency = find(headers, CURRENCY, used);
        String sourceId = find(headers, SOURCE_ID, used);

        if (date == null && label == null && amount == null && debit == null) {
            return Optional.empty();
        }
        return Optional.of(new ImportMapping(date, valueDate, label, amount, debit, credit, currency, sourceId,
                null, null));
    }

    private static String find(List<String> headers, List<String> synonyms, Set<Integer> used) {
        for (String synonym : synonyms) {
            for (int i = 0; i < headers.size(); i++) {
                if (!used.contains(i) && HeaderNormalizer.normalize(headers.get(i)).equals(synonym)) {
                    used.add(i);
                    return headers.get(i);
                }
            }
        }
        return null;
    }
}
