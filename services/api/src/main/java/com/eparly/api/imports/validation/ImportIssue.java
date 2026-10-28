package com.eparly.api.imports.validation;

/**
 * Erreur ou avertissement releve pendant l'analyse d'un fichier.
 * Ne contient jamais de valeur financiere ni de libelle : uniquement un code, un message generique,
 * le numero de ligne (0 pour une anomalie portant sur tout le fichier ou le mapping) et le nom de colonne.
 */
public record ImportIssue(int line, String column, String code, String message, Severity severity) {

    public enum Severity { ERROR, WARNING }

    public static ImportIssue error(int line, String column, String code, String message) {
        return new ImportIssue(line, column, code, message, Severity.ERROR);
    }

    public static ImportIssue warning(int line, String column, String code, String message) {
        return new ImportIssue(line, column, code, message, Severity.WARNING);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
