package com.eparly.api.imports.parser;

/**
 * Convention de notation des montants.
 * FR : virgule decimale (espaces ou points comme separateurs de milliers).
 * EN : point decimal (espaces ou virgules comme separateurs de milliers).
 * AUTO : deduction prudente ; les valeurs ambigues (ex. "1,250") sont refusees.
 */
public enum AmountFormat {
    AUTO, FR, EN
}
