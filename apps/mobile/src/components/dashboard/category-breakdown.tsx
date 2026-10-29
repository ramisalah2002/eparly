import React, { useState } from 'react';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  Car,
  Coffee,
  Receipt,
  Shapes,
  ShoppingBag,
} from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';
import { DemoCategoryItem } from '@/features/dashboard/demo/dashboard-demo-data';

interface CategoryBreakdownProps {
  title?: string;
  subtitle?: string;
  totalExpensesMinor: number;
  categories: DemoCategoryItem[];
  isBalanceHidden?: boolean;
}

export function CategoryBreakdown({
  title = 'Where your money goes',
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

  const getCategoryIcon = (id: string, color: string) => {
    switch (id) {
      case 'cat-shopping':
        return <ShoppingBag size={19} color={color} strokeWidth={2.2} />;
      case 'cat-food':
        return <Coffee size={19} color={color} strokeWidth={2.2} />;
      case 'cat-bills':
        return <Receipt size={19} color={color} strokeWidth={2.2} />;
      case 'cat-transport':
        return <Car size={19} color={color} strokeWidth={2.2} />;
      default:
        return <Shapes size={19} color={color} strokeWidth={2.2} />;
    }
  };

  return (
    <View style={styles.cardContainer}>
      {/* Header: Title and Single Strong Total */}
      <View style={styles.headerRow}>
        <View>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>
            {selectedCategory ? `${selectedCategory.name}: ` : 'October: '}
            <Text style={styles.totalHighlight}>
              {formatCurrency(
                selectedCategory ? selectedCategory.amountMinor : totalExpensesMinor
              )}
            </Text>
          </Text>
        </View>

        {hasSelection ? (
          <TouchableOpacity
            onPress={() => setSelectedCategoryId(null)}
            style={styles.resetBadge}
            activeOpacity={0.7}
            accessibilityLabel="Show all categories"
            accessibilityRole="button">
            <Text style={styles.resetBadgeText}>Show all</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.periodBadge}>
            <Text style={styles.periodBadgeText}>{categories.length} categories</Text>
          </View>
        )}
      </View>

      {/* Proportional Segmented Distribution Bar */}
      <View style={styles.segmentTrack}>
        {categories.map((cat, index) => {
          const isSelected = selectedCategoryId === cat.id;
          const isDimmed = hasSelection && !isSelected;

          return (
            <TouchableOpacity
              key={cat.id}
              activeOpacity={0.75}
              onPress={() => handleToggleCategory(cat.id)}
              accessibilityRole="button"
              accessibilityLabel={`${cat.name}, ${cat.percentage}%`}
              style={[
                styles.segmentPiece,
                {
                  flex: cat.percentage,
                  backgroundColor: cat.color,
                  opacity: isDimmed ? 0.25 : 1,
                },
                index === 0 && styles.firstSegment,
                index === categories.length - 1 && styles.lastSegment,
              ]}
            />
          );
        })}
      </View>

      {/* Categories List with Vector Icons and Proportional Progress Fill */}
      <View style={styles.categoriesList}>
        {categories.map((cat) => {
          const isSelected = selectedCategoryId === cat.id;
          const isDimmed = hasSelection && !isSelected;

          return (
            <TouchableOpacity
              key={cat.id}
              activeOpacity={0.7}
              onPress={() => handleToggleCategory(cat.id)}
              style={[
                styles.categoryRow,
                isDimmed && styles.unselectedCategoryRow,
              ]}>
              {/* Category Icon and Name */}
              <View style={styles.categoryLeft}>
                <View
                  style={[
                    styles.categoryIconCircle,
                    { backgroundColor: `${cat.color}18` },
                  ]}>
                  {getCategoryIcon(cat.id, cat.color)}
                </View>

                <View style={styles.categoryTextGroup}>
                  <Text style={styles.categoryName} numberOfLines={1}>
                    {cat.name}
                  </Text>
                  <View style={styles.miniTrack}>
                    <View
                      style={[
                        styles.miniFill,
                        {
                          width: `${cat.percentage}%`,
                          backgroundColor: cat.color,
                        },
                      ]}
                    />
                  </View>
                </View>
              </View>

              {/* Amount and Percentage */}
              <View style={styles.categoryRight}>
                <Text style={styles.categoryAmount}>
                  {formatCurrency(cat.amountMinor)}
                </Text>
                <View style={styles.percentagePill}>
                  <Text style={styles.percentageText}>{cat.percentage}%</Text>
                </View>
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
  totalHighlight: {
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  periodBadge: {
    backgroundColor: BRAND_COLORS.veryLightLavender,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
  },
  periodBadgeText: {
    fontSize: 11,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
  },
  resetBadge: {
    backgroundColor: BRAND_COLORS.veryLightLavender,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(128, 103, 161, 0.25)',
  },
  resetBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
  },
  segmentTrack: {
    flexDirection: 'row',
    width: '100%',
    height: 16,
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    gap: 2.5,
    marginBottom: Spacing.three + 2,
  },
  segmentPiece: {
    height: '100%',
  },
  firstSegment: {
    borderTopLeftRadius: 8,
    borderBottomLeftRadius: 8,
  },
  lastSegment: {
    borderTopRightRadius: 8,
    borderBottomRightRadius: 8,
  },
  categoriesList: {
    gap: 12,
  },
  categoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 2,
  },
  unselectedCategoryRow: {
    opacity: 0.35,
  },
  categoryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    paddingRight: Spacing.three,
  },
  categoryIconCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  categoryTextGroup: {
    flex: 1,
  },
  categoryName: {
    fontSize: 14,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 5,
  },
  miniTrack: {
    height: 8,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 4,
    overflow: 'hidden',
    width: '88%',
  },
  miniFill: {
    height: '100%',
    borderRadius: 4,
  },
  categoryRight: {
    alignItems: 'flex-end',
    gap: 2,
  },
  categoryAmount: {
    fontSize: 14,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    fontVariant: ['tabular-nums'],
  },
  percentagePill: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 5,
  },
  percentageText: {
    fontSize: 10,
    fontWeight: '600',
    color: BRAND_COLORS.secondaryText,
  },
});
