import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { Upload } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { getActivityCopy } from '@/constants/activity-copy';

interface ActivityHeaderProps {
  title?: string;
  totalCount: number;
  filteredCount: number;
  locale?: string;
  onImportPress: () => void;
}

/**
 * Editorial Activity Header (Quiet Monograph aesthetic).
 * Replaces the previous bright mint gradient with a calm, high-contrast
 * typographic header and a tactile, restrained CSV import pill action.
 */
export function ActivityHeader({
  title,
  totalCount,
  filteredCount,
  locale = 'en-IE',
  onImportPress,
}: ActivityHeaderProps) {
  const copy = getActivityCopy(locale);
  const isReducedMotion = useReducedMotion();
  const resolvedTitle = title || copy.title;

  const countText =
    filteredCount !== totalCount
      ? `${filteredCount} ${copy.operationsOf} ${totalCount} ${copy.operationsRecorded}`
      : `${totalCount} ${copy.operationsRecorded}`;

  return (
    <View style={styles.headerContainer}>
      <View style={styles.titleColumn}>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {resolvedTitle}
        </Text>
        <Text style={styles.headerSubtitle} numberOfLines={1}>
          {countText}
        </Text>
      </View>

      <AnimatedPressable
        onPress={onImportPress}
        scaleTarget={isReducedMotion ? 1.0 : MOTION.scale.buttonPress}
        haptic="light"
        style={styles.importBtn}
        accessibilityRole="button"
        accessibilityLabel={copy.importCsv}>
        <View style={styles.importIconCircle}>
          <Upload size={13} color={BRAND_COLORS.deepLavender} strokeWidth={2.4} />
        </View>
        <Text style={styles.importBtnText}>{copy.importCsv}</Text>
      </AnimatedPressable>
    </View>
  );
}

const styles = StyleSheet.create({
  headerContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 14,
  },
  titleColumn: {
    flex: 1,
  },
  headerTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 26,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.5,
  },
  headerSubtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
    letterSpacing: -0.1,
  },
  importBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(128, 103, 161, 0.10)',
    borderWidth: 1,
    borderColor: 'rgba(128, 103, 161, 0.22)',
    paddingLeft: 7,
    paddingRight: 12,
    paddingVertical: 6,
    borderRadius: 20,
    gap: 7,
  },
  importIconCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: BRAND_COLORS.pureWhite,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  importBtnText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 12.5,
    fontWeight: '600',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: -0.1,
  },
});

