import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  LayoutChangeEvent,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { usePathname } from 'expo-router';
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
  Repeat,
  UserRound,
} from 'lucide-react-native';
import { LinearGradient } from 'expo-linear-gradient';

const TabLayoutContext = React.createContext<(index: number, e: LayoutChangeEvent) => void>(() => { });

export default function AppTabs() {
  return (
    <Tabs>
      <TabSlot style={{ height: '100%' }} />
      <TabList asChild>
        <FloatingTabBar>
          <TabTrigger name="index" href="/(tabs)" asChild>
            <TabButton
              index={0}
              label="Home"
              renderIcon={(color) => <House size={18} color={color} strokeWidth={2.2} />}
            />
          </TabTrigger>

          <TabTrigger name="transactions" href="/(tabs)/transactions" asChild>
            <TabButton
              index={1}
              label="Transactions"
              renderIcon={(color) => <ArrowLeftRight size={18} color={color} strokeWidth={2.2} />}
            />
          </TabTrigger>

          <TabTrigger name="subscriptions" href="/(tabs)/subscriptions" asChild>
            <TabButton
              index={2}
              label="Subscriptions"
              renderIcon={(color) => <Repeat size={18} color={color} strokeWidth={2.2} />}
            />
          </TabTrigger>

          <TabTrigger name="savings" href="/(tabs)/savings" asChild>
            <TabButton
              index={3}
              label="Savings"
              renderIcon={(color) => <PiggyBank size={18} color={color} strokeWidth={2.2} />}
            />
          </TabTrigger>

          <TabTrigger name="profile" href="/(tabs)/profile" asChild>
            <TabButton
              index={4}
              label="Profile"
              renderIcon={(color) => <UserRound size={18} color={color} strokeWidth={2.2} />}
            />
          </TabTrigger>
        </FloatingTabBar>
      </TabList>
    </Tabs>
  );
}

interface TabButtonProps extends TabTriggerSlotProps {
  index: number;
  label: string;
  renderIcon: (color: string) => React.ReactNode;
}

export function TabButton({
  index,
  label,
  renderIcon,
  isFocused,
  ...props
}: TabButtonProps) {
  const onTabLayout = React.useContext(TabLayoutContext);
  const { ref: _unusedRef, ...pressableProps } = props as unknown as {
    ref?: unknown;
    [key: string]: unknown;
  };

  const activeColor = '#000000';
  const inactiveColor = '#8E8E93';
  const currentColor = isFocused ? activeColor : inactiveColor;

  return (
    <Pressable
      {...(pressableProps as any)}
      onLayout={(e) => onTabLayout(index, e)}
      style={({ pressed }) => [
        styles.tabButton,
        pressed && styles.tabButtonPressed,
      ]}
      accessibilityRole="tab"
      accessibilityState={{ selected: isFocused }}>
      <View style={styles.iconContainer}>
        {renderIcon(currentColor)}
      </View>
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
  const pathname = usePathname();
  const [tabLayouts, setTabLayouts] = useState<Record<number, { x: number; width: number }>>({});

  const getRouteIndex = (path: string): number => {
    if (path.includes('/transactions')) return 1;
    if (path.includes('/subscriptions')) return 2;
    if (path.includes('/savings')) return 3;
    if (path.includes('/profile')) return 4;
    return 0; // Home
  };

  const activeIndex = useMemo(() => getRouteIndex(pathname), [pathname]);
  const isFirstRender = useRef(true);

  const indicatorTranslateX = useSharedValue(0);
  const indicatorWidth = useSharedValue(0);

  const handleTabLayout = (index: number, e: LayoutChangeEvent) => {
    const { x, width } = e.nativeEvent.layout;
    if (width > 0) {
      setTabLayouts((prev) => {
        if (prev[index]?.x === x && prev[index]?.width === width) {
          return prev;
        }
        return { ...prev, [index]: { x, width } };
      });
    }
  };

  const activeLayout = tabLayouts[activeIndex];

  useEffect(() => {
    if (activeLayout && activeLayout.width > 0) {
      if (isFirstRender.current) {
        indicatorTranslateX.value = activeLayout.x;
        indicatorWidth.value = activeLayout.width;
        isFirstRender.current = false;
      } else {
        indicatorTranslateX.value = withSpring(activeLayout.x, {
          damping: 20,
          stiffness: 220,
          mass: 0.6,
        });
        indicatorWidth.value = withSpring(activeLayout.width, {
          damping: 20,
          stiffness: 220,
          mass: 0.6,
        });
      }
    }
  }, [activeIndex, activeLayout, indicatorTranslateX, indicatorWidth]);

  const animatedIndicatorStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: indicatorTranslateX.value }],
    width: indicatorWidth.value > 0 ? indicatorWidth.value : undefined,
  }));

  const bottomClearance = insets.bottom > 20 ? insets.bottom - 16 : 12;

  return (
    <View
      {...props}
      style={[
        styles.tabBarWrapper,
        { paddingBottom: Math.max(bottomClearance, 12) },
      ]}>
      {/* Soft translucent gradient shadow: dims and softens scrolling content without completely blocking it */}
      <LinearGradient
        pointerEvents="none"
        colors={[
          'rgba(250, 249, 246, 0)',
          'rgba(235, 233, 228, 0.18)',
          'rgba(245, 243, 239, 0.42)',
          'rgba(245, 243, 239, 0.62)',
        ]}
        locations={[0, 0.35, 0.72, 1]}
        style={styles.bottomGradientScrim}
      />

      <View style={styles.floatingCapsule}>
        {/* Inner container where indicator and tabs share identical coordinate space */}
        <View style={styles.innerContainer}>
          {activeLayout && (
            <Animated.View
              pointerEvents="none"
              style={[
                styles.animatedIndicator,
                animatedIndicatorStyle,
              ]}
            />
          )}

          <TabLayoutContext.Provider value={handleTabLayout}>
            <View style={styles.tabsRow}>
              {children}
            </View>
          </TabLayoutContext.Provider>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
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
    height: 120,
    zIndex: 0,
  },
  floatingCapsule: {
    width: '100%',
    maxWidth: 440,
    height: 76,
    backgroundColor: '#FFFFFF',
    borderRadius: 38,
    paddingHorizontal: 4,
    paddingVertical: 4,
    flexDirection: 'row',
    alignItems: 'center',
    position: 'relative',
    borderWidth: 1,
    borderColor: 'rgba(0, 0, 0, 0.07)',
    zIndex: 1,
    // iOS shadow
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.10,
    shadowRadius: 18,
    // Android elevation
    elevation: 8,
  },
  innerContainer: {
    flex: 1,
    height: '100%',
    position: 'relative',
  },
  animatedIndicator: {
    position: 'absolute',
    top: 1,
    bottom: 1,
    borderRadius: 31,
    backgroundColor: '#F1F2F6', // Soft, clean neutral background
    zIndex: 0,
  },
  tabsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    height: '100%',
    zIndex: 1,
  },
  tabButton: {
    flex: 1,
    height: '100%',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 6,
    paddingBottom: 6,
    paddingHorizontal: 1,
  },
  tabButtonPressed: {
    opacity: 0.8,
    transform: [{ scale: 0.96 }],
  },
  iconContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    height: 22,
    marginBottom: 3,
  },
  tabLabel: {
    fontSize: 9.5,
    letterSpacing: -0.2,
    textAlign: 'center',
    maxWidth: '100%',
  },
  tabLabelActive: {
    color: '#000000',
    fontWeight: '700',
  },
  tabLabelInactive: {
    color: '#8E8E93',
    fontWeight: '500',
  },
});
