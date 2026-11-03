import React, { useEffect, useState } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, {
  FadeIn,
  FadeInUp,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import {
  ArrowDownRight,
  ArrowUpRight,
  Calendar,
} from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { DemoMonthlySpending } from '@/features/dashboard/demo/dashboard-demo-data';
import { triggerHaptic } from '@/utils/haptics';

import { MoneyAmount } from './money-amount';

interface SpendingChartProps {
  months: DemoMonthlySpending[];
  periodTitle?: string;
  isBalanceHidden?: boolean;
}

/**
 * Eparly Redesigned "Spending Activity" Component.
 *
 * Designed with an integrated editorial masthead, eliminating the generic "card-inside-a-card"
 * cliché. Features:
 * - Dynamic 6-month average benchmark line with micro-tag
 * - Proportional architectural pillar bars with full-height ghost rails
 * - Contextual MoM delta badge with directional semantic styling
 * - Interactive active pillar highlight with floating summit indicator
 * - Micro-insight footer offering real financial context (average vs peak)
 * - Full privacy mode integration via MoneyAmount
 */
export function SpendingChart({
  months,
  periodTitle = 'Last 6 months',
  isBalanceHidden = false,
}: SpendingChartProps) {
  // Determine default selected month (default to October 2026 or isSelected flag)
  const defaultSelectedKey =
    months.find((m) => m.isSelected)?.monthKey ||
    months.find((m) => m.monthShort === 'Oct')?.monthKey ||
    months[months.length - 1]?.monthKey ||
    '';

  const [selectedKey, setSelectedKey] = useState<string>(defaultSelectedKey);

  const selectedIndex = months.findIndex((m) => m.monthKey === selectedKey);
  const selectedMonth =
    (selectedIndex >= 0 ? months[selectedIndex] : null) ||
    months.find((m) => m.monthShort === 'Oct') ||
    months[months.length - 1] ||
    { monthKey: 'default', monthShort: 'Oct', monthFull: 'October 2026', amountMinor: 0 };

  // Calculate chart scale & 6-month benchmark metrics
  const maxAmountMinor = Math.max(...months.map((m) => m.amountMinor), 1);
  const totalAmountMinor = months.reduce((acc, m) => acc + m.amountMinor, 0);
  const avgAmountMinor = Math.round(totalAmountMinor / Math.max(months.length, 1));
  const avgEuros = Math.round(avgAmountMinor / 100);

  // Peak month determination for contextual micro-insight
  const peakMonth = months.reduce(
    (prev, current) => (current.amountMinor > prev.amountMinor ? current : prev),
    months[0]
  );
  const peakEuros = Math.round(peakMonth.amountMinor / 100);

  const barTrackHeight = 118;
  const minBarHeight = 18;

  // Position of benchmark line relative to track height
  const benchmarkBottom = Math.max(
    (avgAmountMinor / maxAmountMinor) * barTrackHeight,
    minBarHeight
  );

  // Month-over-month comparison calculation
  const prevMonth = selectedIndex > 0 ? months[selectedIndex - 1] : null;
  const diffMinor = prevMonth ? selectedMonth.amountMinor - prevMonth.amountMinor : 0;
  const diffAbsEuros = Math.round(Math.abs(diffMinor) / 100);

  // Comparison to 6-month average
  const diffAvgMinor = selectedMonth.amountMinor - avgAmountMinor;
  const diffAvgPercent = avgAmountMinor > 0 ? Math.round((diffAvgMinor / avgAmountMinor) * 100) : 0;

  const selectedMonthName = selectedMonth.monthFull.split(' ')[0] || selectedMonth.monthShort;

  return (
    <View style={styles.cardContainer}>
      {/* 1. Header: Section Title & Period Selector */}
      <View style={styles.headerRow}>
        <View style={styles.titleColumn}>
          <Text style={styles.title}>Spending activity</Text>
          <Text style={styles.subtitle}>Your spending over time</Text>
        </View>

        <View
          style={styles.periodBadge}
          accessible
          accessibilityRole="text"
          accessibilityLabel={`Period: ${periodTitle}`}>
          <Calendar size={11} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
          <Text style={styles.periodText}>{periodTitle}</Text>
        </View>
      </View>

      {/* 2. Hero Metric Masthead: Integrated into card without a nested box */}
      <View style={styles.heroMasthead}>
        <View style={styles.amountBlock}>
          <Text style={styles.monthCaption}>Spent in {selectedMonthName}</Text>
          <Animated.View key={`hero-amount-${selectedMonth.monthKey}`} entering={FadeIn.duration(160)}>
            <MoneyAmount
              amountMinor={selectedMonth.amountMinor}
              currency="EUR"
              locale="en-IE"
              isHidden={isBalanceHidden}
              size="editorial"
              hideDecimals
              accessibilityLabelPrefix={`${selectedMonth.monthFull} spending`}
            />
          </Animated.View>
        </View>

        {/* Dynamic Contextual Comparison Pill */}
        <Animated.View
          key={`insight-pill-${selectedMonth.monthKey}`}
          entering={FadeIn.duration(200)}
          style={[
            styles.deltaBadge,
            diffMinor > 0
              ? styles.deltaBadgeIncrease
              : diffMinor < 0
                ? styles.deltaBadgeDecrease
                : styles.deltaBadgeNeutral,
          ]}>
          {prevMonth ? (
            diffMinor > 0 ? (
              <>
                <View style={styles.deltaIconWrapper}>
                  <ArrowUpRight size={13} color="#C4483C" strokeWidth={2.4} />
                </View>
                <View style={styles.deltaTextColumn}>
                  <Text style={[styles.deltaValue, { color: '#B93E32' }]}>
                    {isBalanceHidden ? '••••' : `+€${diffAbsEuros}`}
                  </Text>
                  <Text style={styles.deltaSubtext}>vs {prevMonth.monthShort}</Text>
                </View>
              </>
            ) : diffMinor < 0 ? (
              <>
                <View style={styles.deltaIconWrapper}>
                  <ArrowDownRight size={13} color="#2A8568" strokeWidth={2.4} />
                </View>
                <View style={styles.deltaTextColumn}>
                  <Text style={[styles.deltaValue, { color: '#257A60' }]}>
                    {isBalanceHidden ? '••••' : `-€${diffAbsEuros}`}
                  </Text>
                  <Text style={styles.deltaSubtext}>vs {prevMonth.monthShort}</Text>
                </View>
              </>
            ) : (
              <View style={styles.deltaTextColumn}>
                <Text style={[styles.deltaValue, { color: BRAND_COLORS.darkCharcoal }]}>Equal</Text>
                <Text style={styles.deltaSubtext}>vs {prevMonth.monthShort}</Text>
              </View>
            )
          ) : (
            <View style={styles.deltaTextColumn}>
              <Text style={[styles.deltaValue, { color: BRAND_COLORS.secondaryText }]}>Base</Text>
              <Text style={styles.deltaSubtext}>6M baseline</Text>
            </View>
          )}
        </Animated.View>
      </View>

      {/* 3. Architectural Chart Stage */}
      <View style={styles.chartStage}>
        {/* Benchmark 6-Month Average Dashed Guideline */}
        <View
          style={[styles.benchmarkLineContainer, { bottom: benchmarkBottom + 32 }]}
          pointerEvents="none">
          <View style={styles.benchmarkDashedLine} />
          <View style={styles.benchmarkTag}>
            <Text style={styles.benchmarkTagText}>
              {isBalanceHidden ? 'AVG' : `AVG €${avgEuros}`}
            </Text>
          </View>
        </View>

        {/* 6 Column Pillars with Full-Height Ghost Rails */}
        <View style={styles.barsContainer}>
          {months.map((item, index) => {
            const isSelected = item.monthKey === selectedKey;
            const barHeight = Math.max(
              (item.amountMinor / maxAmountMinor) * barTrackHeight,
              minBarHeight
            );

            return (
              <ChartPillarItem
                key={item.monthKey}
                item={item}
                index={index}
                isSelected={isSelected}
                barHeight={barHeight}
                barTrackHeight={barTrackHeight}
                isBalanceHidden={isBalanceHidden}
                onSelect={() => {
                  triggerHaptic.selection();
                  setSelectedKey(item.monthKey);
                }}
              />
            );
          })}
        </View>
      </View>

      {/* 4. Editorial Contextual Micro-Insight Footer */}
      <View style={styles.insightFooter}>
        <View style={styles.insightDot} />
        <Text style={styles.insightFooterText} numberOfLines={1}>
          {isBalanceHidden
            ? 'Monthly spending trends are active'
            : diffAvgPercent > 0
              ? `${selectedMonthName} is +${diffAvgPercent}% above 6M average (€${avgEuros})`
              : diffAvgPercent < 0
                ? `${selectedMonthName} is ${Math.abs(diffAvgPercent)}% below 6M average (€${avgEuros})`
                : `${selectedMonthName} aligns exactly with your 6M average (€${avgEuros})`}
          {!isBalanceHidden && peakMonth.monthKey !== selectedMonth.monthKey && (
            <Text style={styles.peakText}> • Peak: {peakMonth.monthShort} (€{peakEuros})</Text>
          )}
        </Text>
      </View>
    </View>
  );
}

interface ChartPillarItemProps {
  item: DemoMonthlySpending;
  index: number;
  isSelected: boolean;
  barHeight: number;
  barTrackHeight: number;
  isBalanceHidden: boolean;
  onSelect: () => void;
}

function ChartPillarItem({
  item,
  index,
  isSelected,
  barHeight,
  barTrackHeight,
  isBalanceHidden,
  onSelect,
}: ChartPillarItemProps) {
  const shouldReduceMotion = useReducedMotion();
  const selectProgress = useSharedValue(isSelected ? 1 : 0);

  useEffect(() => {
    if (shouldReduceMotion) {
      selectProgress.value = isSelected ? 1 : 0;
    } else {
      selectProgress.value = withSpring(isSelected ? 1 : 0, MOTION.spring.selection);
    }
  }, [isSelected, shouldReduceMotion, selectProgress]);

  const animatedFillStyle = useAnimatedStyle(() => ({
    backgroundColor: selectProgress.value > 0.5 ? BRAND_COLORS.deepLavender : '#DFD7E8',
  }));

  const animatedMarkerStyle = useAnimatedStyle(() => ({
    opacity: selectProgress.value,
    transform: [{ scaleX: selectProgress.value }],
  }));

  const accessibleAmount = isBalanceHidden
    ? 'spending hidden'
    : `spending ${Math.round(item.amountMinor / 100)} euros`;

  return (
    <Animated.View
      entering={FadeInUp.duration(380).delay(index * 35)}
      style={styles.columnContainer}>
      <Pressable
        onPress={onSelect}
        style={({ pressed }) => [
          styles.columnPressable,
          pressed && styles.columnPressed,
        ]}
        accessible
        accessibilityRole="button"
        accessibilityState={{ selected: isSelected }}
        accessibilityLabel={`${item.monthFull}, ${accessibleAmount}`}
        accessibilityHint={isSelected ? 'Currently selected' : 'Tap to inspect month'}>
        {/* Floating active amount tooltip chip */}
        <View style={styles.tooltipSlot}>
          {isSelected ? (
            <Animated.View entering={FadeIn.duration(160)} style={styles.floatingAmountPill}>
              <Text style={styles.floatingAmountText}>
                {isBalanceHidden ? '•••' : `€${Math.round(item.amountMinor / 100)}`}
              </Text>
            </Animated.View>
          ) : null}
        </View>

        {/* Pillar Rail & Filled Bar */}
        <View style={[styles.pillarTrack, { height: barTrackHeight }]}>
          <Animated.View
            style={[
              styles.pillarFill,
              { height: barHeight },
              animatedFillStyle,
              isSelected && styles.selectedPillarGlow,
            ]}>
            {isSelected && <View style={styles.pillarTopGlint} />}
          </Animated.View>
        </View>

        {/* Baseline Anchor Rule */}
        <View
          style={[
            styles.baselineStep,
            isSelected && styles.selectedBaselineStep,
          ]}
        />

        {/* Month Label with Polished Dynamic Indicator */}
        <View style={styles.labelWrapper}>
          <Text
            style={[
              styles.monthLabel,
              isSelected && styles.selectedMonthLabel,
            ]}>
            {item.monthShort}
          </Text>
          <Animated.View style={[styles.activePillMarker, animatedMarkerStyle]} />
        </View>
      </Pressable>
    </Animated.View>
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
  },
  titleColumn: {
    flex: 1,
    paddingRight: 8,
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
  periodBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: '#F7F5FA',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
  },
  periodText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.1,
  },
  heroMasthead: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginTop: 14,
    marginBottom: 10,
    paddingBottom: 4,
  },
  amountBlock: {
    flex: 1,
  },
  monthCaption: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 12,
    fontWeight: '600',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
    marginBottom: 2,
  },
  deltaBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 14,
    borderWidth: 1,
  },
  deltaBadgeIncrease: {
    backgroundColor: '#FDF2F0',
    borderColor: 'rgba(232, 154, 145, 0.35)',
  },
  deltaBadgeDecrease: {
    backgroundColor: '#EEF7F4',
    borderColor: 'rgba(89, 185, 155, 0.35)',
  },
  deltaBadgeNeutral: {
    backgroundColor: '#F5F4F8',
    borderColor: 'rgba(168, 155, 190, 0.20)',
  },
  deltaIconWrapper: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  deltaTextColumn: {
    justifyContent: 'center',
  },
  deltaValue: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: -0.2,
    lineHeight: 14,
  },
  deltaSubtext: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 9.5,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
    lineHeight: 11,
    marginTop: 1,
  },
  chartStage: {
    width: '100%',
    paddingTop: 18,
    position: 'relative',
  },
  benchmarkLineContainer: {
    position: 'absolute',
    left: 0,
    right: 0,
    flexDirection: 'row',
    alignItems: 'center',
    zIndex: 1,
  },
  benchmarkDashedLine: {
    flex: 1,
    height: 1,
    borderWidth: 0.8,
    borderColor: 'rgba(168, 155, 190, 0.38)',
    borderStyle: 'dashed',
  },
  benchmarkTag: {
    backgroundColor: 'rgba(255, 255, 255, 0.95)',
    paddingHorizontal: 5,
    paddingVertical: 1,
    borderRadius: 4,
    marginLeft: 4,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.25)',
  },
  benchmarkTagText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 8.5,
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: 0.2,
  },
  barsContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    width: '100%',
  },
  columnContainer: {
    flex: 1,
    alignItems: 'center',
  },
  columnPressable: {
    width: '100%',
    alignItems: 'center',
    minHeight: 44,
  },
  columnPressed: {
    opacity: 0.88,
    transform: [{ scale: 0.97 }],
  },
  tooltipSlot: {
    height: 18,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  floatingAmountPill: {
    backgroundColor: BRAND_COLORS.darkCharcoal,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 3,
    elevation: 3,
  },
  floatingAmountText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 9.5,
    fontWeight: '700',
    color: BRAND_COLORS.pureWhite,
    letterSpacing: -0.2,
  },
  pillarTrack: {
    width: 26,
    backgroundColor: '#F5F2F8',
    borderRadius: 13,
    justifyContent: 'flex-end',
    alignItems: 'center',
    overflow: 'hidden',
  },
  pillarFill: {
    width: '100%',
    borderRadius: 13,
    position: 'relative',
  },
  selectedPillarGlow: {
    shadowColor: BRAND_COLORS.deepLavender,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.22,
    shadowRadius: 5,
    elevation: 3,
  },
  pillarTopGlint: {
    position: 'absolute',
    top: 2,
    left: 4,
    right: 4,
    height: 3,
    borderRadius: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.45)',
  },
  baselineStep: {
    width: 24,
    height: 2,
    backgroundColor: 'rgba(168, 155, 190, 0.14)',
    borderRadius: 1,
    marginTop: 6,
  },
  selectedBaselineStep: {
    backgroundColor: BRAND_COLORS.deepLavender,
    height: 2.5,
  },
  labelWrapper: {
    alignItems: 'center',
    marginTop: 6,
    height: 22,
    justifyContent: 'flex-start',
  },
  monthLabel: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    lineHeight: 14,
  },
  selectedMonthLabel: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 12,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  activePillMarker: {
    width: 12,
    height: 2.5,
    borderRadius: 2,
    backgroundColor: BRAND_COLORS.deepLavender,
    marginTop: 3,
  },
  inactiveMarkerSpacer: {
    width: 12,
    height: 2.5,
    marginTop: 3,
  },
  insightFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 14,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(168, 155, 190, 0.12)',
  },
  insightDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: BRAND_COLORS.deepLavender,
    marginRight: 6,
  },
  insightFooterText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    flex: 1,
    letterSpacing: -0.1,
  },
  peakText: {
    fontFamily: FONT_FAMILY.regular,
    color: '#8B8C9E',
  },
});

