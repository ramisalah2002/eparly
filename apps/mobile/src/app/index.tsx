import { useRouter } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import React, { useEffect, useRef } from 'react';
import { AccessibilityInfo, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';

import { EparlyLogo } from '@/components/eparly-logo';
import { ThemedText } from '@/components/themed-text';
import { getHasCompletedOnboarding } from '@/services/onboarding-storage';

const SPLASH_BG_COLOR = '#FFFFFF';
const BRAND_GREEN = '#16A085';
const TEXT_SECONDARY = '#475569';
const SUBTEXT_MUTED = '#94A3B8';

export default function SplashScreenComponent() {
  const router = useRouter();
  const hasNavigated = useRef(false);
  const isMounted = useRef(true);
  const systemReducedMotion = useReducedMotion();
  const hasCompletedOnboardingRef = useRef(false);

  // Animation values
  const logoOpacity = useSharedValue(0);
  const logoScale = useSharedValue(0.92);

  const taglineOpacity = useSharedValue(0);
  const taglineTranslateY = useSharedValue(8);

  const progress = useSharedValue(0);
  const containerOpacity = useSharedValue(1);

  useEffect(() => {
    isMounted.current = true;

    // Asynchronously pre-fetch onboarding completion state during splash animation
    getHasCompletedOnboarding()
      .then((completed) => {
        hasCompletedOnboardingRef.current = completed;
      })
      .catch(() => {
        hasCompletedOnboardingRef.current = false;
      });

    let navTimer: ReturnType<typeof setTimeout> | null = null;
    let fadeTimer: ReturnType<typeof setTimeout> | null = null;

    const navigateToNextScreen = () => {
      if (hasNavigated.current || !isMounted.current) return;
      hasNavigated.current = true;
      if (hasCompletedOnboardingRef.current) {
        router.replace('/(tabs)');
      } else {
        router.replace('/onboarding');
      }
    };

    const startAnimation = async () => {
      // Check accessibility preferences
      let reducedMotion = systemReducedMotion;
      if (!reducedMotion) {
        try {
          reducedMotion = await AccessibilityInfo.isReduceMotionEnabled();
        } catch {
          reducedMotion = false;
        }
      }

      if (reducedMotion) {
        logoOpacity.value = 1;
        logoScale.value = 1;
        taglineOpacity.value = 1;
        taglineTranslateY.value = 0;
        progress.value = 100;

        navTimer = setTimeout(() => {
          navigateToNextScreen();
        }, 1800);
        return;
      }

      // 1. Logo entrance: smooth fade & gentle scale
      logoOpacity.value = withTiming(1, {
        duration: 700,
        easing: Easing.out(Easing.cubic),
      });
      logoScale.value = withTiming(1, {
        duration: 750,
        easing: Easing.out(Easing.back(1.05)),
      });

      // 2. Tagline entrance
      taglineOpacity.value = withDelay(
        450,
        withTiming(1, {
          duration: 600,
          easing: Easing.out(Easing.cubic),
        })
      );
      taglineTranslateY.value = withDelay(
        450,
        withTiming(0, {
          duration: 600,
          easing: Easing.out(Easing.cubic),
        })
      );

      // 3. Understated progress bar fill
      progress.value = withDelay(
        600,
        withTiming(100, {
          duration: 1500,
          easing: Easing.inOut(Easing.quad),
        })
      );

      // 4. Smooth fade out before navigation transition
      fadeTimer = setTimeout(() => {
        if (!isMounted.current) return;
        containerOpacity.value = withTiming(0, {
          duration: 250,
          easing: Easing.in(Easing.ease),
        });
      }, 2350);

      // 5. Navigation
      navTimer = setTimeout(() => {
        navigateToNextScreen();
      }, 2600);
    };

    startAnimation();

    return () => {
      isMounted.current = false;
      if (navTimer) clearTimeout(navTimer);
      if (fadeTimer) clearTimeout(fadeTimer);
    };
  }, [router, systemReducedMotion, containerOpacity, logoOpacity, logoScale, progress, taglineOpacity, taglineTranslateY]);

  // Handle seamless native splash dismissal
  const handleLayout = () => {
    SplashScreen.hideAsync().catch(() => {
      // Ignore if already hidden
    });
  };

  // Animated styles
  const logoAnimatedStyle = useAnimatedStyle(() => ({
    opacity: logoOpacity.value,
    transform: [{ scale: logoScale.value }],
  }));

  const taglineAnimatedStyle = useAnimatedStyle(() => ({
    opacity: taglineOpacity.value,
    transform: [{ translateY: taglineTranslateY.value }],
  }));

  const progressAnimatedStyle = useAnimatedStyle(() => ({
    width: `${progress.value}%`,
  }));

  const containerAnimatedStyle = useAnimatedStyle(() => ({
    opacity: containerOpacity.value,
  }));

  return (
    <View style={styles.backgroundContainer} onLayout={handleLayout}>
      <StatusBar style="dark" />

      <SafeAreaView style={styles.safeArea}>
        <Animated.View style={[styles.mainContent, containerAnimatedStyle]}>
          {/* Central Hero Section */}
          <View style={styles.heroSection}>
            <Animated.View style={[styles.logoWrapper, logoAnimatedStyle]}>
              <EparlyLogo width={270} height={152} theme="light" />
            </Animated.View>

            <Animated.View style={[styles.taglineWrapper, taglineAnimatedStyle]}>
              <ThemedText style={styles.tagline}>
                Spend smarter. Save more.
              </ThemedText>
            </Animated.View>
          </View>

          {/* Bottom Understated Progress & Indicator */}
          <View style={styles.bottomSection}>
            <View style={styles.progressTrack}>
              <Animated.View style={[styles.progressBar, progressAnimatedStyle]} />
            </View>
            <ThemedText style={styles.subtext}>Financial Clarity & Control</ThemedText>
          </View>
        </Animated.View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  backgroundContainer: {
    flex: 1,
    backgroundColor: SPLASH_BG_COLOR,
  },
  safeArea: {
    flex: 1,
  },
  mainContent: {
    flex: 1,
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingVertical: 32,
  },
  heroSection: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    width: '100%',
  },
  logoWrapper: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  taglineWrapper: {
    alignItems: 'center',
    paddingHorizontal: 20,
    marginTop: -8,
  },
  tagline: {
    fontSize: 16,
    fontWeight: '500',
    color: TEXT_SECONDARY,
    letterSpacing: 0.3,
    textAlign: 'center',
  },
  bottomSection: {
    alignItems: 'center',
    gap: 14,
    width: '100%',
    paddingBottom: 16,
  },
  progressTrack: {
    width: 130,
    height: 3,
    backgroundColor: '#E2E8F0',
    borderRadius: 1.5,
    overflow: 'hidden',
  },
  progressBar: {
    height: '100%',
    backgroundColor: BRAND_GREEN,
    borderRadius: 1.5,
  },
  subtext: {
    fontSize: 11,
    fontWeight: '500',
    color: SUBTEXT_MUTED,
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
});
