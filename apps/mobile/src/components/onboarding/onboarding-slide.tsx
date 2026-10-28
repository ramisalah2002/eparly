import { Image } from 'expo-image';
import React, { useEffect } from 'react';
import { Dimensions, Platform, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  Extrapolation,
  interpolate,
  SharedValue,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';

import { ONBOARDING_COLORS, OnboardingSlideItem } from '@/constants/onboarding';

interface OnboardingSlideProps {
  item: OnboardingSlideItem;
  index: number;
  width: number;
  height: number;
  scrollX: SharedValue<number>;
  isActive: boolean;
  reducedMotion?: boolean;
}

export function OnboardingSlide({
  item,
  index,
  width,
  height,
  scrollX,
  isActive,
  reducedMotion = false,
}: OnboardingSlideProps) {
  // Reanimated entrance values
  const imageOpacity = useSharedValue(reducedMotion ? 1 : 0);
  const imageTranslateY = useSharedValue(reducedMotion ? 0 : 20);
  const imageScale = useSharedValue(reducedMotion ? 1 : 0.96);

  const headlineOpacity = useSharedValue(reducedMotion ? 1 : 0);
  const headlineTranslateY = useSharedValue(reducedMotion ? 0 : 14);

  const descOpacity = useSharedValue(reducedMotion ? 1 : 0);
  const descTranslateY = useSharedValue(reducedMotion ? 0 : 10);

  useEffect(() => {
    if (reducedMotion) {
      imageOpacity.value = 1;
      imageTranslateY.value = 0;
      imageScale.value = 1;
      headlineOpacity.value = 1;
      headlineTranslateY.value = 0;
      descOpacity.value = 1;
      descTranslateY.value = 0;
      return;
    }

    if (isActive) {
      // 1. Image entrance
      imageOpacity.value = withTiming(1, {
        duration: 700,
        easing: Easing.out(Easing.cubic),
      });
      imageTranslateY.value = withTiming(0, {
        duration: 750,
        easing: Easing.out(Easing.cubic),
      });
      imageScale.value = withTiming(1, {
        duration: 750,
        easing: Easing.out(Easing.cubic),
      });

      // 2. Headline entrance (staggered)
      headlineOpacity.value = withDelay(
        220,
        withTiming(1, {
          duration: 500,
          easing: Easing.out(Easing.cubic),
        })
      );
      headlineTranslateY.value = withDelay(
        220,
        withTiming(0, {
          duration: 500,
          easing: Easing.out(Easing.cubic),
        })
      );

      // 3. Description entrance (staggered)
      descOpacity.value = withDelay(
        380,
        withTiming(1, {
          duration: 500,
          easing: Easing.out(Easing.cubic),
        })
      );
      descTranslateY.value = withDelay(
        380,
        withTiming(0, {
          duration: 500,
          easing: Easing.out(Easing.cubic),
        })
      );
    }
  }, [isActive, reducedMotion, descOpacity, descTranslateY, headlineOpacity, headlineTranslateY, imageOpacity, imageScale, imageTranslateY]);

  // Subtle interactive parallax during horizontal swipe
  const animatedParallaxStyle = useAnimatedStyle(() => {
    if (reducedMotion) {
      return {
        opacity: imageOpacity.value,
        transform: [{ translateY: imageTranslateY.value }, { scale: imageScale.value }],
      };
    }

    const inputRange = [
      (index - 1) * width,
      index * width,
      (index + 1) * width,
    ];

    const translateX = interpolate(
      scrollX.value,
      inputRange,
      [width * 0.16, 0, -width * 0.16],
      Extrapolation.CLAMP
    );

    return {
      opacity: imageOpacity.value,
      transform: [
        { translateX },
        { translateY: imageTranslateY.value },
        { scale: imageScale.value },
      ],
    };
  });

  const animatedHeadlineStyle = useAnimatedStyle(() => ({
    opacity: headlineOpacity.value,
    transform: [{ translateY: headlineTranslateY.value }],
  }));

  const animatedDescStyle = useAnimatedStyle(() => ({
    opacity: descOpacity.value,
    transform: [{ translateY: descTranslateY.value }],
  }));

  // Responsive sizing
  // Hero illustration occupies ~57-60% of usable height for an immersive, prominent look
  const heroHeight = Math.min(Math.max(height * 0.58, 310), 490);

  return (
    <View style={[styles.slideContainer, { width }]}>
      {/* Hero Illustration Container */}
      <View style={[styles.heroArea, { height: heroHeight }]}>
        <Animated.View style={[styles.imageWrapper, animatedParallaxStyle]}>
          <Image
            source={item.image}
            style={styles.illustration}
            contentFit="contain"
            priority="high"
          />
        </Animated.View>
      </View>

      {/* Typography Content Area */}
      <View style={styles.contentArea}>
        <Animated.View style={animatedHeadlineStyle}>
          <Text style={styles.headline} numberOfLines={2} adjustsFontSizeToFit>
            {item.headline}
          </Text>
        </Animated.View>

        <Animated.View style={animatedDescStyle}>
          <Text style={styles.description}>{item.description}</Text>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  slideContainer: {
    alignItems: 'center',
    justifyContent: 'flex-start',
    paddingHorizontal: 12,
  },
  heroArea: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  imageWrapper: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  illustration: {
    width: '100%',
    height: '100%',
    maxWidth: 420,
  },
  contentArea: {
    alignItems: 'center',
    justifyContent: 'flex-start',
    width: '100%',
    paddingTop: 10,
    paddingHorizontal: 20,
  },
  headline: {
    fontSize: 30,
    fontWeight: '700',
    color: ONBOARDING_COLORS.darkCharcoal,
    textAlign: 'center',
    letterSpacing: -0.6,
    lineHeight: 38,
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif-medium',
      default: 'system-ui',
    }),
  },
  description: {
    fontSize: 15.5,
    fontWeight: '400',
    color: ONBOARDING_COLORS.secondaryText,
    textAlign: 'center',
    letterSpacing: -0.1,
    lineHeight: 23,
    marginTop: 12,
    maxWidth: 320,
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif',
      default: 'system-ui',
    }),
  },
});
