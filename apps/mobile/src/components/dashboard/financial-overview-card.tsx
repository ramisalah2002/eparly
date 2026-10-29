import React from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Eye, EyeOff } from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';
import {
  DemoBalanceOverview,
  DemoCashFlow,
} from '@/features/dashboard/demo/dashboard-demo-data';

interface FinancialOverviewCardProps {
  balance: DemoBalanceOverview;
  cashFlow: DemoCashFlow;
  isBalanceHidden: boolean;
  onToggleBalanceVisibility: () => void;
}

export function FinancialOverviewCard({
  balance,
  cashFlow,
  isBalanceHidden,
  onToggleBalanceVisibility,
}: FinancialOverviewCardProps) {
  const formatAmount = (amountMinor: number) => {
    if (isBalanceHidden) return '••••••';
    const major = amountMinor / 100;
    return new Intl.NumberFormat('en-IE', {
      style: 'currency',
      currency: balance.currency || 'EUR',
      minimumFractionDigits: 2,
    }).format(major);
  };

  return (
    <View style={styles.cardContainer}>
      {/* Subtle decorative watermark accent */}
      <View style={styles.watermarkCircle} pointerEvents="none" />

      {/* Top Header Row: Label & Vector Eye Toggle */}
      <View style={styles.topRow}>
        <View style={styles.labelBadge}>
          <View style={styles.activeDot} />
          <Text style={styles.topLabel}>Total balance</Text>
        </View>

        <TouchableOpacity
          onPress={onToggleBalanceVisibility}
          activeOpacity={0.7}
          style={styles.eyeButton}
          accessibilityLabel={isBalanceHidden ? 'Show balance' : 'Hide balance'}
          accessibilityRole="button">
          {isBalanceHidden ? (
            <Eye size={17} color={BRAND_COLORS.darkCharcoal} strokeWidth={2.2} />
          ) : (
            <EyeOff size={17} color={BRAND_COLORS.darkCharcoal} strokeWidth={2.2} />
          )}
        </TouchableOpacity>
      </View>

      {/* Main Balance Display */}
      <View style={styles.balanceContainer}>
        <Text style={styles.balanceValue}>
          {formatAmount(balance.totalBalanceMinor)}
        </Text>
        <Text style={styles.accountsDetail}>{balance.accountsLabel}</Text>
      </View>

      {/* Embedded Income & Expenses Cards matching Quicken reference design */}
      <View style={styles.cashFlowRow}>
        {/* Income Card */}
        <Pressable
          style={({ pressed }) => [
            styles.cashFlowCard,
            styles.incomeCard,
            pressed && styles.cardPressed,
          ]}
          accessibilityLabel={`Income: ${formatAmount(cashFlow.incomeMinor)}`}>
          <View style={styles.flowLeftContent}>
            <Text style={styles.flowAmount}>
              {formatAmount(cashFlow.incomeMinor)}
            </Text>
            <Text style={styles.flowLabel}>Income</Text>
          </View>
          <Text style={styles.flowSign}>+</Text>
        </Pressable>

        {/* Expenses Card */}
        <Pressable
          style={({ pressed }) => [
            styles.cashFlowCard,
            styles.expenseCard,
            pressed && styles.cardPressed,
          ]}
          accessibilityLabel={`Expenses: ${formatAmount(cashFlow.expensesMinor)}`}>
          <View style={styles.flowLeftContent}>
            <Text style={styles.flowAmount}>
              {formatAmount(cashFlow.expensesMinor)}
            </Text>
            <Text style={styles.flowLabel}>Expenses</Text>
          </View>
          <Text style={styles.flowSign}>−</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  cardContainer: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 24,
    padding: Spacing.four,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 12,
    elevation: 2,
    marginBottom: Spacing.three,
    position: 'relative',
    overflow: 'hidden',
  },
  watermarkCircle: {
    position: 'absolute',
    top: -30,
    right: -30,
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: 'rgba(240, 236, 245, 0.40)',
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  labelBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.veryLightLavender,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
  },
  activeDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: BRAND_COLORS.deepLavender,
    marginRight: 6,
  },
  topLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: 0.2,
  },
  eyeButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  balanceContainer: {
    marginVertical: 4,
  },
  balanceValue: {
    fontSize: 32,
    fontWeight: '800',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.6,
    fontVariant: ['tabular-nums'],
  },
  accountsDetail: {
    fontSize: 12,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  cashFlowRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 16,
  },
  cashFlowCard: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingVertical: 18,
    paddingHorizontal: 16,
    borderRadius: 20,
    minHeight: 88,
  },
  cardPressed: {
    transform: [{ scale: 0.98 }],
    opacity: 0.9,
  },
  incomeCard: {
    backgroundColor: '#E2F7EC',
  },
  expenseCard: {
    backgroundColor: '#E4DCF8',
  },
  flowLeftContent: {
    flex: 1,
    paddingRight: 6,
  },
  flowAmount: {
    fontSize: 16,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.3,
    fontVariant: ['tabular-nums'],
    marginBottom: 4,
  },
  flowLabel: {
    fontSize: 13,
    fontWeight: '400',
    color: '#555566',
  },
  flowSign: {
    fontSize: 22,
    fontWeight: '400',
    color: BRAND_COLORS.darkCharcoal,
    lineHeight: 22,
    marginTop: -1,
  },
});
