import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import { DASHBOARD_DEMO_DATA } from '../dashboard-demo-data.ts';

describe('Dashboard Demo Data Consistency', () => {
  test('validates total balance and account details', () => {
    assert.strictEqual(DASHBOARD_DEMO_DATA.user.name, 'Alex');
    assert.strictEqual(DASHBOARD_DEMO_DATA.balance.totalBalanceMinor, 845050); // €8,450.50
    assert.strictEqual(DASHBOARD_DEMO_DATA.balance.accountsLabel, 'Across 2 accounts');
    assert.strictEqual(DASHBOARD_DEMO_DATA.balance.currency, 'EUR');
  });

  test('validates income and expenses cash flow values', () => {
    assert.strictEqual(DASHBOARD_DEMO_DATA.cashFlow.incomeMinor, 345000); // €3,450.00
    assert.strictEqual(DASHBOARD_DEMO_DATA.cashFlow.expensesMinor, 128000); // €1,280.00
  });

  test('ensures category breakdown matches total expenses exactly', () => {
    const categories = DASHBOARD_DEMO_DATA.categoryBreakdown.categories;
    const totalMinor = categories.reduce((sum, cat) => sum + cat.amountMinor, 0);

    assert.strictEqual(totalMinor, DASHBOARD_DEMO_DATA.cashFlow.expensesMinor);
    assert.strictEqual(totalMinor, 128000); // €1,280.00
  });

  test('validates monthly spending dataset and October selection', () => {
    const months = DASHBOARD_DEMO_DATA.spendingOverview.months;
    assert.strictEqual(months.length, 6);

    const map = new Map(months.map((m) => [m.monthShort, m.amountMinor]));
    assert.strictEqual(map.get('May'), 94000); // €940
    assert.strictEqual(map.get('Jun'), 115000); // €1,150
    assert.strictEqual(map.get('Jul'), 78000); // €780
    assert.strictEqual(map.get('Aug'), 142000); // €1,420
    assert.strictEqual(map.get('Sep'), 106000); // €1,060
    assert.strictEqual(map.get('Oct'), 128000); // €1,280

    const selected = months.find((m) => m.isSelected);
    assert.strictEqual(selected?.monthShort, 'Oct');
  });

  test('validates 4 demo transactions and amount signs', () => {
    const txs = DASHBOARD_DEMO_DATA.recentTransactions.transactions;
    assert.strictEqual(txs.length, 4);

    assert.strictEqual(txs[0].merchant, 'Carrefour');
    assert.strictEqual(txs[0].amountMinor, -5280);

    assert.strictEqual(txs[1].merchant, 'Spotify');
    assert.strictEqual(txs[1].amountMinor, -1099);

    assert.strictEqual(txs[2].merchant, 'Salary');
    assert.strictEqual(txs[2].amountMinor, 285000); // +€2,850.00

    assert.strictEqual(txs[3].merchant, 'Netflix');
    assert.strictEqual(txs[3].amountMinor, -1349);
  });
});
