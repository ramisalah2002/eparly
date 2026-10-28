import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { ONBOARDING_COLORS } from '@/constants/onboarding';

interface OnboardingButtonProps {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export function OnboardingButton({
  label,
  onPress,
  disabled = false,
}: OnboardingButtonProps) {
  const scale = useSharedValue(1);

  const handlePressIn = () => {
    scale.value = withTiming(0.982, { duration: 120 });
  };

  const handlePressOut = () => {
    scale.value = withTiming(1, { duration: 160 });
  };

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <AnimatedPressable
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      disabled={disabled}
      accessible={true}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={[styles.buttonContainer, animatedStyle]}>
      {({ pressed }) => (
        <View
          style={[
            styles.buttonInner,
            pressed && styles.buttonPressed,
          ]}>
          <Text style={styles.buttonText}>{label}</Text>
        </View>
      )}
    </AnimatedPressable>
  );
}

const styles = StyleSheet.create({
  buttonContainer: {
    width: '100%',
    alignSelf: 'stretch',
  },
  buttonInner: {
    height: 56,
    borderRadius: 18,
    backgroundColor: ONBOARDING_COLORS.mutedLavender,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonPressed: {
    backgroundColor: ONBOARDING_COLORS.mutedLavenderPressed,
  },
  buttonText: {
    color: ONBOARDING_COLORS.darkCharcoal,
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
});
