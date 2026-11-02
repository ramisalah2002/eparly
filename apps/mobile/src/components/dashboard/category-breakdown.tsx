import React, { useState } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, { FadeIn, FadeInUp } from 'react-native-reanimated';
import {
  Car,
  Coffee,
  Receipt,
  Shapes,
  ShoppingBag,
} from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import { DemoCategoryItem } from '@/features/dashboard/demo/dashboard-demo-data';
import { computeCategoryShare } from '@/utils/formatters';

import { MoneyAmount } from './money-amount';

interface CategoryBreakdownProps {
  title?: string;
  subtitle?: string;
  totalExpensesMinor: number;
  categories: DemoCategoryItem[];
  isBalanceHidden?: boolean;
}

/**
 * Brand color palette mapping for Eparly Spending Mix categories
 */
const CATEGORY_COLORS: Record<string, string> = {
  'cat-shopping': '#8067A1', // Deep Lavender
  'cat-food': '#AB9AC0', // Muted Lilac
  'cat-bills': '#D8C3A8', // Warm Beige
  'cat-transport': '#59B99B', // Muted Mint
  'cat-other': '#E89A91', // Soft Coral
};

/**
 * Eparly Editorial Spending Breakdown Component (Direction A).
 * - Continuous canvas with clear typographic hierarchy
 * - Total expenses context is permanently preserved in the summary
 * - 11px segmented distribution bar with synchronized selection and touch feedback
 * - Simplified category rows with 3px left vertical color-key indicator
 * - Zero repeated figures and zero redundant mini-progress bars
 */
export function CategoryBreakdown({
  title = 'Where your money goes',
  subtitle = 'October spending',
  totalExpensesMinor,
  categories,
  isBalanceHidden = false,
}: CategoryBreakdownProps) {
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);

  const selectedCategory = categories.find((c) => c.id === selectedCategoryId);
  const hasSelection = selectedCategoryId !== null;

  const handleToggleCategory = (id: string) => {
    setSelectedCategoryId((prev) => (prev === id ? null : id));
  };

  const getCategoryColor = (cat: DemoCategoryItem): string => {
    return CATEGORY_COLORS[cat.id] || cat.color || BRAND_COLORS.deepLavender;
  };

  const getCategoryIcon = (id: string, color: string) => {
    const size = 16;
    const strokeWidth = 2.2;
    switch (id) {
      case 'cat-shopping':
        return <ShoppingBag size={size} color={color} strokeWidth={strokeWidth} />;
      case 'cat-food':
        return <Coffee size={size} color={color} strokeWidth={strokeWidth} />;
      case 'cat-bills':
        return <Receipt size={size} color={color} strokeWidth={strokeWidth} />;
      case 'cat-transport':
        return <Car size={size} color={color} strokeWidth={strokeWidth} />;
      default:
        return <Shapes size={size} color={color} strokeWidth={strokeWidth} />;
    }
  };

  // Pre-calculate shares for all categories
  const effectiveTotalMinor = totalExpensesMinor > 0 ? totalExpensesMinor : 1;

  return (
    <View style={styles.cardContainer}>
      {/* 1. Header Layer: Title, Subtitle & Discreet Reset / Count Action */}
      <View style={styles.headerRow}>
        <View style={styles.titleColumn}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>{subtitle}</Text>
        </View>

        {hasSelection ? (
          <Pressable
            onPress={() => setSelectedCategoryId(null)}
            style={({ pressed }) => [
              styles.resetBadge,
              pressed && styles.buttonPressed,
            ]}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            accessibilityLabel="Show all categories"
            accessibilityRole="button">
            <Text style={styles.resetBadgeText}>Show all</Text>
          </Pressable>
        ) : (
          <View
            style={styles.periodBadge}
            accessible
            accessibilityRole="text"
            accessibilityLabel={`${categories.length} categories`}>
            <Text style={styles.periodBadgeText}>{categories.length} categories</Text>
          </View>
        )}
      </View>

      {/* 2. Total Spending Summary (Total amount permanently visible) */}
      <View style={styles.summaryContainer}>
        <View style={styles.summaryHeaderRow}>
          <Text style={styles.summaryEyebrow}>Total expenses</Text>
          {selectedCategory && (
            <Animated.View
              key={`focus-tag-${selectedCategory.id}`}
              entering={FadeIn.duration(180)}
              style={styles.focusChip}>
              <View
                style={[
                  styles.focusDot,
                  { backgroundColor: getCategoryColor(selectedCategory) },
                ]}
              />
              <Text style={styles.focusText}>
                {selectedCategory.name} · {computeCategoryShare(selectedCategory.amountMinor, effectiveTotalMinor)}%
              </Text>
            </Animated.View>
          )}
        </View>

        <View style={styles.amountRow}>
          <MoneyAmount
            amountMinor={totalExpensesMinor}
            currency="EUR"
            locale="en-IE"
            isHidden={isBalanceHidden}
            size="editorial"
            hideDecimals
            accessibilityLabelPrefix="Total expenses"
          />
        </View>

        <Text style={styles.summaryMetaText}>
          {hasSelection
            ? `Tap ${selectedCategory?.name} again or 'Show all' to clear filter`
            : `Across all ${categories.length} categories`}
        </Text>
      </View>

      {/* 3. Refined 11px Segmented Distribution Bar */}
      <View style={styles.segmentTrack}>
        {categories.map((cat, index) => {
          const isSelected = selectedCategoryId === cat.id;
          const isDimmed = hasSelection && !isSelected;
          const color = getCategoryColor(cat);
          const flexValue = Math.max(cat.amountMinor / effectiveTotalMinor, 0.05);

          return (
            <Pressable
              key={cat.id}
              onPress={() => handleToggleCategory(cat.id)}
              hitSlop={{ top: 12, bottom: 12, left: 2, right: 2 }}
              accessibilityRole="button"
              accessibilityState={{ selected: isSelected }}
              accessibilityLabel={`${cat.name}, ${computeCategoryShare(cat.amountMinor, effectiveTotalMinor)}%`}
              accessibilityHint={isSelected ? 'Selected. Tap to return to total view' : 'Tap to focus on this category'}
              style={[
                styles.segmentPiece,
                {
                  flex: flexValue,
                  backgroundColor: color,
                  opacity: isDimmed ? 0.65 : 1,
                },
                index === 0 && styles.firstSegment,
                index === categories.length - 1 && styles.lastSegment,
                isSelected && styles.selectedSegment,
              ]}
            />
          );
        })}
      </View>

      {/* 4. Streamlined Category Rows (Zero Redundant Mini-Bars) */}
      <View style={styles.categoriesList}>
        {categories.map((cat, index) => {
          const isSelected = selectedCategoryId === cat.id;
          const color = getCategoryColor(cat);
          const share = computeCategoryShare(cat.amountMinor, effectiveTotalMinor);

          const accessibleAmount = isBalanceHidden
            ? 'amount hidden'
            : `${Math.round(cat.amountMinor / 100)} euros`;

          return (
            <Animated.View
              key={cat.id}
              entering={FadeInUp.duration(350).delay(index * 30)}>
              <Pressable
                onPress={() => handleToggleCategory(cat.id)}
                style={({ pressed }) => [
                  styles.categoryRow,
                  isSelected && styles.categoryRowSelected,
                  pressed && styles.rowPressed,
                ]}
                accessible
                accessibilityRole="button"
                accessibilityState={{ selected: isSelected }}
                accessibilityLabel={`${cat.name}, ${share}% of spending, ${accessibleAmount}`}
                accessibilityHint={isSelected ? 'Selected. Tap to return to total view' : 'Tap to focus on this category'}>
                {/* Left Section: 3px Vertical Accent Bar + Category Icon Circle + Name */}
                <View style={styles.categoryLeft}>
                  {isSelected ? (
                    <View
                      style={[
                        styles.activeAccentBar,
                        { backgroundColor: color },
                      ]}
                    />
                  ) : (
                    <View style={styles.inactiveAccentPlaceholder} />
                  )}

                  <View
                    style={[
                      styles.categoryIconCircle,
                      { backgroundColor: `${color}18` },
                      isSelected && {
                        borderColor: `${color}40`,
                        borderWidth: 1,
                      },
                    ]}>
                    {getCategoryIcon(cat.id, color)}
                  </View>

                  <Text
                    style={[
                      styles.categoryName,
                      isSelected && styles.categoryNameSelected,
                    ]}
                    numberOfLines={1}>
                    {cat.name}
                  </Text>
                </View>

                {/* Right Section: Tabular Amount + Percentage */}
                <View style={styles.categoryRight}>
                  <MoneyAmount
                    amountMinor={cat.amountMinor}
                    currency="EUR"
                    locale="en-IE"
                    isHidden={isBalanceHidden}
                    size="subcard"
                    hideDecimals
                    color={BRAND_COLORS.darkCharcoal}
                    accessibilityLabelPrefix={`${cat.name} amount`}
                  />
                  <Text
                    style={[
                      styles.percentageText,
                      isSelected && { color: color, fontFamily: FONT_FAMILY.bold },
                    ]}>
                    {share}%
                  </Text>
                </View>
              </Pressable>
            </Animated.View>
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
    padding: 20,
    paddingBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.04,
    shadowRadius: 10,
    elevation: 2,
    marginBottom: Spacing.three,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
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
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
    letterSpacing: -0.1,
  },
  periodBadge: {
    backgroundColor: 'rgba(240, 236, 245, 0.75)',
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.16)',
  },
  periodBadgeText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
  },
  resetBadge: {
    backgroundColor: 'rgba(128, 103, 161, 0.10)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(128, 103, 161, 0.25)',
  },
  resetBadgeText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11.5,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
  },
  buttonPressed: {
    transform: [{ scale: 0.95 }],
    opacity: 0.85,
  },
  summaryContainer: {
    marginTop: 12,
    marginBottom: 12,
  },
  summaryHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 2,
  },
  summaryEyebrow: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
  },
  focusChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: 'rgba(128, 103, 161, 0.08)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  focusDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
  },
  focusText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
  },
  amountRow: {
    marginTop: 2,
  },
  summaryMetaText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  segmentTrack: {
    flexDirection: 'row',
    width: '100%',
    height: 11,
    borderRadius: 5.5,
    overflow: 'hidden',
    backgroundColor: 'rgba(168, 155, 190, 0.14)',
    gap: 2,
    marginBottom: 14,
  },
  segmentPiece: {
    height: '100%',
  },
  firstSegment: {
    borderTopLeftRadius: 5.5,
    borderBottomLeftRadius: 5.5,
  },
  lastSegment: {
    borderTopRightRadius: 5.5,
    borderBottomRightRadius: 5.5,
  },
  selectedSegment: {
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.pureWhite,
  },
  categoriesList: {
    gap: 4,
  },
  categoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
    paddingRight: 6,
    borderRadius: 12,
  },
  categoryRowSelected: {
    backgroundColor: 'rgba(36, 37, 53, 0.02)',
  },
  rowPressed: {
    transform: [{ scale: 0.985 }],
    opacity: 0.9,
  },
  categoryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    paddingRight: 10,
  },
  activeAccentBar: {
    width: 3,
    height: 22,
    borderRadius: 1.5,
    marginRight: 8,
  },
  inactiveAccentPlaceholder: {
    width: 3,
    height: 22,
    marginRight: 8,
  },
  categoryIconCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 10,
  },
  categoryName: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 14,
    fontWeight: '500',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.1,
    flex: 1,
  },
  categoryNameSelected: {
    fontFamily: FONT_FAMILY.bold,
    fontWeight: '700',
  },
  categoryRight: {
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  percentageText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
    fontVariant: ['tabular-nums'],
  },
});
