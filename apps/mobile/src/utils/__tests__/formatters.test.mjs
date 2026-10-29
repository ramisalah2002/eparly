import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import {
  formatCurrencyMinor,
  formatDateDisplay,
  getCurrentMonthDateRange,
  getTimeBasedGreeting,
  getUserFirstName,
  getAvatarInitials,
} from '../formatters.ts';

describe('Financial & Dashboard Formatters', () => {
  test('formats positive minor units into localized euros', () => {
    const formatted = formatCurrencyMinor(45500); // 455.00 EUR
    // Normalize non-breaking spaces
    const clean = formatted.replace(/\s/g, ' ');
    assert.strictEqual(clean, '455,00 €');
  });

  test('formats negative minor units with negative sign', () => {
    const formatted = formatCurrencyMinor(-1250); // -12.50 EUR
    const clean = formatted.replace(/\s/g, ' ');
    assert.strictEqual(clean, '-12,50 €');
  });

  test('formats with explicit + / - sign when showSign is enabled', () => {
    const pos = formatCurrencyMinor(2500, 'EUR', { showSign: true }).replace(/\s/g, ' ');
    const neg = formatCurrencyMinor(-2500, 'EUR', { showSign: true }).replace(/\s/g, ' ');
    assert.strictEqual(pos, '+25,00 €');
    assert.strictEqual(neg, '-25,00 €');
  });

  test('formats zero minor units correctly', () => {
    const zero = formatCurrencyMinor(0).replace(/\s/g, ' ');
    assert.strictEqual(zero, '0,00 €');
  });

  test('handles null, undefined, or NaN gracefully', () => {
    assert.strictEqual(formatCurrencyMinor(null), '— €');
    assert.strictEqual(formatCurrencyMinor(undefined), '— €');
    assert.strictEqual(formatCurrencyMinor(NaN), '— €');
  });

  test('masks currency when masked option is true', () => {
    assert.strictEqual(formatCurrencyMinor(125000, 'EUR', { masked: true }), '•••• €');
    assert.strictEqual(formatCurrencyMinor(null, 'EUR', { masked: true }), '•••• €');
  });

  test('formats dates into readable localized strings', () => {
    assert.strictEqual(formatDateDisplay(null), '—');
    assert.strictEqual(formatDateDisplay(''), '—');
    const formatted = formatDateDisplay('2026-10-24', { includeYear: true });
    assert.ok(formatted.includes('24'));
    assert.ok(formatted.includes('2026'));
  });

  test('calculates correct month boundaries in ISO-8601', () => {
    const refDate = new Date(2026, 9, 15); // October 15, 2026
    const range = getCurrentMonthDateRange(refDate);
    assert.strictEqual(range.startDate, '2026-10-01');
    assert.strictEqual(range.endDate, '2026-10-31');
  });

  test('calculates February leap year correctly', () => {
    const leapFeb = new Date(2024, 1, 10); // February 2024
    const range = getCurrentMonthDateRange(leapFeb);
    assert.strictEqual(range.startDate, '2024-02-01');
    assert.strictEqual(range.endDate, '2024-02-29');
  });

  test('extracts first name from full name or null', () => {
    assert.strictEqual(getUserFirstName('Salah-Eddine Rami'), 'Salah-Eddine');
    assert.strictEqual(getUserFirstName('  Alice Martin  '), 'Alice');
    assert.strictEqual(getUserFirstName('Bob'), 'Bob');
    assert.strictEqual(getUserFirstName(''), null);
    assert.strictEqual(getUserFirstName(null), null);
    assert.strictEqual(getUserFirstName(undefined), null);
  });

  test('computes avatar initials accurately', () => {
    assert.strictEqual(getAvatarInitials('Salah-Eddine Rami'), 'SR');
    assert.strictEqual(getAvatarInitials('Alice'), 'AL');
    assert.strictEqual(getAvatarInitials(null, 'user@example.com'), 'US');
    assert.strictEqual(getAvatarInitials(null, null), 'EP');
  });

  test('returns contextual greeting according to hour', () => {
    const morning = new Date(2026, 9, 29, 8, 30);
    const afternoon = new Date(2026, 9, 29, 14, 0);
    const evening = new Date(2026, 9, 29, 20, 15);

    assert.strictEqual(getTimeBasedGreeting(morning), 'Bonjour');
    assert.strictEqual(getTimeBasedGreeting(afternoon), 'Bon après-midi');
    assert.strictEqual(getTimeBasedGreeting(evening), 'Bonsoir');
  });
});
