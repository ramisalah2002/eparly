import React from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  Extrapolation,
  interpolate,
  SharedValue,
  useAnimatedStyle,
} from 'react-native-reanimated';

import { ONBOARDING_COLORS } from '@/constants/onboarding';

interface OnboardingPaginationProps {
  total: number;
  scrollX: SharedValue<number>;
  screenWidth: number;
}

interface PaginationDotProps {
  index: number;
  scrollX: SharedValue<number>;
  screenWidth: number;
}

function PaginationDot({ index, scrollX, screenWidth }: PaginationDotProps) {
  const animatedDotStyle = useAnimatedStyle(() => {
    const inputRange = [
      (index - 1) * screenWidth,
      index * screenWidth,
      (index + 1) * screenWidth,
    ];

    const width = interpolate(
      scrollX.value,
      inputRange,
      [7, 24, 7],
      Extrapolation.CLAMP
    );

    const opacity = interpolate(
      scrollX.value,
      inputRange,
      [0.32, 1, 0.32],
      Extrapolation.CLAMP
    );

    return {
      width,
      opacity,
    };
  });

  return <Animated.View style={[styles.dot, animatedDotStyle]} />;
}

export function OnboardingPagination({
  total,
  scrollX,
  screenWidth,
}: OnboardingPaginationProps) {
  return (
    <View style={styles.container}>
      {Array.from({ length: total }).map((_, index) => (
        <PaginationDot
          key={index}
          index={index}
          scrollX={scrollX}
          screenWidth={screenWidth}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    height: 12,
  },
  dot: {
    height: 7,
    borderRadius: 3.5,
    backgroundColor: ONBOARDING_COLORS.mutedLavender,
  },
});
