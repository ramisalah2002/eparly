import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Image } from 'expo-image';
import React, { useEffect } from 'react';
import {
  Platform,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { EparlyLogo } from '@/components/eparly-logo';
import { PastelBackground } from '@/components/onboarding/pastel-background';
import { WelcomeActions } from '@/components/welcome/welcome-actions';
import { BRAND_COLORS } from '@/constants/colors';

export default function WelcomeScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();
  const reducedMotion = useReducedMotion();

  // Reanimated entrance animation values
  const heroOpacity = useSharedValue(reducedMotion ? 1 : 0);
  const heroScale = useSharedValue(reducedMotion ? 1 : 0.95);
  const heroTranslateY = useSharedValue(reducedMotion ? 0 : 20);

  const headlineOpacity = useSharedValue(reducedMotion ? 1 : 0);
  const headlineTranslateY = useSharedValue(reducedMotion ? 0 : 12);

  const descOpacity = useSharedValue(reducedMotion ? 1 : 0);
  const descTranslateY = useSharedValue(reducedMotion ? 0 : 10);

  const actionsOpacity = useSharedValue(reducedMotion ? 1 : 0);
  const actionsTranslateY = useSharedValue(reducedMotion ? 0 : 10);

  useEffect(() => {
    if (reducedMotion) {
      heroOpacity.value = 1;
      heroScale.value = 1;
      heroTranslateY.value = 0;
      headlineOpacity.value = 1;
      headlineTranslateY.value = 0;
      descOpacity.value = 1;
      descTranslateY.value = 0;
      actionsOpacity.value = 1;
      actionsTranslateY.value = 0;
      return;
    }

    // Step 1 — Illustration Entrance
    heroOpacity.value = withTiming(1, {
      duration: 800,
      easing: Easing.out(Easing.cubic),
    });
    heroScale.value = withTiming(1, {
      duration: 850,
      easing: Easing.out(Easing.cubic),
    });
    heroTranslateY.value = withTiming(0, {
      duration: 850,
      easing: Easing.out(Easing.cubic),
    });

    // Step 2 — Headline
    headlineOpacity.value = withDelay(
      220,
      withTiming(1, {
        duration: 450,
        easing: Easing.out(Easing.cubic),
      })
    );
    headlineTranslateY.value = withDelay(
      220,
      withTiming(0, {
        duration: 450,
        easing: Easing.out(Easing.cubic),
      })
    );

    // Step 3 — Description
    descOpacity.value = withDelay(
      360,
      withTiming(1, {
        duration: 450,
        easing: Easing.out(Easing.cubic),
      })
    );
    descTranslateY.value = withDelay(
      360,
      withTiming(0, {
        duration: 450,
        easing: Easing.out(Easing.cubic),
      })
    );

    // Step 4 — Action Buttons
    actionsOpacity.value = withDelay(
      500,
      withTiming(1, {
        duration: 450,
        easing: Easing.out(Easing.cubic),
      })
    );
    actionsTranslateY.value = withDelay(
      500,
      withTiming(0, {
        duration: 450,
        easing: Easing.out(Easing.cubic),
      })
    );
  }, [
    actionsOpacity,
    actionsTranslateY,
    descOpacity,
    descTranslateY,
    headlineOpacity,
    headlineTranslateY,
    heroOpacity,
    heroScale,
    heroTranslateY,
    reducedMotion,
  ]);

  const animatedHeroStyle = useAnimatedStyle(() => ({
    opacity: heroOpacity.value,
    transform: [{ translateY: heroTranslateY.value }, { scale: heroScale.value }],
  }));

  const animatedHeadlineStyle = useAnimatedStyle(() => ({
    opacity: headlineOpacity.value,
    transform: [{ translateY: headlineTranslateY.value }],
  }));

  const animatedDescStyle = useAnimatedStyle(() => ({
    opacity: descOpacity.value,
    transform: [{ translateY: descTranslateY.value }],
  }));

  const animatedActionsStyle = useAnimatedStyle(() => ({
    opacity: actionsOpacity.value,
    transform: [{ translateY: actionsTranslateY.value }],
  }));

  const handleGetStarted = () => {
    router.push('/(auth)/sign-up');
  };

  const handleSignIn = () => {
    router.push('/(auth)/sign-in');
  };

  // Responsive hero height: decreased by 20%
  const heroHeight = Math.min(Math.max(screenHeight * 0.35, 200), 304);

  return (
    <View style={styles.container}>
      <StatusBar style="dark" />

      {/* Shared Ethereal Pastel Background */}
      <PastelBackground />

      <View style={[styles.contentWrapper, { paddingTop: insets.top }]}>
        {/* Top Header with subtle brand mark */}
        <View style={styles.topHeader}>
          <EparlyLogo width={105} height={32} theme="light" />
        </View>

        {/* Centerpiece Hero Illustration */}
        <View style={[styles.heroSection, { height: heroHeight }]}>
          <Animated.View style={[styles.imageWrapper, animatedHeroStyle]}>
            <Image
              source={require('@/components/welcome/money_img.svg')}
              style={styles.illustration}
              contentFit="contain"
              priority="high"
            />
          </Animated.View>
        </View>

        {/* Typography Area */}
        <View style={styles.typographySection}>
          <Animated.View style={animatedHeadlineStyle}>
            <Text style={styles.headline}>Your money, made simple.</Text>
          </Animated.View>

          <Animated.View style={animatedDescStyle}>
            <Text style={styles.description}>
              Track your expenses, manage subscriptions, and discover smarter ways to save.
            </Text>
          </Animated.View>
        </View>

        {/* Bottom Actions Area */}
        <Animated.View
          style={[
            styles.bottomSection,
            animatedActionsStyle,
            { paddingBottom: Math.max(insets.bottom, 16) + 12 },
          ]}>
          <WelcomeActions
            onGetStarted={handleGetStarted}
            onSignIn={handleSignIn}
          />
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  contentWrapper: {
    flex: 1,
    justifyContent: 'space-between',
  },
  topHeader: {
    height: 48,
    paddingHorizontal: 24,
    justifyContent: 'center',
    alignItems: 'flex-start',
  },
  heroSection: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
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
    maxWidth: 312,
  },
  typographySection: {
    paddingHorizontal: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headline: {
    fontSize: 32,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    letterSpacing: -0.6,
    lineHeight: 40,
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif-medium',
      default: 'system-ui',
    }),
  },
  description: {
    fontSize: 15.5,
    fontWeight: '400',
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    letterSpacing: -0.1,
    lineHeight: 23,
    marginTop: 10,
    maxWidth: 320,
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif',
      default: 'system-ui',
    }),
  },
  bottomSection: {
    paddingHorizontal: 24,
    width: '100%',
    alignItems: 'center',
  },
});
