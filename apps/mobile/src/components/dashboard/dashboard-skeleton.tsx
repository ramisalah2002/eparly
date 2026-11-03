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
import { MaxContentWidth } from '@/constants/theme';

interface DashboardSkeletonProps {
  testID?: string;
}

/**
 * Eparly Dashboard Production Loading Skeleton.
 * Accurately mirrors the loaded dashboard structure:
 * 1. Fixed Monograph Header (Contextual Date, Greeting & 36px Actions)
 * 2. Editorial Financial Overview Card (Hero Balance & Cash Flow Horizon)
 * 3. Signature 2x2 Quick Actions Grid with Pastel Tint Surfaces
 * 4. Spending Activity Chart (Hero Masthead & 6 Pillar Bars)
 * 5. Where Your Money Goes (11px Segmented Bar & Streamlined Rows)
 * 6. Your Activity Journal (Squircle Icons & Tabular Rows)
 */
export function DashboardSkeleton({ testID = 'dashboard-skeleton' }: DashboardSkeletonProps) {
  const insets = useSafeAreaInsets();
  const shimmerOpacity = useSharedValue(0.28);

  useEffect(() => {
    // Smooth sinusoidal breathing pulse (900ms cycle)
    shimmerOpacity.value = withRepeat(
      withSequence(
        withTiming(0.72, { duration: 900, easing: Easing.inOut(Easing.quad) }),
        withTiming(0.28, { duration: 900, easing: Easing.inOut(Easing.quad) })
      ),
      -1,
      true
    );
  }, [shimmerOpacity]);

  const animatedShimmerStyle = useAnimatedStyle(() => ({
    opacity: shimmerOpacity.value,
  }));

  const contentPaddingTop = insets.top + 84 + 14;
  const contentPaddingBottom = Math.max(insets.bottom, 20) + 96;

  return (
    <View style={styles.screenContainer} testID={testID} accessibilityLabel="Loading dashboard...">
      {/* Full-screen warm off-white pastel background */}
      <PastelBackground style={StyleSheet.absoluteFill} pointerEvents="none" />

      {/* 1. FIXED STICKY MONOGRAPH HEADER SKELETON */}
      <View style={[styles.fixedHeaderContainer, { paddingTop: insets.top }]}>
        <View style={styles.headerSurface}>
          <View style={styles.headerContent}>
            {/* Left: Monograph Context Date & Greeting */}
            <View style={styles.headerGreetingCol}>
              <Animated.View style={[styles.shimmerBase, styles.headerDateBar, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.headerGreetingBar, animatedShimmerStyle]} />
            </View>

            {/* Right: 36px Notification Bell & User Avatar Monogram */}
            <View style={styles.headerActionsGroup}>
              <Animated.View style={[styles.shimmerBase, styles.headerActionCircle, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.headerActionCircle, animatedShimmerStyle]} />
            </View>
          </View>
        </View>
      </View>

      {/* 2. SCROLLING SKELETON CONTENT */}
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.contentContainer,
          {
            paddingTop: contentPaddingTop,
            paddingBottom: contentPaddingBottom,
          },
        ]}
        showsVerticalScrollIndicator={false}
        scrollEnabled={false}>
        <View style={styles.innerContent}>
          {/* SECTION 1: FINANCIAL OVERVIEW CARD */}
          <View style={[styles.cardContainer, styles.overviewCardContainer]}>
            {/* Top row: Eyebrow badge & Eye toggle button */}
            <View style={styles.cardHeaderRow}>
              <Animated.View style={[styles.shimmerBase, styles.overviewEyebrowBar, animatedShimmerStyle]} />
              <Animated.View style={[styles.shimmerBase, styles.overviewEyeBtn, animatedShimmerStyle]} />
            </View>

            {/* Hero balance amount */}
            <Animated.View style={[styles.shimmerBase, styles.heroBalanceBar, animatedShimmerStyle]} />

            {/* Accounts scope label */}
            <Animated.View style={[styles.shimmerBase, styles.accountsScopeBar, animatedShimmerStyle]} />

            {/* Subtle Horizon Separator */}
            <View style={styles.horizonSeparator} />

            {/* Integrated Cash Flow Horizon (Income & Expenses Columns) */}
            <View style={styles.cashFlowHorizonRow}>
              <View style={styles.cashFlowCol}>
                <Animated.View style={[styles.shimmerBase, styles.cashFlowLabelBar, animatedShimmerStyle]} />
                <Animated.View style={[styles.shimmerBase, styles.cashFlowAmountBar, animatedShimmerStyle]} />
              </View>
              <View style={styles.verticalHorizonDivider} />
              <View style={styles.cashFlowCol}>
                <Animated.View style={[styles.shimmerBase, styles.cashFlowLabelBar, animatedShimmerStyle]} />
                <Animated.View style={[styles.shimmerBase, styles.cashFlowAmountBar, animatedShimmerStyle]} />
              </View>
            </View>
          </View>

          {/* SECTION 2: 2x2 QUICK ACTIONS GRID */}
          <View style={styles.quickActionsContainer}>
            {/* Section Heading */}
            <View style={styles.quickActionsHeaderRow}>
              <Animated.View style={[styles.shimmerBase, styles.quickActionsHeadingBar, animatedShimmerStyle]} />
            </View>

            {/* 2x2 Grid of Cards */}
            <View style={styles.quickActionsGrid}>
              {[
                { bg: '#F1EEF6' },
                { bg: '#F3EFF8' },
                { bg: '#FAF3EB' },
                { bg: '#E8F6F1' },
              ].map((card, idx) => (
                <View key={idx} style={[styles.quickActionCard, { backgroundColor: card.bg }]}>
                  <View style={styles.quickActionTop}>
                    <Animated.View style={[styles.shimmerBase, styles.quickActionPill, animatedShimmerStyle]} />
                    <Animated.View style={[styles.shimmerBase, styles.quickActionTitleBar, animatedShimmerStyle]} />
                  </View>
                  <View style={styles.quickActionBottom}>
                    <Animated.View style={[styles.shimmerBase, styles.quickActionIllustration, animatedShimmerStyle]} />
                    <Animated.View style={[styles.shimmerBase, styles.quickActionArrowBtn, animatedShimmerStyle]} />
                  </View>
                </View>
              ))}
            </View>
          </View>

          {/* SECTION 3: SPENDING ACTIVITY CHART */}
          <View style={styles.cardContainer}>
            {/* Header row: Title & Period selector */}
            <View style={styles.cardHeaderRow}>
              <View style={styles.titleSubCol}>
                <Animated.View style={[styles.shimmerBase, styles.sectionTitleBar, animatedShimmerStyle]} />
                <Animated.View style={[styles.shimmerBase, styles.sectionSubBar, animatedShimmerStyle]} />
              </View>
              <Animated.View style={[styles.shimmerBase, styles.periodBadgePill, animatedShimmerStyle]} />
            </View>

            {/* Hero metric masthead: Spent amount & delta badge */}
            <View style={styles.chartMastheadRow}>
              <View style={styles.chartMastheadCol}>
                <Animated.View style={[styles.shimmerBase, styles.chartSpentCaption, animatedShimmerStyle]} />
                <Animated.View style={[styles.shimmerBase, styles.chartSpentAmount, animatedShimmerStyle]} />
              </View>
              <Animated.View style={[styles.shimmerBase, styles.chartDeltaBadge, animatedShimmerStyle]} />
            </View>

            {/* 6 Proportional Chart Bars */}
            <View style={styles.chartBarsContainer}>
              {[45, 68, 38, 92, 58, 76].map((barHeight, idx) => (
                <View key={idx} style={styles.chartBarCol}>
                  <View style={styles.chartBarTrack}>
                    <Animated.View
                      style={[
                        styles.shimmerBase,
                        styles.chartBarFill,
                        { height: barHeight },
                        animatedShimmerStyle,
                      ]}
                    />
                  </View>
                  <Animated.View style={[styles.shimmerBase, styles.chartMonthLabel, animatedShimmerStyle]} />
                </View>
              ))}
            </View>
          </View>

          {/* SECTION 4: WHERE YOUR MONEY GOES (CATEGORY BREAKDOWN) */}
          <View style={styles.cardContainer}>
            {/* Header row */}
            <View style={styles.cardHeaderRow}>
              <View style={styles.titleSubCol}>
                <Animated.View style={[styles.shimmerBase, styles.categoryTitleBar, animatedShimmerStyle]} />
                <Animated.View style={[styles.shimmerBase, styles.sectionSubBar, animatedShimmerStyle]} />
              </View>
              <Animated.View style={[styles.shimmerBase, styles.categoryTotalPill, animatedShimmerStyle]} />
            </View>

            {/* 11px Segmented Distribution Bar */}
            <View style={styles.segmentedBarTrack}>
              <View style={[styles.segmentPiece, { flex: 3.1, backgroundColor: 'rgba(128, 103, 161, 0.28)' }]} />
              <View style={[styles.segmentPiece, { flex: 2.4, backgroundColor: 'rgba(171, 154, 192, 0.28)' }]} />
              <View style={[styles.segmentPiece, { flex: 2.2, backgroundColor: 'rgba(216, 195, 168, 0.35)' }]} />
              <View style={[styles.segmentPiece, { flex: 1.4, backgroundColor: 'rgba(89, 185, 155, 0.30)' }]} />
              <View style={[styles.segmentPiece, { flex: 0.9, backgroundColor: 'rgba(232, 154, 145, 0.30)' }]} />
            </View>

            {/* Streamlined Category Rows */}
            <View style={styles.categoryRowsList}>
              {[1, 2, 3, 4, 5].map((item, idx) => (
                <View key={item}>
                  <View style={styles.categoryRowItem}>
                    <View style={styles.categoryRowLeft}>
                      <Animated.View style={[styles.shimmerBase, styles.categoryIconCircle, animatedShimmerStyle]} />
                      <Animated.View style={[styles.shimmerBase, styles.categoryRowName, animatedShimmerStyle]} />
                    </View>
                    <View style={styles.categoryRowRight}>
                      <Animated.View style={[styles.shimmerBase, styles.categoryRowAmount, animatedShimmerStyle]} />
                      <Animated.View style={[styles.shimmerBase, styles.categoryRowPercent, animatedShimmerStyle]} />
                    </View>
                  </View>
                  {idx < 4 && <View style={styles.rowDivider} />}
                </View>
              ))}
            </View>
          </View>

          {/* SECTION 5: YOUR ACTIVITY (RECENT TRANSACTIONS) */}
          <View style={styles.cardContainer}>
            {/* Header row */}
            <View style={styles.cardHeaderRow}>
              <View style={styles.titleSubCol}>
                <Animated.View style={[styles.shimmerBase, styles.transactionsTitleBar, animatedShimmerStyle]} />
                <Animated.View style={[styles.shimmerBase, styles.transactionsSubBar, animatedShimmerStyle]} />
              </View>
              <Animated.View style={[styles.shimmerBase, styles.seeAllActionPill, animatedShimmerStyle]} />
            </View>

            {/* Group date heading */}
            <Animated.View style={[styles.shimmerBase, styles.txDateHeadingBar, animatedShimmerStyle]} />

            {/* Transaction Items */}
            <View style={styles.transactionItemsList}>
              {[1, 2, 3].map((item, idx) => (
                <View key={item}>
                  <View style={styles.transactionRowItem}>
                    <View style={styles.transactionRowLeft}>
                      <Animated.View style={[styles.shimmerBase, styles.txIconSquircle, animatedShimmerStyle]} />
                      <View style={styles.txTextCol}>
                        <Animated.View style={[styles.shimmerBase, styles.txMerchantBar, animatedShimmerStyle]} />
                        <Animated.View style={[styles.shimmerBase, styles.txCategoryBar, animatedShimmerStyle]} />
                      </View>
                    </View>
                    <Animated.View style={[styles.shimmerBase, styles.txAmountBar, animatedShimmerStyle]} />
                  </View>
                  {idx < 2 && <View style={styles.txRowDivider} />}
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
    backgroundColor: 'rgba(168, 155, 190, 0.18)',
    borderRadius: 8,
  },
  fixedHeaderContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 99,
    overflow: 'hidden',
    backgroundColor: '#FAF9F6',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(36, 37, 53, 0.05)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.02,
    shadowRadius: 6,
    elevation: 1,
  },
  headerSurface: {
    width: '100%',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 10,
  },
  headerContent: {
    width: '100%',
    maxWidth: MaxContentWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerGreetingCol: {
    gap: 4,
  },
  headerDateBar: {
    width: 105,
    height: 12,
    borderRadius: 4,
  },
  headerGreetingBar: {
    width: 175,
    height: 22,
    borderRadius: 6,
  },
  headerActionsGroup: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
  },
  headerActionCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
  },
  scrollView: {
    flex: 1,
  },
  contentContainer: {
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  innerContent: {
    width: '100%',
    maxWidth: MaxContentWidth,
  },
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
  overviewCardContainer: {
    backgroundColor: '#FAF9FB',
  },
  cardHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 10,
  },
  titleSubCol: {
    gap: 4,
  },
  overviewEyebrowBar: {
    width: 95,
    height: 18,
    borderRadius: 8,
  },
  overviewEyeBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
  },
  heroBalanceBar: {
    width: 210,
    height: 36,
    borderRadius: 8,
    marginBottom: 8,
  },
  accountsScopeBar: {
    width: 120,
    height: 14,
    borderRadius: 4,
    marginBottom: 14,
  },
  horizonSeparator: {
    height: 1,
    backgroundColor: 'rgba(168, 155, 190, 0.14)',
    marginVertical: 12,
    width: '100%',
  },
  cashFlowHorizonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingTop: 2,
  },
  cashFlowCol: {
    flex: 1,
    gap: 6,
  },
  cashFlowLabelBar: {
    width: 75,
    height: 12,
    borderRadius: 4,
  },
  cashFlowAmountBar: {
    width: 95,
    height: 18,
    borderRadius: 5,
  },
  verticalHorizonDivider: {
    width: 1,
    height: 28,
    backgroundColor: 'rgba(168, 155, 190, 0.14)',
    marginHorizontal: 16,
  },
  quickActionsContainer: {
    width: '100%',
    marginTop: 2,
    marginBottom: 16,
  },
  quickActionsHeaderRow: {
    marginBottom: 10,
    paddingHorizontal: 2,
  },
  quickActionsHeadingBar: {
    width: 125,
    height: 20,
    borderRadius: 5,
  },
  quickActionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  quickActionCard: {
    flexBasis: '47%',
    flexGrow: 1,
    minHeight: 162,
    borderRadius: 24,
    paddingHorizontal: 14,
    paddingTop: 14,
    paddingBottom: 12,
    justifyContent: 'space-between',
  },
  quickActionTop: {
    alignItems: 'flex-start',
  },
  quickActionPill: {
    width: 52,
    height: 18,
    borderRadius: 9,
    marginBottom: 8,
  },
  quickActionTitleBar: {
    width: '80%',
    height: 18,
    borderRadius: 5,
  },
  quickActionBottom: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginTop: 10,
  },
  quickActionIllustration: {
    width: 40,
    height: 40,
    borderRadius: 10,
  },
  quickActionArrowBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255, 255, 255, 0.65)',
  },
  sectionTitleBar: {
    width: 140,
    height: 20,
    borderRadius: 5,
  },
  sectionSubBar: {
    width: 125,
    height: 12,
    borderRadius: 4,
  },
  periodBadgePill: {
    width: 95,
    height: 24,
    borderRadius: 12,
  },
  chartMastheadRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginTop: 12,
    marginBottom: 14,
  },
  chartMastheadCol: {
    gap: 5,
  },
  chartSpentCaption: {
    width: 95,
    height: 12,
    borderRadius: 4,
  },
  chartSpentAmount: {
    width: 110,
    height: 26,
    borderRadius: 6,
  },
  chartDeltaBadge: {
    width: 78,
    height: 28,
    borderRadius: 14,
  },
  chartBarsContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    height: 130,
    paddingTop: 8,
  },
  chartBarCol: {
    flex: 1,
    alignItems: 'center',
    height: '100%',
    justifyContent: 'flex-end',
  },
  chartBarTrack: {
    width: 26,
    height: 105,
    backgroundColor: '#F5F2F8',
    borderRadius: 13,
    justifyContent: 'flex-end',
    alignItems: 'center',
    overflow: 'hidden',
  },
  chartBarFill: {
    width: '100%',
    borderRadius: 13,
  },
  chartMonthLabel: {
    width: 22,
    height: 10,
    borderRadius: 3,
    marginTop: 6,
  },
  categoryTitleBar: {
    width: 160,
    height: 20,
    borderRadius: 5,
  },
  categoryTotalPill: {
    width: 72,
    height: 22,
    borderRadius: 8,
  },
  segmentedBarTrack: {
    flexDirection: 'row',
    width: '100%',
    height: 11,
    borderRadius: 5.5,
    overflow: 'hidden',
    backgroundColor: 'rgba(168, 155, 190, 0.14)',
    gap: 2,
    marginVertical: 14,
  },
  segmentPiece: {
    height: '100%',
  },
  categoryRowsList: {
    gap: 4,
  },
  categoryRowItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 7,
  },
  categoryRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    gap: 10,
  },
  categoryIconCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
  },
  categoryRowName: {
    width: 90,
    height: 14,
    borderRadius: 4,
  },
  categoryRowRight: {
    alignItems: 'flex-end',
    gap: 3,
  },
  categoryRowAmount: {
    width: 55,
    height: 14,
    borderRadius: 4,
  },
  categoryRowPercent: {
    width: 32,
    height: 11,
    borderRadius: 3,
  },
  rowDivider: {
    height: 1,
    backgroundColor: 'rgba(36, 37, 53, 0.04)',
    marginLeft: 42,
  },
  transactionsTitleBar: {
    width: 110,
    height: 20,
    borderRadius: 5,
  },
  transactionsSubBar: {
    width: 145,
    height: 12,
    borderRadius: 4,
  },
  seeAllActionPill: {
    width: 50,
    height: 16,
    borderRadius: 4,
  },
  txDateHeadingBar: {
    width: 60,
    height: 12,
    borderRadius: 4,
    marginTop: 10,
    marginBottom: 6,
  },
  transactionItemsList: {
    gap: 4,
  },
  transactionRowItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 7,
  },
  transactionRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    gap: 12,
  },
  txIconSquircle: {
    width: 38,
    height: 38,
    borderRadius: 12,
  },
  txTextCol: {
    gap: 4,
  },
  txMerchantBar: {
    width: 100,
    height: 14,
    borderRadius: 4,
  },
  txCategoryBar: {
    width: 65,
    height: 11,
    borderRadius: 3,
  },
  txAmountBar: {
    width: 55,
    height: 15,
    borderRadius: 4,
  },
  txRowDivider: {
    height: 1,
    backgroundColor: 'rgba(36, 37, 53, 0.05)',
    marginLeft: 50,
  },
});
