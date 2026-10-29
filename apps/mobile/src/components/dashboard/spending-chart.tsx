import React, { useState } from 'react';
import { useRouter } from 'expo-router';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { ChevronRight } from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';
import { DemoMonthlySpending } from '@/features/dashboard/demo/dashboard-demo-data';

interface SpendingChartProps {
  months: DemoMonthlySpending[];
  periodTitle?: string;
  isBalanceHidden?: boolean;
  onSeeMorePress?: () => void;
}

export function SpendingChart({
  months,
  periodTitle = 'Last 6 months',
  isBalanceHidden = false,
  onSeeMorePress,
}: SpendingChartProps) {
  const router = useRouter();

  const handleSeeMore = () => {
    if (onSeeMorePress) {
      onSeeMorePress();
    } else {
      router.push('/(tabs)/transactions');
    }
  };

  // Default selection is October 2026
  const defaultSelectedKey =
    months.find((m) => m.monthShort === 'Oct')?.monthKey ||
    months[months.length - 1]?.monthKey ||
    '';

  const [selectedKey, setSelectedKey] = useState<string>(defaultSelectedKey);

  const selectedMonth =
    months.find((m) => m.monthKey === selectedKey) ||
    months.find((m) => m.monthShort === 'Oct') ||
    months[months.length - 1];

  // Highest amount for proportional scaling
  const maxAmountMinor = Math.max(...months.map((m) => m.amountMinor), 1);
  const chartHeight = 115;

  const formatCurrency = (minor: number) => {
    if (isBalanceHidden) return '••••••';
    const major = minor / 100;
    return new Intl.NumberFormat('en-IE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(major);
  };

  return (
    <View style={styles.cardContainer}>
      {/* Header Row: Title & See More Link/Button */}
      <View style={styles.headerRow}>
        <Text style={styles.title}>Spending overview</Text>

        <TouchableOpacity
          onPress={handleSeeMore}
          activeOpacity={0.7}
          style={styles.seeMoreButton}
          accessibilityLabel="See more spending details"
          accessibilityRole="button">
          <Text style={styles.seeMoreText}>See more</Text>
          <ChevronRight size={13} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
        </TouchableOpacity>
      </View>

      {/* Visually Integrated Selected Month Info */}
      <View style={styles.selectedDetailRow}>
        <View style={styles.selectedIndicatorDot} />
        <Text style={styles.selectedMonthLabel}>
          {selectedMonth.monthFull}:{' '}
          <Text style={styles.selectedMonthValue}>
            {formatCurrency(selectedMonth.amountMinor)}
          </Text>
        </Text>
      </View>

      {/* Bars Chart */}
      <View style={[styles.chartContainer, { height: chartHeight + 28 }]}>
        {months.map((item, index) => {
          const isSelected = item.monthKey === selectedKey;
          const barHeight = Math.max((item.amountMinor / maxAmountMinor) * chartHeight, 16);

          const barColor = isSelected
            ? BRAND_COLORS.deepLavender
            : index % 2 === 0
              ? '#D2C9DF' // Soft Lavender
              : '#E8DED1'; // Warm Sand Beige

          return (
            <TouchableOpacity
              key={item.monthKey}
              activeOpacity={0.8}
              onPress={() => setSelectedKey(item.monthKey)}
              style={styles.barColumn}
              accessibilityLabel={`${item.monthFull}: ${formatCurrency(item.amountMinor)}`}
              accessibilityRole="button">
              {/* Bar track and fill aligned to baseline */}
              <View style={[styles.barWrapper, { height: chartHeight }]}>
                <View
                  style={[
                    styles.barFill,
                    {
                      height: barHeight,
                      backgroundColor: barColor,
                    },
                    isSelected && styles.selectedBarGlow,
                  ]}
                />
              </View>

              {/* Month label below baseline */}
              <View style={styles.monthLabelContainer}>
                <Text
                  style={[
                    styles.monthText,
                    isSelected && styles.selectedMonthText,
                  ]}>
                  {item.monthShort}
                </Text>
                {isSelected && <View style={styles.activeDot} />}
              </View>
            </TouchableOpacity>
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
    marginBottom: Spacing.three,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  title: {
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  seeMoreButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.veryLightLavender,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    gap: 3,
  },
  seeMoreText: {
    fontSize: 11,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
  },
  selectedDetailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 12,
    alignSelf: 'flex-start',
    marginVertical: 8,
  },
  selectedIndicatorDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: BRAND_COLORS.deepLavender,
    marginRight: 8,
  },
  selectedMonthLabel: {
    fontSize: 14,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
  selectedMonthValue: {
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  chartContainer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    paddingTop: 8,
  },
  barColumn: {
    flex: 1,
    alignItems: 'center',
    height: '100%',
    justifyContent: 'flex-end',
  },
  barWrapper: {
    width: '100%',
    justifyContent: 'flex-end',
    alignItems: 'center',
  },
  barFill: {
    width: 38,
    borderRadius: 10,
  },
  selectedBarGlow: {
    shadowColor: BRAND_COLORS.deepLavender,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 2,
  },
  monthLabelContainer: {
    alignItems: 'center',
    marginTop: 6,
    height: 20,
  },
  monthText: {
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
  selectedMonthText: {
    color: BRAND_COLORS.deepLavender,
    fontWeight: '700',
  },
  activeDot: {
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: BRAND_COLORS.deepLavender,
    marginTop: 2,
  },
});
