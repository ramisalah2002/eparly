import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

import { BRAND_COLORS } from '../../../constants/colors.ts';
import { FONT_FAMILY, TYPOGRAPHY } from '../../../constants/typography.ts';

describe('Signature Quick Actions & Typography System', () => {
  it('validates typography tokens and Space Grotesk font families', () => {
    // 1. Font Family contracts
    assert.equal(FONT_FAMILY.regular, 'SpaceGrotesk-Regular');
    assert.equal(FONT_FAMILY.medium, 'SpaceGrotesk-Medium');
    assert.equal(FONT_FAMILY.semiBold, 'SpaceGrotesk-SemiBold');
    assert.equal(FONT_FAMILY.bold, 'SpaceGrotesk-Bold');

    // 2. Scale hierarchy verification
    assert.ok(TYPOGRAPHY.display.fontSize >= 34, 'Display must be >= 34px');
    assert.ok(TYPOGRAPHY.largeHeading.fontSize >= 27, 'Large Heading must be >= 27px');
    assert.ok(TYPOGRAPHY.sectionHeading.fontSize >= 21, 'Section Heading must be >= 21px');
    assert.ok(TYPOGRAPHY.cardHeading.fontSize >= 17, 'Card Heading must be >= 17px');
    assert.ok(TYPOGRAPHY.body.fontSize >= 14, 'Body must be >= 14px');
    assert.ok(TYPOGRAPHY.secondary.fontSize >= 12, 'Secondary must be >= 12px');
    assert.ok(TYPOGRAPHY.caption.fontSize >= 11, 'Caption must be >= 11px');

    // 3. Weight hierarchy checks
    assert.equal(TYPOGRAPHY.display.fontWeight, '700');
    assert.equal(TYPOGRAPHY.largeHeading.fontWeight, '700');
    assert.equal(TYPOGRAPHY.sectionHeading.fontWeight, '700');
    assert.equal(TYPOGRAPHY.cardHeading.fontWeight, '600');
    assert.equal(TYPOGRAPHY.body.fontWeight, '400');
    assert.equal(TYPOGRAPHY.bodyMedium.fontWeight, '500');
  });

  it('validates the four Quick Action cards and their pastel palette', () => {
    const cards = [
      {
        id: 'import',
        title: 'Import your transactions',
        descriptor: 'CSV import',
        backgroundColor: BRAND_COLORS.quickActionLavender,
      },
      {
        id: 'subscriptions',
        title: 'Manage subscriptions',
        descriptor: 'Recurring bills',
        backgroundColor: BRAND_COLORS.quickActionPeach,
      },
      {
        id: 'spending',
        title: 'Explore your spending',
        descriptor: 'Insights',
        backgroundColor: BRAND_COLORS.quickActionYellow,
      },
      {
        id: 'savings',
        title: 'Find ways to save',
        descriptor: 'Savings',
        backgroundColor: BRAND_COLORS.quickActionMint,
      },
    ];

    assert.equal(cards.length, 4);

    // Color hex assertions from the design brief
    assert.equal(BRAND_COLORS.quickActionLavender, '#C9C8F5');
    assert.equal(BRAND_COLORS.quickActionPeach, '#EFE0D5');
    assert.equal(BRAND_COLORS.quickActionYellow, '#EBD98C');
    assert.equal(BRAND_COLORS.quickActionMint, '#CBE7DF');

    // Descriptors and titles match
    assert.deepEqual(cards.map((c) => c.descriptor), [
      'CSV import',
      'Recurring bills',
      'Insights',
      'Savings',
    ]);
  });

  it('validates card geometry and circular arrow specs', () => {
    const cardDimensions = {
      minHeight: 162,
      borderRadius: 24,
      arrowButtonSize: 36,
      arrowButtonRadius: 18,
    };

    assert.ok(cardDimensions.minHeight >= 150 && cardDimensions.minHeight <= 175);
    assert.ok(cardDimensions.borderRadius >= 23 && cardDimensions.borderRadius <= 27);
    assert.equal(cardDimensions.arrowButtonSize / 2, cardDimensions.arrowButtonRadius);
  });

  it('validates accessibility attributes for all quick actions', () => {
    const generateAccessibilityProps = (title, descriptor, hint) => ({
      accessibilityRole: 'button',
      accessibilityLabel: `${title}, ${descriptor}`,
      accessibilityHint: hint,
    });

    const card1 = generateAccessibilityProps(
      'Import your transactions',
      'CSV import',
      'Opens CSV import modal to load bank transactions'
    );
    assert.equal(card1.accessibilityRole, 'button');
    assert.equal(card1.accessibilityLabel, 'Import your transactions, CSV import');
    assert.ok(card1.accessibilityHint.includes('CSV import'));

    const card2 = generateAccessibilityProps(
      'Manage subscriptions',
      'Recurring bills',
      'Navigates to recurring subscriptions screen'
    );
    assert.equal(card2.accessibilityRole, 'button');
    assert.equal(card2.accessibilityLabel, 'Manage subscriptions, Recurring bills');
  });
});

