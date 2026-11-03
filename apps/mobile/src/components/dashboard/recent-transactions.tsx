import React from 'react';
import { useRouter } from 'expo-router';
import {
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, { FadeInUp } from 'react-native-reanimated';
import {
  ArrowUpRight,
  BriefcaseBusiness,
  CreditCard,
  Inbox,
  Music2,
  Play,
  ShoppingCart,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import { DemoTransaction } from '@/features/dashboard/demo/dashboard-demo-data';
import { groupTransactionsByDate } from '@/utils/formatters';

import { MoneyAmount } from './money-amount';

interface RecentTransactionsProps {
  transactions: DemoTransaction[];
  title?: string;
  subtitle?: string;
  isBalanceHidden?: boolean;
  locale?: string;
  referenceDate?: Date | string;
  onSeeAllPress?: () => void;
}

/**
 * Eparly Editorial Transaction Journal (Concept A).
 * - Chronological grouping by real calendar day ("Today", "Yesterday", "29 October")
 * - Refined 38px soft-tinted squircle category icons
 * - Space Grotesk typography with baseline-aligned tabular amounts
 * - Seamless "See all" minimalist text action with accessible >=44pt hitSlop
 * - Zero redundant "Payment" or repeated date labels
 */
export function RecentTransactions({
  transactions = [],
  title = 'Your activity',
  subtitle = 'The latest money movements',
  isBalanceHidden = false,
  locale = 'en-IE',
  referenceDate,
  onSeeAllPress,
}: RecentTransactionsProps) {
  const router = useRouter();

  const handleSeeAll = () => {
    if (onSeeAllPress) {
      onSeeAllPress();
    } else {
      router.push('/(tabs)/transactions');
    }
  };

  const getTransactionIcon = (iconName: string, isPositive: boolean) => {
    const iconColor = isPositive ? '#1E7558' : BRAND_COLORS.deepLavender;
    const size = 17;
    const strokeWidth = 2.1;

    switch (iconName) {
      case 'shopping':
        return <ShoppingCart size={size} color={iconColor} strokeWidth={strokeWidth} />;
      case 'music':
        return <Music2 size={size} color={iconColor} strokeWidth={strokeWidth} />;
      case 'briefcase':
        return <BriefcaseBusiness size={size} color={iconColor} strokeWidth={strokeWidth} />;
      case 'play':
        return <Play size={size} color={iconColor} strokeWidth={strokeWidth} />;
      default:
        return isPositive ? (
          <BriefcaseBusiness size={size} color={iconColor} strokeWidth={strokeWidth} />
        ) : (
          <CreditCard size={size} color={iconColor} strokeWidth={strokeWidth} />
        );
    }
  };

  const getContainerBg = (iconName: string, isPositive: boolean, customBg?: string) => {
    if (customBg) return customBg;
    if (isPositive) return '#E8F6F1';
    switch (iconName) {
      case 'music':
      case 'play':
        return '#F3EFF8';
      case 'shopping':
        return '#F1EEF6';
      default:
        return '#F2EFF7';
    }
  };

  // Group transactions by calendar day relative to the current reference date
  const groupedTransactions = groupTransactionsByDate(transactions, {
    referenceDate,
    locale,
  });

  return (
    <View style={styles.cardContainer}>
      {/* 1. Section Header: Title, Subtitle, and Minimalist See All Action */}
      <View style={styles.headerRow}>
        <View style={styles.titleColumn}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>{subtitle}</Text>
        </View>

        <AnimatedPressable
          onPress={handleSeeAll}
          scaleTarget={MOTION.scale.buttonPress}
          haptic="selection"
          style={styles.seeAllAction}
          hitSlop={{ top: 12, bottom: 12, left: 14, right: 14 }}
          accessibilityLabel="See all transactions"
          accessibilityRole="button">
          <Text style={styles.seeAllText}>See all</Text>
          <ArrowUpRight size={14} color={BRAND_COLORS.deepLavender} strokeWidth={2.4} />
        </AnimatedPressable>
      </View>

      {/* 2. Transaction Groups or Empty State */}
      {transactions.length === 0 ? (
        <View style={styles.emptyContainer}>
          <View style={styles.emptyIconCircle}>
            <Inbox size={26} color={BRAND_COLORS.deepLavender} strokeWidth={1.8} />
          </View>
          <Text style={styles.emptyTitle}>No activity recorded</Text>
          <Text style={styles.emptyDescription}>
            Your recent transactions will appear here as soon as payments or income are logged.
          </Text>
        </View>
      ) : (
        <View style={styles.journalContainer}>
          {groupedTransactions.map((group, groupIndex) => (
            <View key={group.id} style={styles.groupSection}>
              {/* Group Date Header: Clean, understated natural-language heading */}
              <View style={styles.dateHeaderRow}>
                <Text style={styles.dateHeadingText}>{group.title}</Text>
              </View>

              {/* Group Transaction Rows */}
              <View style={styles.groupRowsContainer}>
                {group.transactions.map((tx, txIndex) => {
                  const isPositive = tx.amountMinor > 0;
                  const isLastInGroup = txIndex === group.transactions.length - 1;
                  const globalIndex = groupIndex * 3 + txIndex;

                  const accessibleAmount = isBalanceHidden
                    ? 'amount hidden'
                    : `${isPositive ? 'plus' : 'minus'} ${(Math.abs(tx.amountMinor) / 100).toFixed(2)} euros`;

                  return (
                    <Animated.View
                      key={tx.id}
                      entering={FadeInUp.duration(360).delay(globalIndex * 35)}>
                      <AnimatedPressable
                        onPress={handleSeeAll}
                        scaleTarget={0.985}
                        activeOpacity={0.94}
                        haptic="light"
                        style={styles.transactionRow}
                        accessibilityRole="button"
                        accessibilityLabel={`${tx.merchant}, ${tx.category}, ${group.title}, ${accessibleAmount}`}>
                        {/* Left: 38px Soft-Tinted Squircle Icon Container */}
                        <View
                          style={[
                            styles.iconContainer,
                            {
                              backgroundColor: getContainerBg(
                                tx.iconName,
                                isPositive,
                                tx.iconBg
                              ),
                            },
                          ]}>
                          {getTransactionIcon(tx.iconName, isPositive)}
                        </View>

                        {/* Center: Merchant Name & Clean Category Context */}
                        <View style={styles.detailsContainer}>
                          <Text style={styles.merchantName} numberOfLines={1}>
                            {tx.merchant}
                          </Text>
                          <Text style={styles.categoryText} numberOfLines={1}>
                            {tx.category}
                          </Text>
                        </View>

                        {/* Right: Tabular Amount with Baseline Alignment */}
                        <View style={styles.amountContainer}>
                          <MoneyAmount
                            amountMinor={tx.amountMinor}
                            currency={tx.currency || 'EUR'}
                            locale={locale}
                            isHidden={isBalanceHidden}
                            size="row"
                            showSign={isPositive}
                            color={isPositive ? '#1E7558' : BRAND_COLORS.darkCharcoal}
                            accessibilityLabelPrefix={`${tx.merchant} ${tx.category}`}
                          />
                        </View>
                      </AnimatedPressable>

                      {/* Hairline Row Divider (only between rows inside the same date group) */}
                      {!isLastInGroup && <View style={styles.rowDivider} />}
                    </Animated.View>
                  );
                })}
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  cardContainer: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 24,
    paddingHorizontal: 20,
    paddingTop: 20,
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
    alignItems: 'flex-start',
    marginBottom: 4,
  },
  titleColumn: {
    flex: 1,
    paddingRight: 10,
  },
  title: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 20,
    lineHeight: 25,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.3,
  },
  subtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
    letterSpacing: -0.1,
  },
  seeAllAction: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingVertical: 4,
    paddingHorizontal: 2,
  },
  seeAllText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 13,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: -0.1,
  },
  actionPressed: {
    opacity: 0.65,
    transform: [{ scale: 0.98 }],
  },
  journalContainer: {
    marginTop: 6,
  },
  groupSection: {
    marginTop: 6,
  },
  dateHeaderRow: {
    paddingHorizontal: 2,
    paddingTop: 8,
    paddingBottom: 4,
  },
  dateHeadingText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12.5,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
  },
  groupRowsContainer: {
    marginTop: 2,
  },
  transactionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 4,
    minHeight: 54,
    borderRadius: 12,
  },
  rowPressed: {
    backgroundColor: 'rgba(36, 37, 53, 0.025)',
  },
  iconContainer: {
    width: 38,
    height: 38,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  detailsContainer: {
    flex: 1,
    paddingRight: Spacing.two,
  },
  merchantName: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 15,
    fontWeight: '500',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  categoryText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12.5,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  amountContainer: {
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  rowDivider: {
    height: 1,
    backgroundColor: 'rgba(36, 37, 53, 0.05)',
    marginLeft: 54,
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 28,
    paddingHorizontal: 16,
  },
  emptyIconCircle: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: 'rgba(240, 236, 245, 0.65)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 10,
  },
  emptyTitle: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 4,
  },
  emptyDescription: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    maxWidth: 240,
  },
});
