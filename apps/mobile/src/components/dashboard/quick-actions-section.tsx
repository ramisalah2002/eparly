import React, { useMemo } from 'react';
import {
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, {
  FadeInDown,
  useReducedMotion,
} from 'react-native-reanimated';
import Svg, {
  Circle,
  Line,
  Path,
  Rect,
} from 'react-native-svg';
import { ArrowUpRight } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';

export interface QuickActionItem {
  id: 'import' | 'subscriptions' | 'spending' | 'savings';
  title: string;
  descriptor: string;
  backgroundColor: string;
  accessibilityHint: string;
  onPress: () => void;
}

export interface QuickActionsSectionProps {
  onImportTransactions: () => void;
  onManageSubscriptions: () => void;
  onExploreSpending: () => void;
  onFindWaysToSave: () => void;
}

/**
 * 1. Custom Minimal Illustration: Document with upward import arrow & CSV lines
 */
function ImportTransactionsIllustration() {
  return (
    <Svg width={42} height={42} viewBox="0 0 44 44" fill="none">
      {/* Document Outline with folded top-right corner */}
      <Path
        d="M10 7C10 5.89543 10.8954 5 12 5H24L32 13V35C32 36.1046 31.1046 37 30 37H12C10.8954 37 10 36.1046 10 35V7Z"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Corner fold */}
      <Path
        d="M24 5V13H32"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Abstract CSV transaction rows */}
      <Line x1="15" y1="18" x2="21" y2="18" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      <Line x1="15" y1="23" x2="20" y2="23" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      {/* Upward import arrow circle badge */}
      <Circle cx="25" cy="27" r="7.5" fill={BRAND_COLORS.quickActionLavender} stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} />
      <Path
        d="M25 30.5V23.5M25 23.5L22.5 26M25 23.5L27.5 26"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

/**
 * 2. Custom Minimal Illustration: Calendar outline with recurring cyclical arrows
 */
function ManageSubscriptionsIllustration() {
  return (
    <Svg width={42} height={42} viewBox="0 0 44 44" fill="none">
      {/* Calendar base card */}
      <Rect
        x="8"
        y="9"
        width="28"
        height="26"
        rx="5"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Calendar top pins */}
      <Line x1="15" y1="6" x2="15" y2="10" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      <Line x1="29" y1="6" x2="29" y2="10" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      {/* Calendar header dividing line */}
      <Line x1="8" y1="16" x2="36" y2="16" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      {/* Circular recurring arrows symbol in center */}
      <Path
        d="M17 25C17 22.2386 19.2386 20 22 20C24.3 20 26.2 21.5 26.8 23.6"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
      />
      <Path
        d="M27 23.5H23.5"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <Path
        d="M27 27C27 29.7614 24.7614 32 22 32C19.7 32 17.8 30.5 17.2 28.4"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
      />
      <Path
        d="M17 28.5H20.5"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

/**
 * 3. Custom Minimal Illustration: Ascending bar chart with analytical trajectory line
 */
function ExploreSpendingIllustration() {
  return (
    <Svg width={42} height={42} viewBox="0 0 44 44" fill="none">
      {/* Ascending chart bars with rounded tops */}
      <Rect x="9" y="24" width="6" height="13" rx="3" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} />
      <Rect x="19" y="16" width="6" height="21" rx="3" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} />
      <Rect x="29" y="9" width="6" height="28" rx="3" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} />
      {/* Upward trend trajectory line & point */}
      <Path
        d="M8 20C14 17 22 13 32 6"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeDasharray="2 3"
      />
      <Circle cx="32" cy="6" r="2.5" fill={BRAND_COLORS.darkCharcoal} />
    </Svg>
  );
}

/**
 * 4. Custom Minimal Illustration: Stylized piggy bank with growth sparkle
 */
function FindWaysToSaveIllustration() {
  return (
    <Svg width={42} height={42} viewBox="0 0 44 44" fill="none">
      {/* Stylized piggy silhouette */}
      <Path
        d="M11 26C11 20 15 15 22 15C29 15 33 19 33 25C33 27 32 29 30.5 30.5C29.5 31.5 28 32 26 32H16C13.5 32 11 30 11 26Z"
        stroke={BRAND_COLORS.darkCharcoal}
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Snout */}
      <Path d="M9 23H11" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      {/* Ear */}
      <Path d="M22 15L24 10L27 15" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      {/* Eye */}
      <Circle cx="16" cy="21" r="1.5" fill={BRAND_COLORS.darkCharcoal} />
      {/* Coin slot */}
      <Line x1="21" y1="19" x2="25" y2="19" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      {/* Legs */}
      <Line x1="17" y1="32" x2="17" y2="36" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      <Line x1="26" y1="32" x2="26" y2="36" stroke={BRAND_COLORS.darkCharcoal} strokeWidth={1.8} strokeLinecap="round" />
      {/* Sparkle star / savings growth indicator */}
      <Path
        d="M33 9L34 12L37 13L34 14L33 17L32 14L29 13L32 12L33 9Z"
        fill={BRAND_COLORS.darkCharcoal}
      />
    </Svg>
  );
}

export function QuickActionsSection({
  onImportTransactions,
  onManageSubscriptions,
  onExploreSpending,
  onFindWaysToSave,
}: QuickActionsSectionProps) {
  const shouldReduceMotion = useReducedMotion();

  const actions: (QuickActionItem & { renderIllustration: () => React.ReactNode })[] = useMemo(
    () => [
      {
        id: 'import',
        title: 'Import your transactions',
        descriptor: 'CSV import',
        backgroundColor: BRAND_COLORS.quickActionLavender,
        accessibilityHint: 'Opens CSV import modal to load bank transactions',
        onPress: onImportTransactions,
        renderIllustration: () => <ImportTransactionsIllustration />,
      },
      {
        id: 'subscriptions',
        title: 'Manage subscriptions',
        descriptor: 'Recurring bills',
        backgroundColor: BRAND_COLORS.quickActionPeach,
        accessibilityHint: 'Navigates to recurring subscriptions screen',
        onPress: onManageSubscriptions,
        renderIllustration: () => <ManageSubscriptionsIllustration />,
      },
      {
        id: 'spending',
        title: 'Explore your spending',
        descriptor: 'Insights',
        backgroundColor: BRAND_COLORS.quickActionYellow,
        accessibilityHint: 'Jumps to spending chart overview',
        onPress: onExploreSpending,
        renderIllustration: () => <ExploreSpendingIllustration />,
      },
      {
        id: 'savings',
        title: 'Find ways to save',
        descriptor: 'Savings',
        backgroundColor: BRAND_COLORS.quickActionMint,
        accessibilityHint: 'Navigates to savings and goals screen',
        onPress: onFindWaysToSave,
        renderIllustration: () => <FindWaysToSaveIllustration />,
      },
    ],
    [onImportTransactions, onManageSubscriptions, onExploreSpending, onFindWaysToSave]
  );

  return (
    <View style={styles.container}>
      {/* Section Header */}
      <View style={styles.headerRow}>
        <Text style={styles.sectionHeading}>Quick Actions</Text>
      </View>

      {/* 2x2 Grid of Cards */}
      <View style={styles.gridContainer}>
        {actions.map((item, index) => {
          const CardContainer = shouldReduceMotion ? View : Animated.View;
          const animationProps = shouldReduceMotion
            ? {}
            : {
                entering: FadeInDown.delay(index * 60).duration(420),
              };

          return (
            <CardContainer
              key={item.id}
              style={styles.cardWrapper}
              {...animationProps}>
              <AnimatedPressable
                onPress={item.onPress}
                scaleTarget={MOTION.scale.cardPress}
                haptic="light"
                activeOpacity={0.94}
                style={[
                  styles.cardPressable,
                  { backgroundColor: item.backgroundColor },
                ]}
                accessibilityRole="button"
                accessibilityLabel={`${item.title}, ${item.descriptor}`}
                accessibilityHint={item.accessibilityHint}>
                {/* Top: Descriptor badge & Bold Title */}
                <View style={styles.cardTopArea}>
                  <View style={styles.descriptorBadge}>
                    <Text style={styles.descriptorText}>{item.descriptor}</Text>
                  </View>
                  <Text
                    style={styles.cardTitle}
                    numberOfLines={3}
                    adjustsFontSizeToFit
                    minimumFontScale={0.88}>
                    {item.title}
                  </Text>
                </View>

                {/* Bottom: Custom Illustration & Circular Action Button */}
                <View style={styles.cardBottomRow}>
                  <View style={styles.illustrationWrapper}>
                    {item.renderIllustration()}
                  </View>
                  <View style={styles.arrowButton}>
                    <ArrowUpRight
                      size={17}
                      color={BRAND_COLORS.darkCharcoal}
                      strokeWidth={2.2}
                    />
                  </View>
                </View>
              </AnimatedPressable>
            </CardContainer>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
    marginTop: 2,
    marginBottom: 16,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
    paddingHorizontal: 2,
  },
  sectionHeading: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 20,
    lineHeight: 25,
    fontWeight: '700',
    letterSpacing: -0.3,
    color: BRAND_COLORS.darkCharcoal,
  },
  gridContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  cardWrapper: {
    flexBasis: '47%',
    flexGrow: 1,
  },
  cardPressable: {
    minHeight: 162,
    borderRadius: 24,
    paddingHorizontal: 14,
    paddingTop: 14,
    paddingBottom: 12,
    justifyContent: 'space-between',
  },
  cardTopArea: {
    alignItems: 'flex-start',
  },
  descriptorBadge: {
    backgroundColor: 'rgba(36, 37, 53, 0.08)',
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 3,
    marginBottom: 8,
  },
  descriptorText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
    letterSpacing: 0.1,
    color: BRAND_COLORS.darkCharcoal,
  },
  cardTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '700',
    letterSpacing: -0.3,
    color: BRAND_COLORS.darkCharcoal,
  },
  cardBottomRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginTop: 10,
  },
  illustrationWrapper: {
    justifyContent: 'flex-end',
    alignItems: 'flex-start',
  },
  arrowButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255, 255, 255, 0.65)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(36, 37, 53, 0.08)',
  },
});

