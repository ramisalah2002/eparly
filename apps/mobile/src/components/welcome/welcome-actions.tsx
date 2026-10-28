import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { BRAND_COLORS } from '@/constants/colors';

interface WelcomeActionsProps {
  onGetStarted: () => void;
  onSignIn: () => void;
}

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

function ActionButton({
  label,
  onPress,
  variant,
}: {
  label: string;
  onPress: () => void;
  variant: 'primary' | 'secondary';
}) {
  const scale = useSharedValue(1);

  const handlePressIn = () => {
    scale.value = withTiming(0.982, { duration: 110 });
  };

  const handlePressOut = () => {
    scale.value = withTiming(1, { duration: 150 });
  };

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const isPrimary = variant === 'primary';

  return (
    <AnimatedPressable
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      accessible={true}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={[styles.buttonWrapper, animatedStyle]}>
      {({ pressed }) => (
        <View
          style={[
            styles.buttonBase,
            isPrimary ? styles.primaryButton : styles.secondaryButton,
            pressed && (isPrimary ? styles.primaryPressed : styles.secondaryPressed),
          ]}>
          <Text
            style={[
              styles.buttonText,
              isPrimary ? styles.primaryText : styles.secondaryText,
            ]}>
            {label}
          </Text>
        </View>
      )}
    </AnimatedPressable>
  );
}

export function WelcomeActions({
  onGetStarted,
  onSignIn,
}: WelcomeActionsProps) {
  return (
    <View style={styles.container}>
      <ActionButton
        label="Get Started"
        onPress={onGetStarted}
        variant="primary"
      />
      <ActionButton
        label="I already have an account"
        onPress={onSignIn}
        variant="secondary"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
    gap: 12,
  },
  buttonWrapper: {
    width: '100%',
  },
  buttonBase: {
    height: 56,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  primaryButton: {
    backgroundColor: BRAND_COLORS.mutedLavender,
  },
  primaryPressed: {
    backgroundColor: BRAND_COLORS.mutedLavenderPressed,
  },
  secondaryButton: {
    backgroundColor: BRAND_COLORS.secondaryButtonBg,
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.borderLavender,
  },
  secondaryPressed: {
    backgroundColor: BRAND_COLORS.secondaryButtonPressed,
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  primaryText: {
    color: BRAND_COLORS.darkCharcoal,
  },
  secondaryText: {
    color: BRAND_COLORS.darkCharcoal,
  },
});
