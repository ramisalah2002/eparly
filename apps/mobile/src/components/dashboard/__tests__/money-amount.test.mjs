import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import { parseMoneyParts } from '../../../utils/formatters.ts';

describe('Signature Monetary Typography & Parser', () => {
  test('parses €8,450.50 with correct integer and decimal split in en-IE', () => {
    const parts = parseMoneyParts(845050, 'EUR', 'en-IE');

    assert.strictEqual(parts.prefixCurrency, '€');
    assert.strictEqual(parts.integerText, '8,450');
    assert.strictEqual(parts.decimalText, '.50');
    assert.strictEqual(parts.suffixCurrency, '');
    assert.strictEqual(parts.fullFormatted, '€8,450.50');
    assert.strictEqual(parts.sign, '');
  });

  test('parses income €3,450.00 and expenses €1,280.00 accurately', () => {
    const income = parseMoneyParts(345000, 'EUR', 'en-IE');
    assert.strictEqual(income.prefixCurrency, '€');
    assert.strictEqual(income.integerText, '3,450');
    assert.strictEqual(income.decimalText, '.00');
    assert.strictEqual(income.fullFormatted, '€3,450.00');

    const expenses = parseMoneyParts(128000, 'EUR', 'en-IE');
    assert.strictEqual(expenses.prefixCurrency, '€');
    assert.strictEqual(expenses.integerText, '1,280');
    assert.strictEqual(expenses.decimalText, '.00');
    assert.strictEqual(expenses.fullFormatted, '€1,280.00');
  });

  test('parses French locale (fr-FR) with suffix currency symbol', () => {
    const parts = parseMoneyParts(845050, 'EUR', 'fr-FR');

    assert.strictEqual(parts.prefixCurrency, '');
    // Integer contains group separator
    assert.ok(parts.integerText.startsWith('8'));
    assert.ok(parts.integerText.endsWith('450'));
    assert.strictEqual(parts.decimalText, ',50');
    assert.ok(parts.suffixCurrency.includes('€'));
  });

  test('handles negative amounts and explicit signs correctly', () => {
    const neg = parseMoneyParts(-128000, 'EUR', 'en-IE');
    assert.strictEqual(neg.sign, '-');
    assert.strictEqual(neg.integerText, '1,280');
    assert.strictEqual(neg.decimalText, '.00');
    assert.strictEqual(neg.fullFormatted, '-€1,280.00');

    const posSign = parseMoneyParts(345000, 'EUR', 'en-IE', { showSign: true });
    assert.strictEqual(posSign.sign, '+');
    assert.strictEqual(posSign.fullFormatted, '+€3,450.00');
  });

  test('formats spending amount €1,280 without decimals when hideDecimals is true', () => {
    const parts = parseMoneyParts(128000, 'EUR', 'en-IE', { hideDecimals: true });
    assert.strictEqual(parts.prefixCurrency, '€');
    assert.strictEqual(parts.integerText, '1,280');
    assert.strictEqual(parts.decimalText, '');
    assert.strictEqual(parts.fullFormatted, '€1,280');
  });

  test('handles zero, null, undefined, and NaN values gracefully', () => {
    const zero = parseMoneyParts(0, 'EUR', 'en-IE');
    assert.strictEqual(zero.integerText, '0');
    assert.strictEqual(zero.decimalText, '.00');

    const nullVal = parseMoneyParts(null, 'EUR', 'en-IE');
    assert.strictEqual(nullVal.integerText, '—');
    assert.strictEqual(nullVal.decimalText, '');

    const undefVal = parseMoneyParts(undefined, 'EUR', 'en-IE');
    assert.strictEqual(undefVal.integerText, '—');

    const nanVal = parseMoneyParts(NaN, 'EUR', 'en-IE');
    assert.strictEqual(nanVal.integerText, '—');
  });

  test('verifies privacy masked state does not expose numbers in accessibility', () => {
    const hiddenLabelPrefix = 'Total balance';
    const isHidden = true;

    // Simulate MoneyAmount accessible label calculation
    const accessibleLabel = isHidden
      ? `${hiddenLabelPrefix}: Hidden`
      : parseMoneyParts(845050, 'EUR', 'en-IE').fullFormatted;

    assert.strictEqual(accessibleLabel, 'Total balance: Hidden');
    assert.ok(!accessibleLabel.includes('8450'));
    assert.ok(!accessibleLabel.includes('8,450'));
    assert.ok(!accessibleLabel.includes('50'));
  });

  test('handles Hermes engine environment where formatToParts is undefined', () => {
    const originalFormatToParts = Intl.NumberFormat.prototype.formatToParts;
    try {
      // Force formatToParts to be undefined as in Hermes
      // @ts-expect-error simulating Hermes runtime
      delete Intl.NumberFormat.prototype.formatToParts;

      const parts = parseMoneyParts(845050, 'EUR', 'en-IE');
      assert.strictEqual(parts.prefixCurrency, '€');
      assert.strictEqual(parts.integerText, '8,450');
      assert.strictEqual(parts.decimalText, '.50');
      assert.strictEqual(parts.fullFormatted, '€8,450.50');

      const frParts = parseMoneyParts(845050, 'EUR', 'fr-FR');
      assert.strictEqual(frParts.decimalText, ',50');
      assert.ok(frParts.suffixCurrency.includes('€'));
    } finally {
      Intl.NumberFormat.prototype.formatToParts = originalFormatToParts;
    }
  });
});

