package com.eparly.api.transaction.domain;

/**
 * Nature comptable et budgétaire d'une transaction financière.
 * Les transferts internes (TRANSFER) sont distingués pour ne pas être comptabilisés
 * comme des dépenses ordinaires.
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    REFUND,
    FEE,
    UNKNOWN
}
