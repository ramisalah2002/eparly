import React, { useEffect } from 'react';
import {
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { PastelBackground } from './pastel-background';
import { BRAND_COLORS } from '@/constants/colors';
import { MaxContentWidth, Spacing } from '@/constants/theme';

interface DashboardSkeletonProps {
  testID?: string;
}

export function DashboardSkeleton({ testID = 'dashboard-skeleton' }: DashboardSkeletonProps) {
  const insets = useSafeAreaInsets();
  const shimmerOpacity = useSharedValue(0.35);

  useEffect(() => {
    shimmerOpacity.value = withRepeat(
      withSequence(
        withTiming(0.85, { duration: 800, easing: Easing.inOut(Easing.ease) }),
        withTiming(0.35, { duration: 800, easing: Easing.inOut(Easing.ease) })
      ),
      -1,
      true
    );
  }, [shimmerOpacity]);

  const animatedShimmerStyle = useAnimatedStyle(() => ({
    opacity: shimmerOpacity.value,
  }));

  const contentPaddingBottom = Math.max(insets.bottom, 20) + 96;

  return (
    <View style={styles.screenContainer} testID={testID} accessibilityLabel="Loading dashboard...">
      {/* Full-screen pastel background */}
      <PastelBackground style={StyleSheet.absoluteFill} pointerEvents="none" />

      {/* FIXED STICKY HEADER SKELETON */}
      <View style={[styles.fixedHeaderContainer, { paddingTop: insets.top }]}>
        <View style={styles.headerSurface}>
          <View style={styles.headerContent}>
            {/* Left: Greeting Bar */}
            <View style={styles.headerGreetingCol}>
              <Animated.View style={[styles.shimmerBase, styles.greetingBar, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.subGreetingBar, animatedShimmerStyle]} />
            </View>

            {/* Right: Notification Bell & User Avatar Circles */}
            <View style={styles.headerActionsGroup}>
              <Animated.View style={[styles.shimmerBase, styles.headerCircleBtn, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.headerCircleBtn, animatedShimmerStyle]} />
            </View>
          </View>
        </View>
        <View style={styles.headerBottomBorder} />
      </View>

      {/* SCROLLING SKELETON CONTENT */}
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.contentContainer,
          {
            paddingTop: Spacing.three,
            paddingBottom: contentPaddingBottom,
          },
        ]}
        showsVerticalScrollIndicator={false}
        scrollEnabled={false}>
        <View style={styles.innerContent}>
          {/* 1. FINANCIAL OVERVIEW CARD SKELETON */}
          <View style={styles.cardContainer}>
            {/* Top row: Label & Eye icon */}
            <View style={styles.cardTopRow}>
              <Animated.View style={[styles.shimmerBase, styles.balanceLabelBar, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.circleEyeBtn, animatedShimmerStyle]} />
            </View>

            {/* Main balance amount */}
            <Animated.View style={[styles.shimmerBase, styles.mainBalanceAmountBar, animatedShimmerStyle]} />

            {/* Accounts badge */}
            <Animated.View style={[styles.shimmerBase, styles.accountsBadgeBar, animatedShimmerStyle]} />

            {/* Embedded Income & Expenses Mini Cards (Mint & Lavender matching reference) */}
            <View style={styles.miniCardsRow}>
              {/* Income Mini Card */}
              <View style={[styles.miniCard, styles.incomeMiniCard]}>
                <View style={styles.miniCardTopRow}>
                  <Animated.View style={[styles.shimmerBase, styles.miniCardAmountBar, animatedShimmerStyle]} />
                  <Animated.View style={[styles.shimmerBase, styles.miniCardPlusCircle, animatedShimmerStyle]} />
                </View>
                <Animated.View style={[styles.shimmerBase, styles.miniCardLabelBar, animatedShimmerStyle]} />
              </View>

              {/* Expense Mini Card */}
              <View style={[styles.miniCard, styles.expenseMiniCard]}>
                <View style={styles.miniCardTopRow}>
                  <Animated.View style={[styles.shimmerBase, styles.miniCardAmountBar, animatedShimmerStyle]} />
                  <Animated.View style={[styles.shimmerBase, styles.miniCardPlusCircle, animatedShimmerStyle]} />
                </View>
                <Animated.View style={[styles.shimmerBase, styles.miniCardLabelBar, animatedShimmerStyle]} />
              </View>
            </View>
          </View>

          {/* 2. SPENDING OVERVIEW SKELETON */}
          <View style={styles.cardContainer}>
            {/* Header row: Title & "See more" button */}
            <View style={styles.cardHeaderRow}>
              <Animated.View style={[styles.shimmerBase, styles.chartTitleBar, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.badgePill, animatedShimmerStyle]} />
            </View>

            {/* Selected month tag */}
            <Animated.View style={[styles.shimmerBase, styles.selectedMonthTag, animatedShimmerStyle]} />

            {/* 6 Chart Bars */}
            <View style={styles.chartBarsContainer}>
              {[50, 78, 42, 92, 65, 84].map((barHeight, idx) => (
                <View key={idx} style={styles.barCol}>
                  <View style={styles.barWrapper}>
                    <Animated.View
                      style={[
                        styles.shimmerBase,
                        styles.chartBar,
                        { height: barHeight },
                        animatedShimmerStyle,
                      ]}
                    />
                  </View>
                  <Animated.View style={[styles.shimmerBase, styles.barMonthLabel, animatedShimmerStyle]} />
                </View>
              ))}
            </View>
          </View>

          {/* 3. CATEGORY BREAKDOWN SKELETON */}
          <View style={styles.cardContainer}>
            {/* Header row */}
            <View style={styles.cardHeaderRow}>
              <View style={styles.titleSubGroup}>
                <Animated.View style={[styles.shimmerBase, styles.categoryTitleBar, animatedShimmerStyle]} />
                <Animated.View style={[styles.shimmerBase, styles.categorySubtitleBar, animatedShimmerStyle]} />
              </View>
              <Animated.View style={[styles.shimmerBase, styles.categoryBadge, animatedShimmerStyle]} />
            </View>

            {/* Segmented bar */}
            <View style={styles.segmentedBarTrack}>
              <Animated.View style={[styles.shimmerBase, styles.segmentPiece, { flex: 3 }, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.segmentPiece, { flex: 2.5 }, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.segmentPiece, { flex: 2.2 }, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.segmentPiece, { flex: 1.5 }, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.segmentPiece, { flex: 1 }, animatedShimmerStyle]} />
            </View>

            {/* Category rows */}
            <View style={styles.categoryRowsList}>
              {[1, 2, 3, 4].map((item) => (
                <View key={item} style={styles.categoryRowItem}>
                  <View style={styles.categoryRowLeft}>
                    <Animated.View style={[styles.shimmerBase, styles.categoryCircleIcon, animatedShimmerStyle]} />
                    <View style={styles.categoryRowTextCol}>
                      <Animated.View style={[styles.shimmerBase, styles.categoryRowName, animatedShimmerStyle]} />
                      <Animated.View style={[styles.shimmerBase, styles.categoryRowTrack, animatedShimmerStyle]} />
                    </View>
                  </View>
                  <View style={styles.categoryRowRight}>
                    <Animated.View style={[styles.shimmerBase, styles.categoryRowAmount, animatedShimmerStyle]} />
                    <Animated.View style={[styles.shimmerBase, styles.categoryRowPercent, animatedShimmerStyle]} />
                  </View>
                </View>
              ))}
            </View>
          </View>

          {/* 4. RECENT TRANSACTIONS SKELETON */}
          <View style={styles.cardContainer}>
            {/* Header row */}
            <View style={styles.cardHeaderRow}>
              <Animated.View style={[styles.shimmerBase, styles.transactionsTitleBar, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.seeAllPill, animatedShimmerStyle]} />
            </View>

            {/* Transaction items */}
            <View style={styles.transactionItemsList}>
              {[1, 2, 3].map((item) => (
                <View key={item} style={styles.transactionRowItem}>
                  <View style={styles.transactionRowLeft}>
                    <Animated.View style={[styles.shimmerBase, styles.txIconBox, animatedShimmerStyle]} />
                    <View style={styles.txTextCol}>
                      <Animated.View style={[styles.shimmerBase, styles.txMerchantBar, animatedShimmerStyle]} />
                      <Animated.View style={[styles.shimmerBase, styles.txCategoryBar, animatedShimmerStyle]} />
                    </View>
                  </View>
                  <Animated.View style={[styles.shimmerBase, styles.txAmountBar, animatedShimmerStyle]} />
                </View>
              ))}
            </View>
          </View>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screenContainer: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  shimmerBase: {
    backgroundColor: 'rgba(168, 155, 190, 0.22)',
    borderRadius: 8,
  },
  fixedHeaderContainer: {
    width: '100%',
    zIndex: 100,
    elevation: 4,
    backgroundColor: 'rgba(230, 247, 241, 0.95)',
  },
  headerSurface: {
    width: '100%',
    alignItems: 'center',
    paddingHorizontal: Spacing.three,
    paddingVertical: 10,
  },
  headerContent: {
    width: '100%',
    maxWidth: MaxContentWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerGreetingCol: {
    gap: 6,
  },
  greetingBar: {
    width: 150,
    height: 20,
    borderRadius: 6,
  },
  subGreetingBar: {
    width: 100,
    height: 12,
    borderRadius: 4,
  },
  headerActionsGroup: {
    flexDirection: 'row',
    gap: 10,
    alignItems: 'center',
  },
  headerCircleBtn: {
    width: 38,
    height: 38,
    borderRadius: 19,
  },
  headerBottomBorder: {
    height: 1,
    backgroundColor: 'rgba(168, 155, 190, 0.18)',
    width: '100%',
  },
  scrollView: {
    flex: 1,
  },
  contentContainer: {
    alignItems: 'center',
    paddingHorizontal: Spacing.three,
  },
  innerContent: {
    width: '100%',
    maxWidth: MaxContentWidth,
  },
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
  },
  cardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  balanceLabelBar: {
    width: 95,
    height: 14,
    borderRadius: 4,
  },
  circleEyeBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
  },
  mainBalanceAmountBar: {
    width: 210,
    height: 38,
    borderRadius: 8,
    marginBottom: 10,
  },
  accountsBadgeBar: {
    width: 125,
    height: 20,
    borderRadius: 10,
    marginBottom: 16,
  },
  miniCardsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  miniCard: {
    flex: 1,
    borderRadius: 20,
    padding: 14,
    minHeight: 88,
    justifyContent: 'space-between',
  },
  incomeMiniCard: {
    backgroundColor: 'rgba(226, 247, 236, 0.75)',
  },
  expenseMiniCard: {
    backgroundColor: 'rgba(228, 220, 248, 0.75)',
  },
  miniCardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  miniCardAmountBar: {
    width: '68%',
    height: 18,
    borderRadius: 5,
  },
  miniCardPlusCircle: {
    width: 18,
    height: 18,
    borderRadius: 9,
  },
  miniCardLabelBar: {
    width: '45%',
    height: 12,
    borderRadius: 4,
    marginTop: 10,
  },
  cardHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  chartTitleBar: {
    width: 140,
    height: 18,
    borderRadius: 6,
  },
  badgePill: {
    width: 78,
    height: 26,
    borderRadius: 12,
  },
  selectedMonthTag: {
    width: 135,
    height: 28,
    borderRadius: 12,
    marginVertical: 6,
  },
  chartBarsContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    height: 140,
    paddingTop: 12,
  },
  barCol: {
    flex: 1,
    alignItems: 'center',
    height: '100%',
    justifyContent: 'flex-end',
  },
  barWrapper: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  chartBar: {
    width: 38,
    borderRadius: 6,
  },
  barMonthLabel: {
    width: 24,
    height: 10,
    borderRadius: 3,
    marginTop: 8,
  },
  titleSubGroup: {
    gap: 4,
  },
  categoryTitleBar: {
    width: 160,
    height: 18,
    borderRadius: 6,
  },
  categorySubtitleBar: {
    width: 100,
    height: 12,
    borderRadius: 4,
  },
  categoryBadge: {
    width: 76,
    height: 22,
    borderRadius: 10,
  },
  segmentedBarTrack: {
    flexDirection: 'row',
    width: '100%',
    height: 16,
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    gap: 2.5,
    marginVertical: 14,
  },
  segmentPiece: {
    height: '100%',
    borderRadius: 2,
  },
  categoryRowsList: {
    gap: 12,
  },
  categoryRowItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  categoryRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  categoryCircleIcon: {
    width: 38,
    height: 38,
    borderRadius: 19,
    marginRight: 12,
  },
  categoryRowTextCol: {
    flex: 1,
    gap: 6,
  },
  categoryRowName: {
    width: 90,
    height: 14,
    borderRadius: 4,
  },
  categoryRowTrack: {
    width: '85%',
    height: 8,
    borderRadius: 4,
  },
  categoryRowRight: {
    alignItems: 'flex-end',
    gap: 4,
  },
  categoryRowAmount: {
    width: 55,
    height: 14,
    borderRadius: 4,
  },
  categoryRowPercent: {
    width: 32,
    height: 16,
    borderRadius: 8,
  },
  transactionsTitleBar: {
    width: 145,
    height: 18,
    borderRadius: 6,
  },
  seeAllPill: {
    width: 62,
    height: 24,
    borderRadius: 12,
  },
  transactionItemsList: {
    gap: 12,
  },
  transactionRowItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 4,
  },
  transactionRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  txIconBox: {
    width: 40,
    height: 40,
    borderRadius: 14,
    marginRight: 12,
  },
  txTextCol: {
    gap: 5,
  },
  txMerchantBar: {
    width: 110,
    height: 14,
    borderRadius: 4,
  },
  txCategoryBar: {
    width: 75,
    height: 11,
    borderRadius: 3,
  },
  txAmountBar: {
    width: 58,
    height: 15,
    borderRadius: 4,
  },
});
