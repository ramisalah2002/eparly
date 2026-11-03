import React, { useState } from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { Search, SlidersHorizontal, X } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { getActivityCopy } from '@/constants/activity-copy';
import { triggerHaptic } from '@/utils/haptics';

export interface QuickCategoryChip {
  name: string;
  count: number;
}

interface ActivitySearchBarProps {
  searchQuery: string;
  onSearchChange: (text: string) => void;
  isFilterActive: boolean;
  onOpenFilterSheet: () => void;
  categories: QuickCategoryChip[];
  selectedCategory: string | null;
  onSelectCategory: (categoryName: string | null) => void;
  locale?: string;
}

/**
 * Editorial Search and Filter Control Cluster.
 * Combines full-text search with 1-tap horizontal category chips and
 * direct gateway to the advanced filter bottom sheet.
 */
export function ActivitySearchBar({
  searchQuery,
  onSearchChange,
  isFilterActive,
  onOpenFilterSheet,
  categories,
  selectedCategory,
  onSelectCategory,
  locale = 'en-IE',
}: ActivitySearchBarProps) {
  const copy = getActivityCopy(locale);
  const isReducedMotion = useReducedMotion();
  const [isFocused, setIsFocused] = useState<boolean>(false);

  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;

  const handleChipPress = (categoryName: string | null) => {
    void triggerHaptic.selection();
    onSelectCategory(categoryName);
  };

  return (
    <View style={styles.container}>
      {/* Search Input & Advanced Filter Gateway */}
      <View style={styles.inputRow}>
        <View style={[styles.searchField, isFocused && styles.searchFieldFocused]}>
          <Search
            size={18}
            color={isFocused ? BRAND_COLORS.deepLavender : BRAND_COLORS.secondaryText}
            strokeWidth={2.2}
            style={styles.searchIcon}
          />
          <TextInput
            value={searchQuery}
            onChangeText={onSearchChange}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            placeholder={copy.searchPlaceholder}
            placeholderTextColor={BRAND_COLORS.secondaryText}
            style={styles.textInput}
            clearButtonMode="never"
            autoCapitalize="none"
            autoCorrect={false}
            returnKeyType="search"
            accessibilityLabel={copy.searchPlaceholder}
          />
          {searchQuery.length > 0 && (
            <AnimatedPressable
              onPress={() => {
                void triggerHaptic.light();
                onSearchChange('');
              }}
              scaleTarget={scalePress}
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              style={styles.clearBtn}
              accessibilityRole="button"
              accessibilityLabel={copy.clearSearch}>
              <X size={12} color={BRAND_COLORS.secondaryText} strokeWidth={2.4} />
            </AnimatedPressable>
          )}
        </View>

        <AnimatedPressable
          onPress={() => {
            void triggerHaptic.light();
            onOpenFilterSheet();
          }}
          scaleTarget={scalePress}
          style={[
            styles.filterTrigger,
            isFilterActive && styles.filterTriggerActive,
          ]}
          accessibilityRole="button"
          accessibilityLabel={copy.filterButtonLabel}>
          <SlidersHorizontal
            size={18}
            color={isFilterActive ? BRAND_COLORS.deepLavender : BRAND_COLORS.darkCharcoal}
            strokeWidth={2.2}
          />
          {isFilterActive && <View style={styles.activeDot} />}
        </AnimatedPressable>
      </View>

      {/* Horizontal Quick-Filter Chips */}
      {categories.length > 0 && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.chipsScrollContent}
          style={styles.chipsScrollView}>
          {/* 'All' Chip */}
          <AnimatedPressable
            onPress={() => handleChipPress(null)}
            scaleTarget={scalePress}
            style={[
              styles.chip,
              selectedCategory === null && styles.chipActive,
            ]}
            accessibilityRole="button"
            accessibilityLabel={copy.allFilter}>
            <Text
              style={[
                styles.chipText,
                selectedCategory === null && styles.chipTextActive,
              ]}>
              {copy.allFilter}
            </Text>
          </AnimatedPressable>

          {/* Individual Category Chips */}
          {categories.map((cat) => {
            const isSelected = selectedCategory === cat.name;
            return (
              <AnimatedPressable
                key={cat.name}
                onPress={() => handleChipPress(isSelected ? null : cat.name)}
                scaleTarget={scalePress}
                style={[
                  styles.chip,
                  isSelected && styles.chipActive,
                ]}
                accessibilityRole="button"
                accessibilityLabel={`${cat.name} (${cat.count})`}>
                <Text
                  style={[
                    styles.chipText,
                    isSelected && styles.chipTextActive,
                  ]}>
                  {cat.name}
                </Text>
                <Text
                  style={[
                    styles.chipCountText,
                    isSelected && styles.chipCountTextActive,
                  ]}>
                  {cat.count}
                </Text>
              </AnimatedPressable>
            );
          })}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 20,
    paddingBottom: 10,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 10,
  },
  searchField: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    paddingHorizontal: 14,
    height: 46,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1.5 },
    shadowOpacity: 0.03,
    shadowRadius: 4,
    elevation: 1,
  },
  searchFieldFocused: {
    borderColor: BRAND_COLORS.deepLavender,
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
  },
  searchIcon: {
    marginRight: 9,
  },
  textInput: {
    flex: 1,
    fontFamily: FONT_FAMILY.regular,
    fontSize: 14.5,
    color: BRAND_COLORS.darkCharcoal,
    paddingVertical: 0,
    letterSpacing: -0.1,
  },
  clearBtn: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: 'rgba(0, 0, 0, 0.05)',
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 6,
  },
  filterTrigger: {
    width: 46,
    height: 46,
    borderRadius: 14,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1.5 },
    shadowOpacity: 0.03,
    shadowRadius: 4,
    elevation: 1,
  },
  filterTriggerActive: {
    backgroundColor: 'rgba(128, 103, 161, 0.10)',
    borderColor: 'rgba(128, 103, 161, 0.35)',
  },
  activeDot: {
    position: 'absolute',
    top: 9,
    right: 9,
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: BRAND_COLORS.deepLavender,
  },
  chipsScrollView: {
    marginHorizontal: -20,
  },
  chipsScrollContent: {
    paddingHorizontal: 20,
    gap: 7,
    alignItems: 'center',
    paddingVertical: 2,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 12,
    paddingVertical: 6.5,
    borderRadius: 12,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
  },
  chipActive: {
    backgroundColor: BRAND_COLORS.deepLavender,
    borderColor: BRAND_COLORS.deepLavender,
  },
  chipText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12.5,
    fontWeight: '500',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.1,
  },
  chipTextActive: {
    color: BRAND_COLORS.pureWhite,
    fontWeight: '600',
  },
  chipCountText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    fontVariant: ['tabular-nums'],
  },
  chipCountTextActive: {
    color: 'rgba(255, 255, 255, 0.85)',
  },
});

