import React from 'react';
import { StyleSheet, View } from 'react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';

export function DashboardSkeleton() {
  return (
    <View style={styles.container}>
      {/* Header skeleton */}
      <View style={styles.headerRow}>
        <View style={styles.headerTextGroup}>
          <View style={[styles.shimmer, styles.greetingBar]} />
          <View style={[styles.shimmer, styles.subGreetingBar]} />
        </View>
        <View style={styles.headerActionsGroup}>
          <View style={[styles.shimmer, styles.circleBtn]} />
          <View style={[styles.shimmer, styles.circleBtn]} />
        </View>
      </View>

      {/* Main Overview Card Skeleton */}
      <View style={styles.cardSkeleton}>
        <View style={styles.cardTopRow}>
          <View style={[styles.shimmer, styles.accountBadgeBar]} />
          <View style={[styles.shimmer, styles.circleSmall]} />
        </View>
        <View style={[styles.shimmer, styles.labelBar]} />
        <View style={[styles.shimmer, styles.amountBar]} />
        <View style={[styles.shimmer, styles.disclaimerBar]} />
        <View style={styles.divider} />
        <View style={styles.cardFooterRow}>
          <View style={[styles.shimmer, styles.footerCol]} />
          <View style={[styles.shimmer, styles.footerCol]} />
        </View>
      </View>

      {/* Income & Expense Cards Skeleton */}
      <View style={styles.splitCardsRow}>
        <View style={styles.smallCardSkeleton}>
          <View style={[styles.shimmer, styles.circleSmall]} />
          <View style={[styles.shimmer, styles.miniLabelBar]} />
          <View style={[styles.shimmer, styles.miniAmountBar]} />
        </View>
        <View style={styles.smallCardSkeleton}>
          <View style={[styles.shimmer, styles.circleSmall]} />
          <View style={[styles.shimmer, styles.miniLabelBar]} />
          <View style={[styles.shimmer, styles.miniAmountBar]} />
        </View>
      </View>

      {/* Chart Skeleton */}
      <View style={styles.cardSkeleton}>
        <View style={[styles.shimmer, styles.chartTitleBar]} />
        <View style={[styles.shimmer, styles.chartBar]} />
        <View style={[styles.shimmer, styles.chartRow]} />
        <View style={[styles.shimmer, styles.chartRow]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: Spacing.two,
  },
  shimmer: {
    backgroundColor: 'rgba(168, 155, 190, 0.16)',
    borderRadius: 8,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.three,
  },
  headerTextGroup: {
    gap: 6,
  },
  greetingBar: {
    width: 170,
    height: 22,
    borderRadius: 6,
  },
  subGreetingBar: {
    width: 110,
    height: 14,
    borderRadius: 4,
  },
  headerActionsGroup: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  circleBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
  },
  cardSkeleton: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 24,
    padding: Spacing.four,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    marginBottom: Spacing.three,
  },
  cardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: Spacing.three,
  },
  accountBadgeBar: {
    width: 120,
    height: 24,
    borderRadius: 12,
  },
  circleSmall: {
    width: 28,
    height: 28,
    borderRadius: 14,
  },
  labelBar: {
    width: 140,
    height: 14,
    marginBottom: 8,
  },
  amountBar: {
    width: 200,
    height: 36,
    borderRadius: 8,
    marginBottom: 12,
  },
  disclaimerBar: {
    width: 180,
    height: 16,
    borderRadius: 6,
  },
  divider: {
    height: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.05)',
    marginVertical: Spacing.three,
  },
  cardFooterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  footerCol: {
    width: '45%',
    height: 28,
  },
  splitCardsRow: {
    flexDirection: 'row',
    gap: Spacing.two + 4,
    marginBottom: Spacing.three,
  },
  smallCardSkeleton: {
    flex: 1,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 20,
    padding: Spacing.three,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    gap: 8,
  },
  miniLabelBar: {
    width: '60%',
    height: 12,
  },
  miniAmountBar: {
    width: '80%',
    height: 22,
  },
  chartTitleBar: {
    width: 150,
    height: 18,
    marginBottom: Spacing.three,
  },
  chartBar: {
    width: '100%',
    height: 12,
    borderRadius: 6,
    marginBottom: Spacing.three,
  },
  chartRow: {
    width: '100%',
    height: 24,
    marginBottom: 10,
  },
});
