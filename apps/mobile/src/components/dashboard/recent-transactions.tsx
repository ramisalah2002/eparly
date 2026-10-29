import React from 'react';
import { useRouter } from 'expo-router';
import {
  Pressable,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  Briefcase,
  Music2,
  Play,
  ShoppingCart,
} from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';
import { DemoTransaction } from '@/features/dashboard/demo/dashboard-demo-data';

interface RecentTransactionsProps {
  transactions: DemoTransaction[];
  title?: string;
  isBalanceHidden?: boolean;
  onSeeAllPress?: () => void;
}

export function RecentTransactions({
  transactions,
  title = 'Recent transactions',
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

  const formatCurrency = (minor: number) => {
    if (isBalanceHidden) return '••••••';
    const isNegative = minor < 0;
    const absMajor = Math.abs(minor) / 100;
    const formatted = new Intl.NumberFormat('en-IE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 2,
    }).format(absMajor);

    return isNegative ? `-${formatted}` : `+${formatted}`;
  };

  const getTransactionIcon = (iconName: string, isPositive: boolean) => {
    const color = isPositive ? BRAND_COLORS.mutedMintGreen : BRAND_COLORS.deepLavender;
    switch (iconName) {
      case 'shopping':
        return <ShoppingCart size={17} color={color} strokeWidth={2.2} />;
      case 'music':
        return <Music2 size={17} color={color} strokeWidth={2.2} />;
      case 'briefcase':
        return <Briefcase size={17} color={color} strokeWidth={2.2} />;
      case 'play':
        return <Play size={17} color={color} strokeWidth={2.2} />;
      default:
        return <ShoppingCart size={17} color={color} strokeWidth={2.2} />;
    }
  };

  return (
    <View style={styles.cardContainer}>
      {/* Header */}
      <View style={styles.headerRow}>
        <View>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>Latest activity</Text>
        </View>

        <TouchableOpacity
          onPress={handleSeeAll}
          activeOpacity={0.7}
          style={styles.seeAllButton}
          accessibilityLabel="See all transactions"
          accessibilityRole="button">
          <Text style={styles.seeAllText}>See all</Text>
        </TouchableOpacity>
      </View>

      {/* Transactions List with Vector Icons */}
      <View style={styles.listContainer}>
        {transactions.map((tx, index) => {
          const isPositive = tx.amountMinor > 0;
          const isLast = index === transactions.length - 1;

          return (
            <Pressable
              key={tx.id}
              style={({ pressed }) => [
                styles.transactionRow,
                isLast && styles.noDivider,
                pressed && styles.rowPressed,
              ]}
              accessibilityLabel={`${tx.merchant}, ${tx.category}, ${tx.dateLabel}, ${formatCurrency(tx.amountMinor)}`}>
              {/* Rounded Vector Icon Container */}
              <View
                style={[
                  styles.iconContainer,
                  { backgroundColor: tx.iconBg },
                ]}>
                {getTransactionIcon(tx.iconName, isPositive)}
              </View>

              {/* Merchant & Category / Date */}
              <View style={styles.detailsContainer}>
                <Text style={styles.merchantName} numberOfLines={1}>
                  {tx.merchant}
                </Text>
                <View style={styles.metaRow}>
                  <Text style={styles.categoryBadgeText}>{tx.category}</Text>
                  <Text style={styles.metaDot}>•</Text>
                  <Text style={styles.dateText}>{tx.dateLabel}</Text>
                </View>
              </View>

              {/* Amount */}
              <View style={styles.amountContainer}>
                <Text
                  style={[
                    styles.amountText,
                    isPositive ? styles.positiveAmount : styles.negativeAmount,
                  ]}>
                  {formatCurrency(tx.amountMinor)}
                </Text>
              </View>
            </Pressable>
          );
        })}
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
    marginBottom: Spacing.four,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.two + 4,
  },
  title: {
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  subtitle: {
    fontSize: 12,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  seeAllButton: {
    paddingVertical: 5,
    paddingHorizontal: 10,
    borderRadius: 12,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
  },
  seeAllText: {
    fontSize: 11,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
  },
  listContainer: {
    gap: 2,
  },
  transactionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 9,
    paddingHorizontal: 6,
    borderRadius: 14,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 0, 0, 0.03)',
  },
  noDivider: {
    borderBottomWidth: 0,
  },
  rowPressed: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  iconContainer: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  detailsContainer: {
    flex: 1,
    paddingRight: Spacing.two,
  },
  merchantName: {
    fontSize: 14,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 2,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  categoryBadgeText: {
    fontSize: 11,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
  },
  metaDot: {
    fontSize: 10,
    color: BRAND_COLORS.secondaryText,
  },
  dateText: {
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
  },
  amountContainer: {
    alignItems: 'flex-end',
  },
  amountText: {
    fontSize: 14,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  positiveAmount: {
    color: '#2E7D32',
  },
  negativeAmount: {
    color: BRAND_COLORS.darkCharcoal,
  },
});
