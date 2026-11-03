import React from 'react';
import {
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, { FadeIn } from 'react-native-reanimated';
import { ArrowDownLeft, ArrowUpRight, Eye, EyeOff } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import {
  DemoBalanceOverview,
  DemoCashFlow,
} from '@/features/dashboard/demo/dashboard-demo-data';

import { MoneyAmount } from './money-amount';

interface FinancialOverviewCardProps {
  balance: DemoBalanceOverview;
  cashFlow: DemoCashFlow;
  isBalanceHidden: boolean;
  onToggleBalanceVisibility: () => void;
}

/**
 * Eparly Editorial Financial Overview Card (Direction A with subtle tonal styling).
 * Natural continuation of The Quiet Monograph design language:
 * - Single unified surface with a soft lavender-pearl tonal base (#FAF9FB)
 * - Large, confident 36px hero balance with baseline-aligned decimals
 * - Integrated period-aware cash flow horizon with restrained mint & lavender accents
 * - Fully dynamic period derivation, zero nested cards, and accessible 44x44 touch target
 */
export function FinancialOverviewCard({
  balance,
  cashFlow,
  isBalanceHidden,
  onToggleBalanceVisibility,
}: FinancialOverviewCardProps) {
  // Dynamically derive current period if not provided by backend contract
  const dynamicPeriod =
    cashFlow.periodLabel?.trim() ||
    new Intl.DateTimeFormat('en-US', { month: 'long' }).format(new Date());

  return (
    <View style={styles.cardContainer}>
      {/* 1. Header Row: Understated Brand Eyebrow & Accessible Privacy Toggle */}
      <View style={styles.headerRow}>
        <View style={styles.eyebrowChip}>
          <View style={styles.brandDot} />
          <Text style={styles.eyebrowText}>Total balance</Text>
        </View>

        <AnimatedPressable
          onPress={onToggleBalanceVisibility}
          haptic="selection"
          scaleTarget={MOTION.scale.buttonPress}
          style={styles.eyeButton}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          accessibilityLabel={isBalanceHidden ? 'Show balance' : 'Hide balance'}
          accessibilityRole="button">
          <Animated.View key={isBalanceHidden ? 'eye-hidden' : 'eye-visible'} entering={FadeIn.duration(140)}>
            {isBalanceHidden ? (
              <Eye size={18} color={BRAND_COLORS.darkCharcoal} strokeWidth={2} />
            ) : (
              <EyeOff size={18} color={BRAND_COLORS.darkCharcoal} strokeWidth={2} />
            )}
          </Animated.View>
        </AnimatedPressable>
      </View>

      {/* 2. Hero Monetary Section: Bold Amount & Account Scope */}
      <View style={styles.balanceContainer}>
        <Animated.View
          key={isBalanceHidden ? 'bal-hidden' : 'bal-visible'}
          entering={FadeIn.duration(160)}>
          <MoneyAmount
            amountMinor={balance.totalBalanceMinor}
            currency={balance.currency || 'EUR'}
            locale="en-IE"
            isHidden={isBalanceHidden}
            size="hero"
            accessibilityLabelPrefix="Total balance"
          />
        </Animated.View>
        <View style={styles.accountScopeRow}>
          <Text style={styles.accountsDetail}>{balance.accountsLabel}</Text>
        </View>
      </View>

      {/* 3. Subtle Hairline Horizon Separator */}
      <View style={styles.separator} />

      {/* 4. Integrated Cash Flow Horizon: Restrained Mint & Lavender Indicators */}
      <View style={styles.cashFlowRow}>
        {/* Income Column */}
        <View style={styles.cashFlowColumn}>
          <View style={styles.cashFlowLabelRow}>
            <View style={[styles.directionBadge, styles.incomeBadge]}>
              <ArrowDownLeft size={11} color="#206E54" strokeWidth={2.4} />
            </View>
            <Text style={styles.flowTypeLabel}>Income</Text>
            <Text style={styles.flowPeriodTag}>· {dynamicPeriod}</Text>
          </View>
          <Animated.View
            key={isBalanceHidden ? 'income-hidden' : 'income-visible'}
            entering={FadeIn.duration(160)}
            style={styles.amountWrapper}>
            <MoneyAmount
              amountMinor={cashFlow.incomeMinor}
              currency={balance.currency || 'EUR'}
              locale="en-IE"
              isHidden={isBalanceHidden}
              size="subcard"
              color={BRAND_COLORS.darkCharcoal}
              accessibilityLabelPrefix={`Income for ${dynamicPeriod}`}
            />
          </Animated.View>
        </View>

        {/* Vertical subtle divider between columns */}
        <View style={styles.verticalDivider} />

        {/* Expenses Column */}
        <View style={styles.cashFlowColumn}>
          <View style={styles.cashFlowLabelRow}>
            <View style={[styles.directionBadge, styles.expenseBadge]}>
              <ArrowUpRight size={11} color="#755899" strokeWidth={2.4} />
            </View>
            <Text style={styles.flowTypeLabel}>Expenses</Text>
            <Text style={styles.flowPeriodTag}>· {dynamicPeriod}</Text>
          </View>
          <Animated.View
            key={isBalanceHidden ? 'expense-hidden' : 'expense-visible'}
            entering={FadeIn.duration(160)}
            style={styles.amountWrapper}>
            <MoneyAmount
              amountMinor={cashFlow.expensesMinor}
              currency={balance.currency || 'EUR'}
              locale="en-IE"
              isHidden={isBalanceHidden}
              size="subcard"
              color={BRAND_COLORS.darkCharcoal}
              accessibilityLabelPrefix={`Expenses for ${dynamicPeriod}`}
            />
          </Animated.View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  cardContainer: {
    backgroundColor: '#FAF9FB',
    borderRadius: 24,
    padding: 20,
    paddingBottom: 18,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.03,
    shadowRadius: 10,
    elevation: 2,
    marginBottom: 14,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  eyebrowChip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(240, 236, 245, 0.75)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    gap: 6,
  },
  brandDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: BRAND_COLORS.deepLavender,
  },
  eyebrowText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '500',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: -0.1,
  },
  eyeButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
    borderWidth: 1,
    borderColor: 'rgba(36, 37, 53, 0.08)',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.03,
    shadowRadius: 3,
    elevation: 1,
  },
  balanceContainer: {
    marginTop: 10,
    marginBottom: 4,
  },
  accountScopeRow: {
    marginTop: 4,
  },
  accountsDetail: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
  },
  separator: {
    height: 1,
    backgroundColor: 'rgba(168, 155, 190, 0.14)',
    marginTop: 16,
    marginBottom: 14,
    width: '100%',
  },
  cashFlowRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  cashFlowColumn: {
    flex: 1,
  },
  verticalDivider: {
    width: 1,
    height: 38,
    backgroundColor: 'rgba(168, 155, 190, 0.14)',
    marginHorizontal: 16,
  },
  cashFlowLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 4,
  },
  directionBadge: {
    width: 18,
    height: 18,
    borderRadius: 9,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 2,
  },
  incomeBadge: {
    backgroundColor: '#E8F6F1',
    borderWidth: 1,
    borderColor: 'rgba(89, 185, 155, 0.25)',
  },
  expenseBadge: {
    backgroundColor: '#F2ECF8',
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.25)',
  },
  flowTypeLabel: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12,
    fontWeight: '500',
    color: '#3A3B4A',
    letterSpacing: -0.1,
  },
  flowPeriodTag: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
  },
  amountWrapper: {
    paddingLeft: 2,
  },
});
