import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { ChevronRight } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { triggerHaptic } from '@/utils/haptics';

export interface SettingsRowProps {
  icon?: React.ComponentType<{ size: number; color: string; strokeWidth?: number }>;
  iconColor?: string;
  iconBgColor?: string;
  label: string;
  subtitle?: string;
  value?: string;
  badge?: {
    text: string;
    color?: string;
    bgColor?: string;
  };
  showChevron?: boolean;
  onPress?: () => void;
  destructive?: boolean;
  disabled?: boolean;
  accessibilityLabel?: string;
}

/**
 * Editorial Settings Row.
 * Standardized across Eparly's Profile experience with calibrated
 * press dynamics, squircle icon badges, and baseline-aligned values.
 */
export function SettingsRow({
  icon: IconComponent,
  iconColor = BRAND_COLORS.deepLavender,
  iconBgColor = BRAND_COLORS.veryLightLavender,
  label,
  subtitle,
  value,
  badge,
  showChevron = true,
  onPress,
  destructive = false,
  disabled = false,
  accessibilityLabel,
}: SettingsRowProps) {
  const isReducedMotion = useReducedMotion();
  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.cardPress;

  const handlePress = () => {
    if (disabled || !onPress) return;
    void triggerHaptic.light();
    onPress();
  };

  const isInteractive = Boolean(onPress) && !disabled;

  const content = (
    <View style={styles.rowContent}>
      {/* Icon Squircle */}
      {IconComponent && (
        <View style={[styles.iconContainer, { backgroundColor: iconBgColor }]}>
          <IconComponent size={17} color={iconColor} strokeWidth={2.2} />
        </View>
      )}

      {/* Label and Subtitle */}
      <View style={styles.labelColumn}>
        <Text
          style={[
            styles.label,
            destructive && styles.labelDestructive,
            disabled && styles.labelDisabled,
          ]}
          numberOfLines={1}>
          {label}
        </Text>
        {subtitle && (
          <Text style={styles.subtitle} numberOfLines={1}>
            {subtitle}
          </Text>
        )}
      </View>

      {/* Right-hand side (Badge, Value, Chevron) */}
      <View style={styles.rightGroup}>
        {badge && (
          <View
            style={[
              styles.badge,
              badge.bgColor ? { backgroundColor: badge.bgColor } : undefined,
            ]}>
            <Text
              style={[
                styles.badgeText,
                badge.color ? { color: badge.color } : undefined,
              ]}>
              {badge.text}
            </Text>
          </View>
        )}

        {value && (
          <Text style={styles.valueText} numberOfLines={1}>
            {value}
          </Text>
        )}

        {showChevron && isInteractive && (
          <ChevronRight size={17} color="#A89BBE" strokeWidth={2.2} />
        )}
      </View>
    </View>
  );

  if (!isInteractive) {
    return <View style={styles.staticWrapper}>{content}</View>;
  }

  return (
    <AnimatedPressable
      onPress={handlePress}
      scaleTarget={scalePress}
      activeOpacity={0.92}
      style={styles.pressableWrapper}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel || `${label} ${value || ''}`}>
      {content}
    </AnimatedPressable>
  );
}

const styles = StyleSheet.create({
  pressableWrapper: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 14,
  },
  staticWrapper: {
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  rowContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  iconContainer: {
    width: 34,
    height: 34,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  labelColumn: {
    flex: 1,
    gap: 1.5,
  },
  label: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 14,
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.1,
  },
  labelDestructive: {
    color: '#E11D48',
  },
  labelDisabled: {
    color: BRAND_COLORS.secondaryText,
    opacity: 0.6,
  },
  subtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
  },
  rightGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  valueText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
  },
  badge: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 7,
    paddingVertical: 2.5,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.2)',
  },
  badgeText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 10.5,
    color: BRAND_COLORS.darkCharcoal,
  },
});

