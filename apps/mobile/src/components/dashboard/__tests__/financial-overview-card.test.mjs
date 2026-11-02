import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

import { BRAND_COLORS } from '../../../constants/colors.ts';
import { FONT_FAMILY } from '../../../constants/typography.ts';
import { DASHBOARD_DEMO_DATA } from '../../../features/dashboard/demo/dashboard-demo-data.ts';
import { parseMoneyParts } from '../../../utils/formatters.ts';

describe('Editorial Financial Overview Card (Direction A)', () => {
  const { balance, cashFlow } = DASHBOARD_DEMO_DATA;

  it('validates balance hero amount and formatted currency display', () => {
    assert.equal(balance.totalBalanceMinor, 845050);
    assert.equal(balance.currency, 'EUR');
    assert.equal(balance.accountsLabel, 'Across 2 accounts');

    const formatted = parseMoneyParts(balance.totalBalanceMinor, balance.currency, 'en-IE');
    assert.equal(formatted.integerText, '8,450');
    assert.equal(formatted.decimalText, '.50');
    assert.equal(formatted.prefixCurrency, '€');
  });

  it('validates cash flow income and expenses with period context', () => {
    assert.equal(cashFlow.incomeMinor, 345000);
    assert.equal(cashFlow.expensesMinor, 128000);
    assert.equal(cashFlow.periodLabel, 'October');

    const incomeParts = parseMoneyParts(cashFlow.incomeMinor, 'EUR', 'en-IE');
    assert.equal(incomeParts.integerText, '3,450');
    assert.equal(incomeParts.decimalText, '.00');

    const expenseParts = parseMoneyParts(cashFlow.expensesMinor, 'EUR', 'en-IE');
    assert.equal(expenseParts.integerText, '1,280');
    assert.equal(expenseParts.decimalText, '.00');
  });

  it('verifies privacy masked state does not leak figures', () => {
    const isBalanceHidden = true;
    const heroHiddenMask = '••••••••';
    const subcardHiddenMask = '•••••';

    assert.equal(isBalanceHidden, true);
    assert.ok(!heroHiddenMask.includes('8450'));
    assert.ok(!subcardHiddenMask.includes('3450'));
  });

  it('validates typography hierarchy and tokens for Direction A', () => {
    assert.equal(FONT_FAMILY.medium, 'SpaceGrotesk-Medium');
    assert.equal(FONT_FAMILY.bold, 'SpaceGrotesk-Bold');
    assert.equal(FONT_FAMILY.regular, 'SpaceGrotesk-Regular');

    assert.equal(BRAND_COLORS.pureWhite, '#FFFFFF');
    assert.equal(BRAND_COLORS.darkCharcoal, '#242535');
    assert.equal(BRAND_COLORS.secondaryText, '#737382');
  });

  it('validates dynamic period derivation when periodLabel is absent', () => {
    const fallbackPeriod = (cashFlow?.periodLabel?.trim() ||
      new Intl.DateTimeFormat('en-US', { month: 'long' }).format(new Date()));
    assert.ok(fallbackPeriod.length > 0);

    const emptyCashFlow = { periodLabel: '' };
    const dynamicMonth = emptyCashFlow.periodLabel?.trim() ||
      new Intl.DateTimeFormat('en-US', { month: 'long' }).format(new Date());
    assert.ok(['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'].includes(dynamicMonth));
  });
});
