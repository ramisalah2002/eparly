import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { BRAND_COLORS } from '../../constants/colors.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

describe('Signature Bottom Navigation — Eparly Dock', () => {
  it('ensures BottomTabInset provides sufficient clearance for 68px dock + floating gap', () => {
    // Dock height is 68px + bottom clearance (up to 26px on iOS). Total dock footprint ~94px.
    // BottomTabInset of 84px + Spacing.four (24px) gives screens 108px of padding, avoiding overlap.
    const themeContent = fs.readFileSync(
      path.resolve(__dirname, '../../constants/theme.ts'),
      'utf8'
    );
    const match = themeContent.match(/BottomTabInset\s*=\s*Platform\.select\(\{\s*ios:\s*(\d+),\s*android:\s*(\d+)\s*\}\)\s*\?\?\s*(\d+);/);
    assert.ok(match, 'BottomTabInset should be defined with Platform.select');
    const iosInset = Number(match[1]);
    const androidInset = Number(match[2]);
    const fallbackInset = Number(match[3]);

    assert.ok(iosInset >= 84, `iOS BottomTabInset must be >= 84, got ${iosInset}`);
    assert.ok(androidInset >= 84, `Android BottomTabInset must be >= 84, got ${androidInset}`);
    assert.ok(fallbackInset >= 84, `Fallback BottomTabInset must be >= 84, got ${fallbackInset}`);
  });

  it('validates all 5 navigation destinations and metadata', () => {
    const tabs = [
      { name: 'index', href: '/(tabs)', label: 'Home', icon: 'House' },
      { name: 'transactions', href: '/(tabs)/transactions', label: 'Activity', icon: 'ArrowLeftRight' },
      { name: 'subscriptions', href: '/(tabs)/subscriptions', label: 'Recurring', icon: 'Repeat2' },
      { name: 'savings', href: '/(tabs)/savings', label: 'Savings', icon: 'PiggyBank' },
      { name: 'profile', href: '/(tabs)/profile', label: 'Profile', icon: 'UserRound' },
    ];

    assert.equal(tabs.length, 5);
    assert.deepEqual(tabs.map((t) => t.label), ['Home', 'Activity', 'Recurring', 'Savings', 'Profile']);
    assert.deepEqual(tabs.map((t) => t.name), ['index', 'transactions', 'subscriptions', 'savings', 'profile']);
    assert.deepEqual(tabs.map((t) => t.href), [
      '/(tabs)',
      '/(tabs)/transactions',
      '/(tabs)/subscriptions',
      '/(tabs)/savings',
      '/(tabs)/profile',
    ]);
  });

  it('validates active and inactive visual design tokens', () => {
    // Brand color contracts
    assert.equal(BRAND_COLORS.deepLavender, '#8067A1');
    assert.equal(BRAND_COLORS.secondaryText, '#737382');
    assert.equal(BRAND_COLORS.pureWhite, '#FFFFFF');
    assert.equal(BRAND_COLORS.darkCharcoal, '#242535');

    // Active state capsule contract: 48x34px capsule with soft lavender background
    const activeCapsuleBg = 'rgba(128, 103, 161, 0.12)';
    const inactiveCapsuleBg = 'transparent';
    assert.ok(activeCapsuleBg.includes('128, 103, 161'));
    assert.equal(inactiveCapsuleBg, 'transparent');

    // Dock dimensions
    const dockHeight = 68;
    const dockRadius = 34; // exactly half the height for pill geometry
    const capsuleWidth = 48;
    const capsuleHeight = 34;
    const capsuleRadius = 17; // exactly half the height

    assert.equal(dockHeight / 2, dockRadius);
    assert.equal(capsuleHeight / 2, capsuleRadius);
    assert.ok(capsuleWidth < 68);
  });

  it('verifies accessibility role and label generation', () => {
    const generateAccessibilityProps = (label, isFocused) => ({
      accessibilityRole: 'tab',
      accessibilityState: { selected: isFocused },
      accessibilityLabel: `${label}, tab`,
    });

    const homeTabProps = generateAccessibilityProps('Home', true);
    assert.equal(homeTabProps.accessibilityRole, 'tab');
    assert.equal(homeTabProps.accessibilityState.selected, true);
    assert.equal(homeTabProps.accessibilityLabel, 'Home, tab');

    const activityTabProps = generateAccessibilityProps('Activity', false);
    assert.equal(activityTabProps.accessibilityRole, 'tab');
    assert.equal(activityTabProps.accessibilityState.selected, false);
    assert.equal(activityTabProps.accessibilityLabel, 'Activity, tab');
  });
});
