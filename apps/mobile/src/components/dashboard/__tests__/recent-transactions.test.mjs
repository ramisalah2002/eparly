import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import { DASHBOARD_DEMO_DATA } from '../../../features/dashboard/demo/dashboard-demo-data.ts';
import { groupTransactionsByDate, parseMoneyParts } from '../../../utils/formatters.ts';

describe('Editorial Transaction Journal (Concept A)', () => {
  const { transactions } = DASHBOARD_DEMO_DATA.recentTransactions;

  test('validates the four expected demo transactions', () => {
    assert.strictEqual(transactions.length, 4);

    // 1. Carrefour
    assert.strictEqual(transactions[0].merchant, 'Carrefour');
    assert.strictEqual(transactions[0].category, 'Groceries');
    assert.strictEqual(transactions[0].amountMinor, -5280);
    assert.strictEqual(transactions[0].date, '2026-10-29');

    // 2. Spotify
    assert.strictEqual(transactions[1].merchant, 'Spotify');
    assert.strictEqual(transactions[1].category, 'Subscription');
    assert.strictEqual(transactions[1].amountMinor, -1099);
    assert.strictEqual(transactions[1].date, '2026-10-28');

    // 3. Salary
    assert.strictEqual(transactions[2].merchant, 'Salary');
    assert.strictEqual(transactions[2].category, 'Income');
    assert.strictEqual(transactions[2].amountMinor, 285000);
    assert.strictEqual(transactions[2].date, '2026-10-25');

    // 4. Netflix
    assert.strictEqual(transactions[3].merchant, 'Netflix');
    assert.strictEqual(transactions[3].category, 'Subscription');
    assert.strictEqual(transactions[3].amountMinor, -1349);
    assert.strictEqual(transactions[3].date, '2026-10-23');
  });

  test('accurately groups transactions by calendar date when reference date is Oct 29, 2026', () => {
    // When reference date is Oct 29, 2026:
    // - Oct 29 = Today
    // - Oct 28 = Yesterday
    // - Oct 25 = 25 October
    // - Oct 23 = 23 October
    const groups = groupTransactionsByDate(transactions, {
      referenceDate: new Date(2026, 9, 29),
      locale: 'en-IE',
    });

    assert.strictEqual(groups.length, 4);

    // Group 1: Today (Oct 29)
    assert.strictEqual(groups[0].title, 'Today');
    assert.strictEqual(groups[0].dateKey, '2026-10-29');
    assert.strictEqual(groups[0].transactions.length, 1);
    assert.strictEqual(groups[0].transactions[0].merchant, 'Carrefour');

    // Group 2: Yesterday (Oct 28)
    assert.strictEqual(groups[1].title, 'Yesterday');
    assert.strictEqual(groups[1].dateKey, '2026-10-28');
    assert.strictEqual(groups[1].transactions.length, 1);
    assert.strictEqual(groups[1].transactions[0].merchant, 'Spotify');

    // Group 3: 25 October (Oct 25)
    assert.strictEqual(groups[2].title, '25 October');
    assert.strictEqual(groups[2].dateKey, '2026-10-25');
    assert.strictEqual(groups[2].transactions.length, 1);
    assert.strictEqual(groups[2].transactions[0].merchant, 'Salary');

    // Group 4: 23 October (Oct 23)
    assert.strictEqual(groups[3].title, '23 October');
    assert.strictEqual(groups[3].dateKey, '2026-10-23');
    assert.strictEqual(groups[3].transactions.length, 1);
    assert.strictEqual(groups[3].transactions[0].merchant, 'Netflix');
  });

  test('accurately assigns calendar date headings when viewing from Nov 2, 2026 (no fake Today/Yesterday)', () => {
    // When viewed on November 2, 2026, transactions from October 29 & 28 must NOT be labeled Today/Yesterday
    const groups = groupTransactionsByDate(transactions, {
      referenceDate: new Date(2026, 10, 2), // Nov 2, 2026
      locale: 'en-IE',
    });

    assert.strictEqual(groups.length, 4);
    assert.strictEqual(groups[0].title, '29 October');
    assert.strictEqual(groups[1].title, '28 October');
    assert.strictEqual(groups[2].title, '25 October');
    assert.strictEqual(groups[3].title, '23 October');
  });

  test('groups multiple transactions occurring on the same calendar day together', () => {
    const multiTxList = [
      { id: 'tx-1', merchant: 'Morning Coffee', date: '2026-10-29', amountMinor: -450 },
      { id: 'tx-2', merchant: 'Carrefour', date: '2026-10-29', amountMinor: -5280 },
      { id: 'tx-3', merchant: 'Lunch Bistro', date: '2026-10-29', amountMinor: -1850 },
      { id: 'tx-4', merchant: 'Spotify', date: '2026-10-28', amountMinor: -1099 },
    ];

    const groups = groupTransactionsByDate(multiTxList, {
      referenceDate: new Date(2026, 9, 29),
      locale: 'en-IE',
    });

    assert.strictEqual(groups.length, 2);
    // Group 1: Today contains all 3 transactions from 2026-10-29
    assert.strictEqual(groups[0].title, 'Today');
    assert.strictEqual(groups[0].transactions.length, 3);
    assert.strictEqual(groups[0].transactions[0].merchant, 'Morning Coffee');
    assert.strictEqual(groups[0].transactions[1].merchant, 'Carrefour');
    assert.strictEqual(groups[0].transactions[2].merchant, 'Lunch Bistro');

    // Group 2: Yesterday contains Spotify
    assert.strictEqual(groups[1].title, 'Yesterday');
    assert.strictEqual(groups[1].transactions.length, 1);
    assert.strictEqual(groups[1].transactions[0].merchant, 'Spotify');
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
      : `minus ${(Math.abs(tx.amountMinor) / 100).toFixed(2)} euros`;

    const label = `${tx.merchant}, ${tx.category}, 29 October, ${accessibleAmount}`;

    assert.strictEqual(label, 'Carrefour, Groceries, 29 October, amount hidden');
    assert.ok(!label.includes('52.80'));
    assert.ok(!label.includes('5280'));
  });
});
