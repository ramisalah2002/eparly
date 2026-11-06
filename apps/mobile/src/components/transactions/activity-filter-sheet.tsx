import React from 'react';
import {
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { Calendar, Check, Layers, RotateCcw, X } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { getCategoryTheme } from '@/constants/category-theme';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { getActivityCopy } from '@/constants/activity-copy';
import { triggerHaptic } from '@/utils/haptics';

export type DurationFilter = 'ALL' | '30_DAYS' | '3_MONTHS' | 'THIS_YEAR';

export interface CategoryOption {
  name: string;
  count: number;
}

interface ActivityFilterSheetProps {
  visible: boolean;
  onClose: () => void;
  selectedDuration: DurationFilter;
  onSelectDuration: (duration: DurationFilter) => void;
  categories: CategoryOption[];
  selectedCategory: string | null;
  onSelectCategory: (categoryName: string | null) => void;
  filteredCount: number;
  totalCount: number;
  locale?: string;
}

/**
 * Editorial Filter Bottom Sheet.
 * Features unified grabber physics, segmented period pill row,
 * category selection list with counts, and safe-area aware apply action.
 */
export function ActivityFilterSheet({
  visible,
  onClose,
  selectedDuration,
  onSelectDuration,
  categories,
  selectedCategory,
  onSelectCategory,
  filteredCount,
  totalCount,
  locale = 'en-IE',
}: ActivityFilterSheetProps) {
  const insets = useSafeAreaInsets();
  const copy = getActivityCopy(locale);

  const isReducedMotion = useReducedMotion();
  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleCard = isReducedMotion ? 1.0 : MOTION.scale.cardPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  const isFilterActive = selectedCategory !== null || selectedDuration !== 'ALL';

  const durationOptions: { id: DurationFilter; label: string; shortLabel: string }[] = [
    { id: 'ALL', label: copy.durationAll, shortLabel: copy.shortAll },
    { id: '30_DAYS', label: copy.duration30Days, shortLabel: copy.short30Days },
    { id: '3_MONTHS', label: copy.duration3Months, shortLabel: copy.short3Months },
    { id: 'THIS_YEAR', label: copy.durationThisYear, shortLabel: copy.shortThisYear },
  ];

  const handleReset = () => {
    void triggerHaptic.light();
    onSelectCategory(null);
    onSelectDuration('ALL');
  };

  const handleCategoryPress = (catName: string | null) => {
    void triggerHaptic.selection();
    onSelectCategory(catName);
  };

  const handleDurationPress = (dur: DurationFilter) => {
    void triggerHaptic.selection();
    onSelectDuration(dur);
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <AnimatedPressable
          style={styles.backdrop}
          onPress={onClose}
          scaleTarget={1.0}
          accessibilityLabel={copy.close}
          accessibilityRole="button"
        />

        <View style={[styles.sheetContainer, { paddingBottom: Math.max(insets.bottom, 16) + 12 }]}>
          {/* Sheet Grabber Handle */}
          <View style={styles.handle} />

          {/* Sheet Header */}
          <View style={styles.headerRow}>
            <View style={styles.titleColumn}>
              <Text style={styles.headerTitle}>{copy.filterButtonLabel}</Text>
              <Text style={styles.headerSubtitle}>
                {filteredCount} {copy.operationsOf} {totalCount} {copy.operationsRecorded}
              </Text>
            </View>

            <View style={styles.actionsGroup}>
              {isFilterActive && (
                <AnimatedPressable
                  onPress={handleReset}
                  scaleTarget={scalePress}
                  haptic="light"
                  style={styles.resetBtn}
                  accessibilityRole="button"
                  accessibilityLabel={copy.resetFilters}>
                  <RotateCcw size={12} color={BRAND_COLORS.deepLavender} strokeWidth={2.4} />
                  <Text style={styles.resetBtnText}>{copy.resetFilters}</Text>
                </AnimatedPressable>
              )}

              <AnimatedPressable
                onPress={onClose}
                scaleTarget={scalePress}
                style={styles.closeBtn}
                accessibilityRole="button"
                accessibilityLabel={copy.close}>
                <X size={16} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
              </AnimatedPressable>
            </View>
          </View>

          {/* Section 1: Period Selection */}
          <View style={styles.sectionHeader}>
            <Calendar size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
            <Text style={styles.sectionLabel}>{copy.period}</Text>
          </View>

          <View style={styles.durationPillsRow}>
            {durationOptions.map((opt) => {
              const isActive = selectedDuration === opt.id;
              return (
                <AnimatedPressable
                  key={opt.id}
                  onPress={() => handleDurationPress(opt.id)}
                  scaleTarget={scalePress}
                  style={[
                    styles.durationPill,
                    isActive && styles.durationPillActive,
                  ]}
                  accessibilityRole="button"
                  accessibilityLabel={opt.label}>
                  <Text
                    style={[
                      styles.durationPillText,
                      isActive && styles.durationPillTextActive,
                    ]}>
                    {opt.shortLabel}
                  </Text>
                </AnimatedPressable>
              );
            })}
          </View>

          {/* Section 2: Category Selection */}
          <View style={[styles.sectionHeader, { marginTop: 16 }]}>
            <Layers size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
            <Text style={styles.sectionLabel}>{copy.categories}</Text>
          </View>

          <ScrollView style={styles.categoriesScroll} showsVerticalScrollIndicator={false}>
            {/* Option: All categories */}
            <AnimatedPressable
              onPress={() => handleCategoryPress(null)}
              scaleTarget={scaleCard}
              style={[
                styles.categoryItem,
                selectedCategory === null && styles.categoryItemActive,
              ]}
              accessibilityRole="button"
              accessibilityLabel={`${copy.showAllCategories}, ${totalCount} ${copy.operationsRecorded}`}>
              <View style={styles.categoryLeft}>
                <View style={[styles.categoryIconCircle, { backgroundColor: BRAND_COLORS.veryLightLavender }]}>
                  <Layers size={16} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                </View>
                <View>
                  <Text style={[styles.categoryName, selectedCategory === null && styles.categoryNameActive]}>
                    {copy.showAllCategories}
                  </Text>
                  <Text style={styles.categoryCount}>
                    {totalCount} {copy.operationsRecorded}
                  </Text>
                </View>
              </View>

              {selectedCategory === null && (
                <Check size={18} color={BRAND_COLORS.deepLavender} strokeWidth={2.6} />
              )}
            </AnimatedPressable>

            {/* Individual Categories */}
            {categories.map((cat) => {
              const isSelected = selectedCategory === cat.name;
              const theme = getCategoryTheme(cat.name === 'Non catégorisé' ? null : cat.name);
              const IconComp = theme.icon;

              return (
                <AnimatedPressable
                  key={cat.name}
                  onPress={() => handleCategoryPress(isSelected ? null : cat.name)}
                  scaleTarget={scaleCard}
                  style={[
                    styles.categoryItem,
                    isSelected && styles.categoryItemActive,
                  ]}
                  accessibilityRole="button"
                  accessibilityLabel={`${cat.name}, ${cat.count} ${copy.operationsRecorded}`}>
                  <View style={styles.categoryLeft}>
                    <View style={[styles.categoryIconCircle, { backgroundColor: theme.backgroundColor }]}>
                      <IconComp size={16} color={theme.color} strokeWidth={2.2} />
                    </View>
                    <View>
                      <Text style={[styles.categoryName, isSelected && styles.categoryNameActive]}>
                        {cat.name}
                      </Text>
                      <Text style={styles.categoryCount}>
                        {cat.count} {copy.operationsRecorded}
                      </Text>
                    </View>
                  </View>

                  {isSelected && (
                    <Check size={18} color={BRAND_COLORS.deepLavender} strokeWidth={2.6} />
                  )}
                </AnimatedPressable>
              );
            })}
          </ScrollView>

          {/* Bottom Sticky Apply Button */}
          <AnimatedPressable
            onPress={onClose}
            scaleTarget={scaleAction}
            haptic="light"
            style={styles.applyBtn}
            accessibilityRole="button"
            accessibilityLabel={`${copy.applyFilters} (${filteredCount})`}>
            <Text style={styles.applyBtnText}>
              {copy.applyFilters} ({filteredCount})
            </Text>
          </AnimatedPressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(26, 26, 26, 0.45)',
  },
  backdrop: {
    ...StyleSheet.absoluteFill,
  },
  sheetContainer: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 20,
    paddingTop: 10,
    maxHeight: '85%',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 10,
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(0, 0, 0, 0.12)',
    alignSelf: 'center',
    marginBottom: 12,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(36, 37, 53, 0.06)',
    marginBottom: 14,
  },
  titleColumn: {
    flex: 1,
    paddingRight: 10,
  },
  headerTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  headerSubtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  actionsGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  resetBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 5.5,
    borderRadius: 20,
    backgroundColor: 'rgba(128, 103, 161, 0.10)',
  },
  resetBtnText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11.5,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
  },
  closeBtn: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 8,
  },
  sectionLabel: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 11,
    fontWeight: '700',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: 0.5,
  },
  durationPillsRow: {
    flexDirection: 'row',
    gap: 6,
  },
  durationPill: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
  },
  durationPillActive: {
    backgroundColor: BRAND_COLORS.deepLavender,
    borderColor: BRAND_COLORS.deepLavender,
  },
  durationPillText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    fontWeight: '500',
    color: BRAND_COLORS.darkCharcoal,
  },
  durationPillTextActive: {
    color: BRAND_COLORS.pureWhite,
    fontFamily: FONT_FAMILY.semiBold,
    fontWeight: '600',
  },
  categoriesScroll: {
    maxHeight: 250,
  },
  categoryItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 10,
    paddingHorizontal: 10,
    borderRadius: 12,
    marginBottom: 4,
  },
  categoryItemActive: {
    backgroundColor: 'rgba(128, 103, 161, 0.08)',
  },
  categoryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    flex: 1,
  },
  categoryIconCircle: {
    width: 34,
    height: 34,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  categoryName: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 14,
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.1,
  },
  categoryNameActive: {
    fontFamily: FONT_FAMILY.semiBold,
    color: BRAND_COLORS.deepLavender,
    fontWeight: '600',
  },
  categoryCount: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
    fontVariant: ['tabular-nums'],
  },
  applyBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 13,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 14,
    shadowColor: BRAND_COLORS.deepLavender,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 5,
    elevation: 2,
  },
  applyBtnText: {
    fontFamily: FONT_FAMILY.semiBold,
    color: BRAND_COLORS.pureWhite,
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: 0.1,
  },
});

