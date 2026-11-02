import React from 'react';
import { useRouter } from 'expo-router';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, { FadeInUp } from 'react-native-reanimated';
import {
  ArrowUpRight,
  BriefcaseBusiness,
  Inbox,
  Music2,
  Play,
  ShoppingCart,
} from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
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
  onSeeAllPress?: () => void;
}

/**
 * Eparly Activity Feed (Recent Transactions).
 * Features an editorial header with active See-All navigation,
 * calendar-accurate date grouping ("Today", "Yesterday", "Earlier"),
 * compact transaction rows with vector iconography and MoneyAmount typography,
 * calm pastel tints, accessibility compliance, and empty-state support.
 */
export function RecentTransactions({
  transactions = [],
  title = 'Your activity',
  subtitle = 'The latest money movements',
  isBalanceHidden = false,
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
    const color = isPositive ? '#23755A' : BRAND_COLORS.deepLavender;
    const size = 19;
    const strokeWidth = 2.2;

    switch (iconName) {
      case 'shopping':
        return <ShoppingCart size={size} color={color} strokeWidth={strokeWidth} />;
      case 'music':
        return <Music2 size={size} color={color} strokeWidth={strokeWidth} />;
      case 'briefcase':
        return <BriefcaseBusiness size={size} color={color} strokeWidth={strokeWidth} />;
      case 'play':
        return <Play size={size} color={color} strokeWidth={strokeWidth} />;
      default:
        return <ShoppingCart size={size} color={color} strokeWidth={strokeWidth} />;
    }
  };

  // Group transactions by calendar day relative to demo reference date
  const groupedTransactions = groupTransactionsByDate(transactions);

  return (
    <View style={styles.cardContainer}>
      {/* 1. Section Header: Title, Subtitle, and See All Action */}
      <View style={styles.headerRow}>
        <View style={styles.titleColumn}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>{subtitle}</Text>
        </View>

        <Pressable
          onPress={handleSeeAll}
          style={({ pressed }) => [
            styles.seeAllButton,
            pressed && styles.buttonPressed,
          ]}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          accessibilityLabel="See all transactions"
          accessibilityRole="button">
          <Text style={styles.seeAllText}>See all</Text>
          <ArrowUpRight size={13} color={BRAND_COLORS.deepLavender} strokeWidth={2.4} />
        </Pressable>
      </View>

      {/* 2. Transaction Groups or Empty State */}
      {transactions.length === 0 ? (
        <View style={styles.emptyContainer}>
          <View style={styles.emptyIconCircle}>
            <Inbox size={28} color={BRAND_COLORS.mutedLavender} strokeWidth={1.8} />
          </View>
          <Text style={styles.emptyTitle}>No transactions yet</Text>
          <Text style={styles.emptyDescription}>
            Your recent money movements will show up here once activity is recorded.
          </Text>
        </View>
      ) : (
        <View style={styles.feedContainer}>
          {groupedTransactions.map((group, groupIndex) => (
            <View key={group.id} style={styles.groupSection}>
              {/* Group Date Header */}
              <View style={styles.groupHeader}>
                <Text style={styles.groupTitle}>{group.title}</Text>
              </View>

              {/* Group Rows */}
              <View style={styles.groupRowsContainer}>
                {group.transactions.map((tx, txIndex) => {
                  const isPositive = tx.amountMinor > 0;
                  const isLastInGroup = txIndex === group.transactions.length - 1;
                  const globalIndex = groupIndex * 2 + txIndex;

                  const accessibleAmount = isBalanceHidden
                    ? 'amount hidden'
                    : `${isPositive ? 'plus' : 'minus'} ${Math.abs(tx.amountMinor) / 100} euros`;

                  return (
                    <Animated.View
                      key={tx.id}
                      entering={FadeInUp.duration(380).delay(globalIndex * 40)}>
                      <View
                        style={styles.transactionRow}
                        accessible
                        accessibilityRole="text"
                        accessibilityLabel={`${tx.merchant}, ${tx.category}, ${tx.dateLabel}, ${accessibleAmount}`}>
                        {/* Left: 44px Rounded Icon Container with Category Background */}
                        <View
                          style={[
                            styles.iconContainer,
                            { backgroundColor: tx.iconBg || (isPositive ? '#E4F6F0' : '#F0ECF5') },
                          ]}>
                          {getTransactionIcon(tx.iconName, isPositive)}
                        </View>

                        {/* Center: Merchant Title & Category / Date Meta */}
                        <View style={styles.detailsContainer}>
                          <Text style={styles.merchantName} numberOfLines={1}>
                            {tx.merchant}
                          </Text>
                          <Text style={styles.metaText} numberOfLines={1}>
                            {tx.category} • {tx.dateLabel}
                          </Text>
                        </View>

                        {/* Right: Tabular Amount & Movement Descriptor */}
                        <View style={styles.amountContainer}>
                          <MoneyAmount
                            amountMinor={tx.amountMinor}
                            currency={tx.currency || 'EUR'}
                            locale="en-IE"
                            isHidden={isBalanceHidden}
                            size="subcard"
                            showSign={isPositive}
                            color={isPositive ? '#23755A' : BRAND_COLORS.darkCharcoal}
                            accessibilityLabelPrefix={`${tx.merchant} ${tx.category}`}
                          />
                          <Text style={styles.amountDescriptor}>
                            {isPositive ? 'Income' : 'Payment'}
                          </Text>
                        </View>
                      </View>

                      {/* Subtle Row Divider (only between rows inside the same group) */}
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
    padding: Spacing.four,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.04,
    shadowRadius: 12,
    elevation: 2,
    marginBottom: Spacing.four,
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
    fontSize: 21,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.4,
  },
  subtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  seeAllButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: 'rgba(128, 103, 161, 0.10)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(128, 103, 161, 0.22)',
  },
  seeAllText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 11.5,
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
  },
  buttonPressed: {
    transform: [{ scale: 0.95 }],
    opacity: 0.85,
  },
  feedContainer: {
    marginTop: 8,
  },
  groupSection: {
    marginTop: 6,
  },
  groupHeader: {
    paddingHorizontal: 4,
    paddingTop: 8,
    paddingBottom: 4,
  },
  groupTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 11.5,
    fontWeight: '600',
    color: BRAND_COLORS.secondaryText,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  groupRowsContainer: {
    marginTop: 2,
  },
  transactionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 4,
    minHeight: 64,
  },
  iconContainer: {
    width: 44,
    height: 44,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  detailsContainer: {
    flex: 1,
    paddingRight: Spacing.two,
  },
  merchantName: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 15.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  metaText: {
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
  amountDescriptor: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  rowDivider: {
    height: 1,
    backgroundColor: 'rgba(168, 155, 190, 0.08)',
    marginLeft: 56,
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 32,
    paddingHorizontal: 16,
  },
  emptyIconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: 'rgba(240, 236, 245, 0.70)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  emptyTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 4,
  },
  emptyDescription: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    maxWidth: 240,
  },
});
