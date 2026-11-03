import type { TextStyle } from 'react-native';

/**
 * EPARLY DESIGN SYSTEM — Typography Tokens
 * Inspired by modern geometric editorial fintech typography.
 * Font Family: Space Grotesk (Regular 400, Medium 500, SemiBold 600, Bold 700)
 */

export const FONT_FAMILY = {
  regular: 'SpaceGrotesk-Regular',
  medium: 'SpaceGrotesk-Medium',
  semiBold: 'SpaceGrotesk-SemiBold',
  bold: 'SpaceGrotesk-Bold',
} as const;

export type FontFamilyType = typeof FONT_FAMILY[keyof typeof FONT_FAMILY];

export const TYPOGRAPHY = {
  // Display: 34–40px / Bold (Hero balances, Splash titles)
  display: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 36,
    lineHeight: 44,
    fontWeight: '700',
    letterSpacing: -0.8,
  } as TextStyle,

  // Large Heading: 27–30px / Bold (Screen titles, Onboarding headlines)
  largeHeading: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '700',
    letterSpacing: -0.5,
  } as TextStyle,

  // Section Heading: 21–24px / SemiBold or Bold (Section titles like Quick Actions, Spending Overview)
  sectionHeading: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '700',
    letterSpacing: -0.3,
  } as TextStyle,

  // Card Heading: 17–19px / SemiBold (Cards, modal titles, overview amounts)
  cardHeading: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '600',
    letterSpacing: -0.2,
  } as TextStyle,

  // Body: 14–16px / Regular & Medium (Primary descriptions, form inputs)
  body: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '400',
  } as TextStyle,

  bodyMedium: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '500',
  } as TextStyle,

  bodySemiBold: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600',
  } as TextStyle,

  // Secondary: 12–14px / Regular & Medium (Dates, captions, subtitles)
  secondary: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '400',
  } as TextStyle,

  secondaryMedium: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
  } as TextStyle,

  secondarySemiBold: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
  } as TextStyle,

  // Caption: 11–12px / Medium (Badges, tags, axis labels)
  caption: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.1,
  } as TextStyle,

  captionSemiBold: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '600',
    letterSpacing: 0.1,
  } as TextStyle,

  // Financial Tabular Numbers
  financialHero: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 34,
    lineHeight: 40,
    fontWeight: '700',
    letterSpacing: -0.6,
  } as TextStyle,

  financialCard: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '700',
    letterSpacing: -0.3,
  } as TextStyle,

  financialItem: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
    letterSpacing: -0.2,
  } as TextStyle,
} as const;
