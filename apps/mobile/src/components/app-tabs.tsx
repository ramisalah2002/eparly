import React, { useEffect } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Tabs,
  TabList,
  TabTrigger,
  TabSlot,
  TabTriggerSlotProps,
  TabListProps,
} from 'expo-router/ui';
import {
  ArrowLeftRight,
  House,
  PiggyBank,
  Repeat2,
  UserRound,
} from 'lucide-react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { triggerHaptic } from '@/utils/haptics';

export default function AppTabs() {
  return (
    <Tabs>
      <TabSlot style={styles.tabSlot} />
      <TabList asChild>
        <FloatingTabBar>
          <TabTrigger name="index" href="/(tabs)" asChild>
            <TabButton
              label="Home"
              renderIcon={(color) => <House size={21} color={color} strokeWidth={2.0} />}
            />
          </TabTrigger>

          <TabTrigger name="transactions" href="/(tabs)/transactions" asChild>
            <TabButton
              label="Activity"
              renderIcon={(color) => <ArrowLeftRight size={21} color={color} strokeWidth={2.0} />}
            />
          </TabTrigger>

          <TabTrigger name="subscriptions" href="/(tabs)/subscriptions" asChild>
            <TabButton
              label="Recurring"
              renderIcon={(color) => <Repeat2 size={21} color={color} strokeWidth={2.0} />}
            />
          </TabTrigger>

          <TabTrigger name="savings" href="/(tabs)/savings" asChild>
            <TabButton
              label="Savings"
              renderIcon={(color) => <PiggyBank size={21} color={color} strokeWidth={2.0} />}
            />
          </TabTrigger>

          <TabTrigger name="profile" href="/(tabs)/profile" asChild>
            <TabButton
              label="Profile"
              renderIcon={(color) => <UserRound size={21} color={color} strokeWidth={2.0} />}
            />
          </TabTrigger>
        </FloatingTabBar>
      </TabList>
    </Tabs>
  );
}

export interface TabButtonProps extends TabTriggerSlotProps {
  label: string;
  renderIcon: (color: string) => React.ReactNode;
}

export function TabButton({
  label,
  renderIcon,
  isFocused,
  ...props
}: TabButtonProps) {
  const { ref: _unusedRef, ...pressableProps } = props as unknown as {
    ref?: unknown;
    [key: string]: unknown;
  };

  const isReduced = useReducedMotion();
  const iconScale = useSharedValue(isFocused ? 1.06 : 1);

  useEffect(() => {
    iconScale.value = isReduced
      ? (isFocused ? 1.06 : 1)
      : withSpring(isFocused ? 1.06 : 1, MOTION.spring.selection);
  }, [isFocused, isReduced, iconScale]);

  const animatedIconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: iconScale.value }],
  }));

  const activeColor = BRAND_COLORS.deepLavender;
  const inactiveColor = BRAND_COLORS.secondaryText;
  const currentColor = isFocused ? activeColor : inactiveColor;

  const handlePress = (event: any) => {
    triggerHaptic.selection();
    if (typeof (pressableProps as any).onPress === 'function') {
      (pressableProps as any).onPress(event);
    }
  };

  return (
    <Pressable
      {...(pressableProps as any)}
      onPress={handlePress}
      style={({ pressed }) => [
        styles.tabButton,
        pressed && styles.tabButtonPressed,
      ]}
      accessibilityRole="tab"
      accessibilityState={{ selected: isFocused }}
      accessibilityLabel={`${label}, tab`}>
      <Animated.View
        style={[
          styles.iconCapsule,
          isFocused ? styles.iconCapsuleActive : styles.iconCapsuleInactive,
          animatedIconStyle,
        ]}>
        {renderIcon(currentColor)}
      </Animated.View>
      <Text
        style={[
          styles.tabLabel,
          isFocused ? styles.tabLabelActive : styles.tabLabelInactive,
        ]}
        numberOfLines={1}>
        {label}
      </Text>
    </Pressable>
  );
}

function FloatingTabBar({ children, ...props }: React.PropsWithChildren<TabListProps>) {
  const insets = useSafeAreaInsets();
  const bottomClearance = insets.bottom > 20 ? insets.bottom - 8 : 12;

  return (
    <View
      {...props}
      style={[
        styles.tabBarWrapper,
        { paddingBottom: Math.max(bottomClearance, 12) },
      ]}>
      {/* Soft translucent gradient scrim: fades scrolling content behind dock */}
      <LinearGradient
        pointerEvents="none"
        colors={[
          'rgba(250, 249, 246, 0)',
          'rgba(240, 236, 245, 0.35)',
          'rgba(250, 249, 246, 0.85)',
        ]}
        locations={[0, 0.45, 1]}
        style={styles.bottomGradientScrim}
      />

      <View style={styles.floatingCapsule}>
        <View style={styles.tabsRow}>
          {children}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  tabSlot: {
    height: '100%',
  },
  tabBarWrapper: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    alignItems: 'center',
    paddingHorizontal: 16,
    pointerEvents: 'box-none',
    zIndex: 999,
  },
  bottomGradientScrim: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 110,
    zIndex: 0,
  },
  floatingCapsule: {
    width: '100%',
    maxWidth: 440,
    height: 68,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 34,
    paddingHorizontal: 8,
    paddingVertical: 4,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    zIndex: 1,
    // iOS shadow
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    // Android elevation
    elevation: 6,
  },
  tabsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    height: '100%',
  },
  tabButton: {
    flex: 1,
    height: '100%',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 2,
    paddingHorizontal: 2,
  },
  tabButtonPressed: {
    opacity: 0.88,
    transform: [{ scale: 0.94 }],
  },
  iconCapsule: {
    width: 48,
    height: 34,
    borderRadius: 17,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 2,
  },
  iconCapsuleActive: {
    backgroundColor: 'rgba(128, 103, 161, 0.12)',
  },
  iconCapsuleInactive: {
    backgroundColor: 'transparent',
  },
  tabLabel: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 10.5,
    letterSpacing: -0.15,
    textAlign: 'center',
    maxWidth: '100%',
  },
  tabLabelActive: {
    fontFamily: FONT_FAMILY.bold,
    color: BRAND_COLORS.deepLavender,
    fontWeight: '700',
  },
  tabLabelInactive: {
    fontFamily: FONT_FAMILY.medium,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
});
