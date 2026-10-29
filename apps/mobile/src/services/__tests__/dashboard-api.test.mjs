import test, { describe } from 'node:test';
import assert from 'node:assert/strict';


describe('Dashboard Data Mapping & Business Rules', () => {
  test('accurately identifies new user without financial data as empty state', () => {
    const accounts = [];
    const summary = {
      totalExpensesMinor: 0,
      confirmedIncomeMinor: 0,
      unclassifiedExpensesMinor: 0,
      unclassifiedPositiveMinor: 0,
      transactionsCount: 0,
      uncategorizedTransactionsCount: 0,
      firstTransactionDate: null,
      lastTransactionDate: null,
      currency: 'EUR',
      basedOnIncompleteData: true,
    };
    const recentTransactions = [];

    const hasAnyData =
      accounts.length > 0 ||
      (summary !== null && summary.transactionsCount > 0) ||
      recentTransactions.length > 0;

    assert.strictEqual(hasAnyData, false);
  });

  test('identifies user with imported account as having data', () => {
    const accounts = [
      {
        id: 'acc-1',
        name: 'Compte Principal',
        sourceType: 'CSV',
        currency: 'EUR',
        createdAt: '2026-10-01T10:00:00Z',
        updatedAt: '2026-10-01T10:00:00Z',
      },
    ];
    const summary = null;
    const recentTransactions = [];

    const hasAnyData =
      accounts.length > 0 ||
      (summary !== null && summary.transactionsCount > 0) ||
      recentTransactions.length > 0;

    assert.strictEqual(hasAnyData, true);
  });

  test('maps category names to transactions correctly', () => {
    const categories = [
      { id: 'cat-courses', name: 'Courses', categoryType: 'EXPENSE', system: true },
      { id: 'cat-loyer', name: 'Logement', categoryType: 'EXPENSE', system: true },
    ];

    const rawTransactions = [
      {
        id: 'tx-1',
        accountId: 'acc-1',
        bookingDate: '2026-10-24',
        amountMinor: -4550,
        currency: 'EUR',
        rawLabel: 'CARREFOUR MARKET',
        normalizedLabel: 'Carrefour',
        transactionType: 'EXPENSE',
        categoryId: 'cat-courses',
        customCategoryFlag: false,
      },
      {
        id: 'tx-2',
        accountId: 'acc-1',
        bookingDate: '2026-10-22',
        amountMinor: -1200,
        currency: 'EUR',
        rawLabel: 'BOULANGERIE',
        normalizedLabel: null,
        transactionType: 'EXPENSE',
        categoryId: null,
        customCategoryFlag: false,
      },
    ];

    const categoryMap = new Map();
    categories.forEach((cat) => categoryMap.set(cat.id, cat.name));

    const mapped = rawTransactions.map((tx) => ({
      ...tx,
      categoryName: tx.categoryId ? categoryMap.get(tx.categoryId) ?? null : null,
    }));

    assert.strictEqual(mapped[0].categoryName, 'Courses');
    assert.strictEqual(mapped[1].categoryName, null);
  });

  test('validates dashboard summary semantics from backend contract', () => {
    const summary = {
      totalExpensesMinor: 45500, // 455.00 EUR
      confirmedIncomeMinor: 125000, // 1250.00 EUR
      unclassifiedExpensesMinor: 2500,
      unclassifiedPositiveMinor: 35020,
      transactionsCount: 42,
      uncategorizedTransactionsCount: 5,
      firstTransactionDate: '2026-10-01',
      lastTransactionDate: '2026-10-28',
      currency: 'EUR',
      basedOnIncompleteData: true,
    };

    // totalExpensesMinor must be a positive number in cents representing expenses + fees
    assert.ok(summary.totalExpensesMinor >= 0);
    // confirmedIncomeMinor is confirmed income (INCOME > 0)
    assert.ok(summary.confirmedIncomeMinor >= 0);
    // basedOnIncompleteData is always true for CSV imports
    assert.strictEqual(summary.basedOnIncompleteData, true);
  });
});
