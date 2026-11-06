import React from 'react';
import { StyleProp, StyleSheet, Text, TextStyle, View, ViewStyle } from 'react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { FONT_FAMILY } from '@/constants/typography';
import { parseMoneyParts } from '@/utils/formatters';

export type MoneyAmountSize = 'hero' | 'editorial' | 'subcard' | 'compact' | 'row';

export interface MoneyAmountProps {
  amountMinor: number | null | undefined;
  currency?: string;
  locale?: string;
  isHidden?: boolean;
  size?: MoneyAmountSize;
  color?: string;
  decimalColor?: string;
  showSign?: boolean;
  hideDecimals?: boolean;
  style?: StyleProp<ViewStyle>;
  textStyle?: StyleProp<TextStyle>;
  accessibilityLabelPrefix?: string;
}

/**
 * Reusable editorial financial typography component.
 * Features baseline-aligned decimals, localized currency placement,
 * tabular numerals, precise integer-minor math, and secure privacy mode.
 */
export function MoneyAmount({
  amountMinor,
  currency = 'EUR',
  locale = 'en-IE',
  isHidden = false,
  size = 'hero',
  color = BRAND_COLORS.darkCharcoal,
  decimalColor,
  showSign = false,
  hideDecimals = false,
  style,
  textStyle,
  accessibilityLabelPrefix,
}: MoneyAmountProps) {
  const parts = parseMoneyParts(amountMinor, currency, locale, { showSign, hideDecimals });

  const resolvedDecimalColor = decimalColor || (size === 'hero' ? '#5E6072' : color);

  if (isHidden) {
    const maskedString = size === 'hero' ? '••••••••' : size === 'editorial' ? '••••••' : '•••••';
    const accessibleHiddenLabel = accessibilityLabelPrefix
      ? `${accessibilityLabelPrefix}: Hidden`
      : 'Amount hidden';

    return (
      <View
        style={[styles.container, style]}
        accessible
        accessibilityLabel={accessibleHiddenLabel}
        accessibilityRole="text">
        <Text
          style={[
            styles.baseText,
            styles[`${size}HiddenText`],
            { color },
            textStyle,
          ]}
          numberOfLines={1}>
          {maskedString}
        </Text>
      </View>
    );
  }

  const accessibleLabel = accessibilityLabelPrefix
    ? `${accessibilityLabelPrefix}: ${parts.fullFormatted}`
    : parts.fullFormatted;

  return (
    <View
      style={[styles.container, style]}
      accessible
      accessibilityLabel={accessibleLabel}
      accessibilityRole="text">
      <Text style={[styles.baseText, textStyle]} numberOfLines={1}>
        {parts.sign ? (
          <Text style={[styles[`${size}Sign`], { color }]}>{parts.sign}</Text>
        ) : null}
        {parts.prefixCurrency ? (
          <Text style={[styles[`${size}Currency`], { color }]}>
            {parts.prefixCurrency}
          </Text>
        ) : null}
        <Text style={[styles[`${size}Integer`], { color }]}>
          {parts.integerText}
        </Text>
        {parts.decimalText ? (
          <Text style={[styles[`${size}Decimal`], { color: resolvedDecimalColor }]}>
            {parts.decimalText}
          </Text>
        ) : null}
        {parts.suffixCurrency ? (
          <Text style={[styles[`${size}Currency`], { color }]}>
            {parts.suffixCurrency}
          </Text>
        ) : null}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    justifyContent: 'center',
  },
  baseText: {
    fontFamily: FONT_FAMILY.bold,
    includeFontPadding: false,
    textAlignVertical: 'bottom',
  },

  // HERO VARIANT (e.g. Total Balance €8,450.50)
  heroInteger: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 34,
    fontWeight: '700',
    letterSpacing: -0.8,
    fontVariant: ['tabular-nums'],
    lineHeight: 40,
  },
  heroDecimal: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 22,
    fontWeight: '600',
    letterSpacing: -0.3,
    fontVariant: ['tabular-nums'],
  },
  heroCurrency: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 26,
    fontWeight: '600',
    letterSpacing: -0.4,
    fontVariant: ['tabular-nums'],
  },
  heroSign: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 30,
    fontWeight: '600',
    letterSpacing: -0.5,
  },
  heroHiddenText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: 4,
    lineHeight: 40,
  },

  // EDITORIAL VARIANT (e.g. Spending Chart Selected Month €1,280)
  editorialInteger: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 26,
    fontWeight: '700',
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
    lineHeight: 32,
  },
  editorialDecimal: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 18,
    fontWeight: '600',
    letterSpacing: -0.3,
    fontVariant: ['tabular-nums'],
  },
  editorialCurrency: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 22,
    fontWeight: '600',
    letterSpacing: -0.3,
    fontVariant: ['tabular-nums'],
  },
  editorialSign: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 24,
    fontWeight: '600',
    letterSpacing: -0.4,
  },
  editorialHiddenText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 24,
    fontWeight: '700',
    letterSpacing: 3,
    lineHeight: 32,
  },

  // SUBCARD VARIANT (e.g. Income €3,450.00 / Expenses €1,280.00)
  subcardInteger: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: -0.3,
    fontVariant: ['tabular-nums'],
    lineHeight: 22,
  },
  subcardDecimal: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: -0.2,
    fontVariant: ['tabular-nums'],
  },
  subcardCurrency: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  subcardSign: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    fontWeight: '600',
  },
  subcardHiddenText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: 2,
    lineHeight: 22,
  },

  // COMPACT VARIANT
  compactInteger: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 14,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    lineHeight: 18,
  },
  compactDecimal: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  compactCurrency: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 12,
    fontWeight: '600',
  },
  compactSign: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 13,
    fontWeight: '600',
  },
  compactHiddenText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    lineHeight: 18,
  },

  // ROW VARIANT (e.g. Transaction activity feed rows: consistent 15px baseline alignment)
  rowInteger: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: -0.2,
    fontVariant: ['tabular-nums'],
    lineHeight: 20,
  },
  rowDecimal: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: -0.2,
    fontVariant: ['tabular-nums'],
    lineHeight: 20,
  },
  rowCurrency: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: -0.2,
    lineHeight: 20,
  },
  rowSign: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    fontWeight: '600',
    lineHeight: 20,
  },
  rowHiddenText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: 2,
    lineHeight: 20,
  },
});

