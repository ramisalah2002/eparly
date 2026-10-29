import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';
import { DashboardSummary } from '@/services/dashboard-api';
import { formatCurrencyMinor } from '@/utils/formatters';

interface IncomeExpenseCardsProps {
  summary: DashboardSummary | null;
  isBalanceHidden: boolean;
}

export function IncomeExpenseCards({ summary, isBalanceHidden }: IncomeExpenseCardsProps) {
  const confirmedIncome = summary?.confirmedIncomeMinor ?? 0;
  const totalExpenses = summary?.totalExpensesMinor ?? 0;

  return (
    <View style={styles.container}>
      {/* Income Card */}
      <View style={[styles.card, styles.incomeCard]}>
        <View style={styles.cardHeader}>
          <View style={styles.incomeIconBadge}>
            <Text style={styles.incomeArrowText}>↙</Text>
          </View>
          <Text style={styles.periodBadge}>Ce mois</Text>
        </View>

        <Text style={styles.cardLabel}>Revenus confirmés</Text>
        <Text style={[styles.cardAmount, styles.incomeAmount]}>
          {formatCurrencyMinor(confirmedIncome, 'EUR', { masked: isBalanceHidden })}
        </Text>
        <Text style={styles.cardDetail}>Revenus qualifiés</Text>
      </View>

      {/* Expenses Card */}
      <View style={[styles.card, styles.expenseCard]}>
        <View style={styles.cardHeader}>
          <View style={styles.expenseIconBadge}>
            <Text style={styles.expenseArrowText}>↗</Text>
          </View>
          <Text style={styles.periodBadge}>Ce mois</Text>
        </View>

        <Text style={styles.cardLabel}>Dépenses totales</Text>
        <Text style={styles.cardAmount}>
          {formatCurrencyMinor(totalExpenses, 'EUR', { masked: isBalanceHidden })}
        </Text>
        <Text style={styles.cardDetail}>Dépenses & frais</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    gap: Spacing.two + 4,
    marginBottom: Spacing.three,
  },
  card: {
    flex: 1,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 20,
    padding: Spacing.three,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    shadowColor: '#242535',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.04,
    shadowRadius: 8,
    elevation: 1,
  },
  incomeCard: {
    borderLeftWidth: 3,
    borderLeftColor: '#34A853',
  },
  expenseCard: {
    borderLeftWidth: 3,
    borderLeftColor: BRAND_COLORS.mutedLavender,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.two,
  },
  incomeIconBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: BRAND_COLORS.softMintBackground,
    justifyContent: 'center',
    alignItems: 'center',
  },
  incomeArrowText: {
    color: '#2E7D32',
    fontSize: 14,
    fontWeight: '800',
  },
  expenseIconBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
  },
  expenseArrowText: {
    color: '#7B6B96',
    fontSize: 14,
    fontWeight: '800',
  },
  periodBadge: {
    fontSize: 10,
    color: BRAND_COLORS.secondaryText,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    fontWeight: '500',
  },
  cardLabel: {
    fontSize: 12,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    marginBottom: 4,
  },
  cardAmount: {
    fontSize: 19,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    fontVariant: ['tabular-nums'],
    letterSpacing: -0.4,
  },
  incomeAmount: {
    color: '#1B5E20',
  },
  cardDetail: {
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    marginTop: 4,
  },
});
