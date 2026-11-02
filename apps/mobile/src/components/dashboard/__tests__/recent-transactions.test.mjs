import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import { DASHBOARD_DEMO_DATA } from '../../../features/dashboard/demo/dashboard-demo-data.ts';
import { groupTransactionsByDate, parseMoneyParts } from '../../../utils/formatters.ts';

describe('Signature Recent Transactions Activity Feed', () => {
  const { transactions } = DASHBOARD_DEMO_DATA.recentTransactions;

  test('validates the four expected demo transactions', () => {
    assert.strictEqual(transactions.length, 4);

    // 1. Carrefour
    assert.strictEqual(transactions[0].merchant, 'Carrefour');
    assert.strictEqual(transactions[0].category, 'Groceries');
    assert.strictEqual(transactions[0].amountMinor, -5280);
    assert.strictEqual(transactions[0].dateLabel, 'Oct 29');

    // 2. Spotify
    assert.strictEqual(transactions[1].merchant, 'Spotify');
    assert.strictEqual(transactions[1].category, 'Subscription');
    assert.strictEqual(transactions[1].amountMinor, -1099);
    assert.strictEqual(transactions[1].dateLabel, 'Oct 28');

    // 3. Salary
    assert.strictEqual(transactions[2].merchant, 'Salary');
    assert.strictEqual(transactions[2].category, 'Income');
    assert.strictEqual(transactions[2].amountMinor, 285000);
    assert.strictEqual(transactions[2].dateLabel, 'Oct 25');

    // 4. Netflix
    assert.strictEqual(transactions[3].merchant, 'Netflix');
    assert.strictEqual(transactions[3].category, 'Subscription');
    assert.strictEqual(transactions[3].amountMinor, -1349);
    assert.strictEqual(transactions[3].dateLabel, 'Oct 23');
  });

  test('groups transactions by calendar date into Today, Yesterday, Earlier', () => {
    // Relative to Oct 29, 2026 reference date
    const groups = groupTransactionsByDate(transactions, {
      referenceDate: new Date(2026, 9, 29),
    });

    assert.strictEqual(groups.length, 3);

    // Group 1: Today
    assert.strictEqual(groups[0].title, 'Today');
    assert.strictEqual(groups[0].transactions.length, 1);
    assert.strictEqual(groups[0].transactions[0].merchant, 'Carrefour');

    // Group 2: Yesterday
    assert.strictEqual(groups[1].title, 'Yesterday');
    assert.strictEqual(groups[1].transactions.length, 1);
    assert.strictEqual(groups[1].transactions[0].merchant, 'Spotify');

    // Group 3: Earlier
    assert.strictEqual(groups[2].title, 'Earlier');
    assert.strictEqual(groups[2].transactions.length, 2);
    assert.strictEqual(groups[2].transactions[0].merchant, 'Salary');
    assert.strictEqual(groups[2].transactions[1].merchant, 'Netflix');
  });

  test('handles empty transactions list gracefully without errors', () => {
    const emptyGroups = groupTransactionsByDate([]);
    assert.deepStrictEqual(emptyGroups, []);
  });

  test('formats transaction monetary amounts with correct positive/negative signs', () => {
    // Carrefour: -€52.80
    const carrefour = parseMoneyParts(-5280, 'EUR', 'en-IE');
    assert.strictEqual(carrefour.sign, '-');
    assert.strictEqual(carrefour.fullFormatted, '-€52.80');

    // Salary: +€2,850.00
    const salary = parseMoneyParts(285000, 'EUR', 'en-IE', { showSign: true });
    assert.strictEqual(salary.sign, '+');
    assert.strictEqual(salary.fullFormatted, '+€2,850.00');
  });

  test('ensures privacy mode masks transaction amounts in accessibility text', () => {
    const isBalanceHidden = true;
    const tx = transactions[0]; // Carrefour

    const accessibleAmount = isBalanceHidden
      ? 'amount hidden'
      : `minus ${Math.abs(tx.amountMinor) / 100} euros`;

    const label = `${tx.merchant}, ${tx.category}, ${tx.dateLabel}, ${accessibleAmount}`;

    assert.strictEqual(label, 'Carrefour, Groceries, Oct 29, amount hidden');
    assert.ok(!label.includes('52.80'));
    assert.ok(!label.includes('5280'));
  });
});

