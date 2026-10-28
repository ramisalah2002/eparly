package com.eparly.api.account.domain;

/**
 * Origine des données pour un compte financier Eparly.
 * Pour le MVP, les imports proviennent de fichiers CSV.
 * Conçu pour être étendu à des agrégateurs bancaires DSP2 (BANK_API) en P1.
 */
public enum AccountSourceType {
    CSV,
    BANK_API
}
