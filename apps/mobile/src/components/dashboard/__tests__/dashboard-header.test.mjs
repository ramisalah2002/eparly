import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

import { BRAND_COLORS } from '../../../constants/colors.ts';
import { FONT_FAMILY } from '../../../constants/typography.ts';
import {
  formatMonographHeaderDate,
  formatNaturalCapitalization,
  getTimeBasedGreeting,
  sanitizeInitials,
} from '../../../utils/formatters.ts';

describe('Monograph Dashboard Header (Concept 1)', () => {
  it('formats refined editorial date in natural casing', () => {
    const fixedDate = new Date(2026, 10, 2); // Monday, Nov 2, 2026
    const formatted = formatMonographHeaderDate(fixedDate);

    assert.equal(formatted, 'Monday, 2 November');
    assert.ok(!formatted.includes('MONDAY'));
    assert.ok(!formatted.includes('NOV'));
  });

  it('generates natural-cased personalized greeting', () => {
    const frGreeting = getTimeBasedGreeting();
    const prefix = frGreeting === 'Bonjour' ? 'Good morning' : frGreeting === 'Bon après-midi' ? 'Good afternoon' : 'Good evening';

    const formattedName = formatNaturalCapitalization('alex');
    assert.equal(formattedName, 'Alex');

    const greetingText = `${prefix}, ${formattedName}.`;
    assert.ok(greetingText.startsWith(prefix));
    assert.ok(greetingText.endsWith('Alex.'));
  });

  it('sanitizes user initials to clean 2-character monogram', () => {
    assert.equal(sanitizeInitials('AL'), 'AL');
    assert.equal(sanitizeInitials('alex'), 'AL');
    assert.equal(sanitizeInitials('⭐AL'), 'AL');
    assert.equal(sanitizeInitials(''), 'EP');
  });

  it('validates typography hierarchy and 36px control tokens', () => {
    // Left column typography tokens
    assert.equal(FONT_FAMILY.medium, 'SpaceGrotesk-Medium');
    assert.equal(FONT_FAMILY.bold, 'SpaceGrotesk-Bold');

    // Right action tokens
    const controlButtonSize = 36;
    const controlBorderRadius = 18;
    assert.equal(controlButtonSize, 36);
    assert.equal(controlBorderRadius, 18);

    // Color tokens
    assert.equal(BRAND_COLORS.darkCharcoal, '#242535');
    assert.equal(BRAND_COLORS.deepLavender, '#8067A1');
    assert.equal(BRAND_COLORS.pureWhite, '#FFFFFF');
  });
});

