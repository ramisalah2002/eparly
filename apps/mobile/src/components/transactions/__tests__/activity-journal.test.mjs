import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import { getActivityCopy } from '../../../constants/activity-copy.ts';
import { groupTransactionsByDate } from '../../../utils/formatters.ts';

describe('Activity Screen — Editorial Activity Journal (Concept A+)', () => {
  const sampleTransactions = [
    {
      id: 'tx-1',
      accountId: 'acc-1',
      bookingDate: '2026-10-30T10:00:00Z',
      amountMinor: -4500,
      currency: 'EUR',
      rawLabel: 'CARREFOUR MARKET PARIS 11',
      normalizedLabel: 'Carrefour Market',
      transactionType: 'EXPENSE',
      categoryName: 'Courses',
      customCategoryFlag: false,
    },
    {
      id: 'tx-2',
      accountId: 'acc-1',
      bookingDate: '2026-10-30T14:30:00Z',
      amountMinor: -1799,
      currency: 'EUR',
      rawLabel: 'NETFLIX ENTERTAINMENT SVOD',
      normalizedLabel: 'Netflix',
      transactionType: 'EXPENSE',
      categoryName: 'Abonnements',
      customCategoryFlag: false,
    },
    {
      id: 'tx-3',
      accountId: 'acc-1',
      bookingDate: '2026-10-29T09:00:00Z',
      amountMinor: 245000,
      currency: 'EUR',
      rawLabel: 'VIREMENT SALAIRE ACME CORP',
      normalizedLabel: 'Salary',
      transactionType: 'INCOME',
      categoryName: 'Revenus',
      customCategoryFlag: false,
    },
  ];

  test('validates localized copy consistency without mixing languages', () => {
    const en = getActivityCopy('en-IE');
    const fr = getActivityCopy('fr-FR');

    // English consistency
    assert.strictEqual(en.title, 'Activity');
    assert.strictEqual(en.importCsv, 'Import CSV');
    assert.strictEqual(en.searchPlaceholder, 'Search merchants, notes, amounts...');
    assert.strictEqual(en.subscriptionBadge, 'Subscription');

    // French consistency
    assert.strictEqual(fr.title, 'Activité');
    assert.strictEqual(fr.importCsv, 'Importer CSV');
    assert.strictEqual(fr.searchPlaceholder, 'Rechercher commerçants, notes, montants...');
    assert.strictEqual(fr.subscriptionBadge, 'Abonnement');
  });

  test('chronologically groups multiple transactions occurring on the same day', () => {
    // Reference date set to 2026-10-30 (Today)
    const groups = groupTransactionsByDate(sampleTransactions, {
      referenceDate: new Date(2026, 9, 30),
      locale: 'en-IE',
    });

    assert.strictEqual(groups.length, 2);

    // Group 1: Oct 30 (Today) -> 2 transactions
    assert.strictEqual(groups[0].title, 'Today');
    assert.strictEqual(groups[0].transactions.length, 2);

    // Group 2: Oct 29 (Yesterday) -> 1 transaction
    assert.strictEqual(groups[1].title, 'Yesterday');
    assert.strictEqual(groups[1].transactions.length, 1);
  });

  test('calculates accurate net daily cashflow for calendar date sections', () => {
    const groups = groupTransactionsByDate(sampleTransactions, {
      referenceDate: new Date(2026, 9, 30),
      locale: 'en-IE',
    });

    const todayNet = groups[0].transactions.reduce((acc, tx) => acc + tx.amountMinor, 0);
    // -4500 + (-1799) = -6299 (-€62.99)
    assert.strictEqual(todayNet, -6299);

    const yesterdayNet = groups[1].transactions.reduce((acc, tx) => acc + tx.amountMinor, 0);
    // +245000 (+€2,450.00)
    assert.strictEqual(yesterdayNet, 245000);
  });

  test('identifies subscription transactions safely from reliable category classification', () => {
    const isSubscription = (tx) =>
      tx.categoryName?.toLowerCase().includes('abonnement') ||
      tx.categoryName?.toLowerCase().includes('subscription');

    assert.strictEqual(isSubscription(sampleTransactions[0]), false); // Groceries
    assert.strictEqual(isSubscription(sampleTransactions[1]), true);  // Netflix (Abonnements)
    assert.strictEqual(isSubscription(sampleTransactions[2]), false); // Salary
  });
});
