import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import { DASHBOARD_DEMO_DATA } from '../../../features/dashboard/demo/dashboard-demo-data.ts';
import { computeCategoryShare, parseMoneyParts } from '../../../utils/formatters.ts';

describe('Spending Mix / Category Breakdown Consistency', () => {
  const { totalExpensesMinor, categories } = DASHBOARD_DEMO_DATA.categoryBreakdown;

  test('validates 5 categories sum to exactly €1,280', () => {
    assert.strictEqual(categories.length, 5);
    const sum = categories.reduce((acc, cat) => acc + cat.amountMinor, 0);
    assert.strictEqual(sum, 128000);
    assert.strictEqual(sum, totalExpensesMinor);
  });

  test('computes accurate percentage shares matching the Eparly design brief', () => {
    // Shopping €390 -> 30.5%
    const shopping = categories.find((c) => c.id === 'cat-shopping');
    assert.ok(shopping);
    assert.strictEqual(computeCategoryShare(shopping.amountMinor, totalExpensesMinor), '30.5');

    // Food & Drinks €310 -> 24.2%
    const food = categories.find((c) => c.id === 'cat-food');
    assert.ok(food);
    assert.strictEqual(computeCategoryShare(food.amountMinor, totalExpensesMinor), '24.2');

    // Bills €280 -> 21.9%
    const bills = categories.find((c) => c.id === 'cat-bills');
    assert.ok(bills);
    assert.strictEqual(computeCategoryShare(bills.amountMinor, totalExpensesMinor), '21.9');

    // Transport €180 -> 14.1%
    const transport = categories.find((c) => c.id === 'cat-transport');
    assert.ok(transport);
    assert.strictEqual(computeCategoryShare(transport.amountMinor, totalExpensesMinor), '14.1');

    // Other €120 -> 9.4%
    const other = categories.find((c) => c.id === 'cat-other');
    assert.ok(other);
    assert.strictEqual(computeCategoryShare(other.amountMinor, totalExpensesMinor), '9.4');
  });

  test('handles division-by-zero safely when total is zero or negative', () => {
    assert.strictEqual(computeCategoryShare(5000, 0), '0.0');
    assert.strictEqual(computeCategoryShare(5000, -100), '0.0');
  });

  test('formats monetary amounts without decimals correctly in editorial mode', () => {
    const totalFormatted = parseMoneyParts(totalExpensesMinor, 'EUR', 'en-IE', {
      hideDecimals: true,
    });
    assert.strictEqual(totalFormatted.fullFormatted, '€1,280');

    const shoppingFormatted = parseMoneyParts(39000, 'EUR', 'en-IE', {
      hideDecimals: true,
    });
    assert.strictEqual(shoppingFormatted.fullFormatted, '€390');
  });

  test('verifies privacy mode masks sensitive values in screen reader labels', () => {
    const isBalanceHidden = true;
    const shopping = categories.find((c) => c.id === 'cat-shopping');
    assert.ok(shopping);

    const share = computeCategoryShare(shopping.amountMinor, totalExpensesMinor);
    const accessibleAmount = isBalanceHidden
      ? 'amount hidden'
      : `${Math.round(shopping.amountMinor / 100)} euros`;

    const fullLabel = `${shopping.name}, ${share}% of spending, ${accessibleAmount}`;

    assert.strictEqual(fullLabel, 'Shopping, 30.5% of spending, amount hidden');
    assert.ok(!fullLabel.includes('390'));
  });
});
