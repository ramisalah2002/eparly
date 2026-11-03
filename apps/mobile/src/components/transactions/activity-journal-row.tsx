import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { Repeat2 } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { MoneyAmount } from '@/components/dashboard/money-amount';
import { getCategoryTheme } from '@/constants/category-theme';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { getActivityCopy } from '@/constants/activity-copy';
import { TransactionItem } from '@/services/dashboard-api';

interface ActivityJournalRowProps {
  transaction: TransactionItem;
  locale?: string;
  isLastInGroup?: boolean;
  onPress: (transaction: TransactionItem) => void;
}

/**
 * Editorial Transaction Journal Row (Concept A+).
 * Eliminates large red/green card surfaces in favor of a clean, neutral row
 * featuring a 38px soft-tinted category icon squircle, baseline-aligned
 * Space Grotesk tabular numerals, and an understated recurring subscription indicator.
 */
export function ActivityJournalRow({
  transaction,
  locale = 'en-IE',
  isLastInGroup = false,
  onPress,
}: ActivityJournalRowProps) {
  const isReducedMotion = useReducedMotion();
  const copy = getActivityCopy(locale);
  const isPositive = transaction.amountMinor > 0;
  const theme = getCategoryTheme(transaction.categoryName, isPositive);
  const IconComponent = theme.icon;

  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.cardPress;

  // Use normalized merchant label when reliable, otherwise raw bank label
  const displayLabel = transaction.normalizedLabel || transaction.rawLabel || copy.expenseType;
  const categoryLabel = transaction.categoryName || copy.showAllCategories;

  // Safely detect subscription only when categorized as subscription
  const isSubscription =
    transaction.categoryName?.toLowerCase().includes('abonnement') ||
    transaction.categoryName?.toLowerCase().includes('subscription');

  const accessibleAmount = `${isPositive ? '+' : '-'} ${(Math.abs(transaction.amountMinor) / 100).toFixed(2)} ${transaction.currency || 'EUR'}`;

  return (
    <View style={styles.rowWrapper}>
      <AnimatedPressable
        onPress={() => onPress(transaction)}
        scaleTarget={scalePress}
        activeOpacity={0.94}
        haptic="light"
        style={styles.pressableRow}
        accessibilityRole="button"
        accessibilityLabel={`${displayLabel}, ${categoryLabel}, ${accessibleAmount}`}>
        {/* Category Icon Squircle */}
        <View style={[styles.iconContainer, { backgroundColor: theme.backgroundColor }]}>
          <IconComponent size={18} color={theme.color} strokeWidth={2.2} />
        </View>

        {/* Merchant & Classification Context */}
        <View style={styles.detailsContainer}>
          <Text style={styles.merchantName} numberOfLines={1}>
            {displayLabel}
          </Text>

          <View style={styles.metaRow}>
            <Text style={styles.categoryText} numberOfLines={1}>
              {categoryLabel}
            </Text>

            {isSubscription && (
              <View style={styles.subscriptionBadge}>
                <Repeat2 size={10} color={BRAND_COLORS.deepLavender} strokeWidth={2.4} />
                <Text style={styles.subscriptionText}>{copy.subscriptionBadge}</Text>
              </View>
            )}
          </View>
        </View>

        {/* Tabular Baseline-Aligned Amount */}
        <View style={styles.amountContainer}>
          <MoneyAmount
            amountMinor={transaction.amountMinor}
            currency={transaction.currency || 'EUR'}
            locale={locale}
            size="row"
            showSign={isPositive}
            color={isPositive ? '#1E7558' : BRAND_COLORS.darkCharcoal}
            accessibilityLabelPrefix={displayLabel}
          />
        </View>
      </AnimatedPressable>

      {/* Hairline Row Separator */}
      {!isLastInGroup && <View style={styles.rowDivider} />}
    </View>
  );
}

const styles = StyleSheet.create({
  rowWrapper: {
    backgroundColor: BRAND_COLORS.pureWhite,
  },
  pressableRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 11,
    paddingHorizontal: 16,
    borderRadius: 14,
  },
  iconContainer: {
    width: 38,
    height: 38,
    borderRadius: 13,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  detailsContainer: {
    flex: 1,
    paddingRight: 10,
    justifyContent: 'center',
  },
  merchantName: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 14.5,
    fontWeight: '500',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.15,
    marginBottom: 2,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  categoryText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
  },
  subscriptionBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    backgroundColor: 'rgba(128, 103, 161, 0.08)',
    paddingHorizontal: 6,
    paddingVertical: 1.5,
    borderRadius: 6,
  },
  subscriptionText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 10.5,
    color: BRAND_COLORS.deepLavender,
    fontWeight: '500',
  },
  amountContainer: {
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  rowDivider: {
    height: 1,
    backgroundColor: 'rgba(36, 37, 53, 0.05)',
    marginLeft: 66,
    marginRight: 16,
  },
});

