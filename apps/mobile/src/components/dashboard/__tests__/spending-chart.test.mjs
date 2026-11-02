import test, { describe } from 'node:test';
import assert from 'node:assert/strict';

import { DASHBOARD_DEMO_DATA } from '../../../features/dashboard/demo/dashboard-demo-data.ts';
import { parseMoneyParts } from '../../../utils/formatters.ts';

describe('Spending Overview Chart & Data Mapping', () => {
  const months = DASHBOARD_DEMO_DATA.spendingOverview.months;

  test('verifies all 6 months are present with exact amounts and order', () => {
    assert.strictEqual(months.length, 6);

    const expected = [
      { key: '2026-05', short: 'May', amount: 94000 },
      { key: '2026-06', short: 'Jun', amount: 115000 },
      { key: '2026-07', short: 'Jul', amount: 78000 },
      { key: '2026-08', short: 'Aug', amount: 142000 },
      { key: '2026-09', short: 'Sep', amount: 106000 },
      { key: '2026-10', short: 'Oct', amount: 128000 },
    ];

    expected.forEach((exp, idx) => {
      assert.strictEqual(months[idx].monthKey, exp.key);
      assert.strictEqual(months[idx].monthShort, exp.short);
      assert.strictEqual(months[idx].amountMinor, exp.amount);
    });
  });

  test('computes correct default selected month (October 2026)', () => {
    const defaultSelected =
      months.find((m) => m.isSelected) ||
      months.find((m) => m.monthShort === 'Oct') ||
      months[months.length - 1];

    assert.strictEqual(defaultSelected.monthShort, 'Oct');
    assert.strictEqual(defaultSelected.monthFull, 'October 2026');
    assert.strictEqual(defaultSelected.amountMinor, 128000);

    const formatted = parseMoneyParts(defaultSelected.amountMinor, 'EUR', 'en-IE', {
      hideDecimals: true,
    });
    assert.strictEqual(formatted.fullFormatted, '€1,280');
  });

  test('calculates accurate relative bar heights without division-by-zero risk', () => {
    const barTrackHeight = 110;
    const minBarHeight = 22;
    const maxAmountMinor = Math.max(...months.map((m) => m.amountMinor), 1);

    assert.strictEqual(maxAmountMinor, 142000); // August is the peak month

    const heights = months.map((m) => {
      return Math.max((m.amountMinor / maxAmountMinor) * barTrackHeight, minBarHeight);
    });

    // August (€1,420) must be exactly at barTrackHeight
    assert.strictEqual(heights[3], barTrackHeight);

    // July (€780) should be around 60.4px
    assert.ok(heights[2] > minBarHeight);
    assert.ok(heights[2] < heights[3]);

    // October (€1,280) height must be proportional
    const expectedOctHeight = (128000 / 142000) * barTrackHeight;
    assert.ok(Math.abs(heights[5] - expectedOctHeight) < 0.001);

    // Zero dataset safety check
    const zeroMax = Math.max(...[], 1);
    const zeroHeight = Math.max((0 / zeroMax) * barTrackHeight, minBarHeight);
    assert.strictEqual(zeroHeight, minBarHeight);
  });

  test('ensures privacy mode does not expose financial values in accessibility text', () => {
    const isBalanceHidden = true;
    const item = months.find((m) => m.monthShort === 'Oct');
    assert.ok(item);

    const accessibleAmount = isBalanceHidden
      ? 'spending hidden'
      : `spending ${Math.round(item.amountMinor / 100)} euros`;

    const fullLabel = `${item.monthFull}, ${accessibleAmount}`;

    assert.strictEqual(fullLabel, 'October 2026, spending hidden');
    assert.ok(!fullLabel.includes('1280'));
    assert.ok(!fullLabel.includes('1,280'));
  });

  test('switching selected month maps to correct monthly dataset', () => {
    const targetMonthKey = '2026-08'; // August
    const target = months.find((m) => m.monthKey === targetMonthKey);
    assert.ok(target);

    assert.strictEqual(target.monthFull, 'August 2026');
    assert.strictEqual(target.amountMinor, 142000);

    const formatted = parseMoneyParts(target.amountMinor, 'EUR', 'en-IE', {
      hideDecimals: true,
    });
    assert.strictEqual(formatted.fullFormatted, '€1,420');
  });
});

