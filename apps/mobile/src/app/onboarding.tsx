import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useRef, useState } from 'react';
import type { FlatListProps } from 'react-native';
import {
  FlatList,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import Animated, {
  useAnimatedScrollHandler,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { OnboardingButton } from '@/components/onboarding/onboarding-button';
import { OnboardingPagination } from '@/components/onboarding/onboarding-pagination';
import { OnboardingSlide } from '@/components/onboarding/onboarding-slide';
import { PastelBackground } from '@/components/onboarding/pastel-background';
import {
  ONBOARDING_COLORS,
  ONBOARDING_SLIDES,
  OnboardingSlideItem,
} from '@/constants/onboarding';
import { setHasCompletedOnboarding } from '@/services/onboarding-storage';

const AnimatedFlatList = Animated.createAnimatedComponent(
  FlatList as new () => FlatList<OnboardingSlideItem>
);

const VIEWABILITY_CONFIG = {
  itemVisiblePercentThreshold: 50,
};

export default function OnboardingScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();
  const reducedMotion = useReducedMotion();

  const [currentIndex, setCurrentIndex] = useState(0);
  const flatListRef = useRef<FlatList<OnboardingSlideItem>>(null);
  const isNavigating = useRef(false);

  // Animated horizontal scroll tracking
  const scrollX = useSharedValue(0);

  const scrollHandler = useAnimatedScrollHandler({
    onScroll: (event) => {
      scrollX.value = event.contentOffset.x;
    },
  });

  const handleViewableItemsChanged = useCallback<
    NonNullable<FlatListProps<OnboardingSlideItem>['onViewableItemsChanged']>
  >(({ viewableItems }) => {
    if (
      viewableItems.length > 0 &&
      viewableItems[0].index !== null &&
      viewableItems[0].index !== undefined
    ) {
      setCurrentIndex(viewableItems[0].index);
    }
  }, []);

  // Complete onboarding flow and persist state
  const handleFinishOnboarding = useCallback(async () => {
    if (isNavigating.current) return;
    isNavigating.current = true;
    try {
      await setHasCompletedOnboarding(true);
    } catch {
      // Ignore storage errors and proceed with navigation
    }
    router.replace('/(tabs)');
  }, [router]);

  // Handle CTA button press
  const handleButtonPress = useCallback(() => {
    if (currentIndex < ONBOARDING_SLIDES.length - 1) {
      flatListRef.current?.scrollToIndex({
        index: currentIndex + 1,
        animated: !reducedMotion,
      });
    } else {
      handleFinishOnboarding();
    }
  }, [currentIndex, handleFinishOnboarding, reducedMotion]);

  // Handle Skip press
  const handleSkipPress = useCallback(() => {
    handleFinishOnboarding();
  }, [handleFinishOnboarding]);

  const isLastSlide = currentIndex === ONBOARDING_SLIDES.length - 1;
  const currentButtonLabel = ONBOARDING_SLIDES[currentIndex]?.buttonLabel ?? 'Continue';

  // Usable height for the slides
  const topBarHeight = 36;
  const bottomBarHeight = 96 + insets.bottom;
  const slideUsableHeight = screenHeight - insets.top - topBarHeight - bottomBarHeight;

  return (
    <View style={styles.container}>
      <StatusBar style="dark" />

      {/* Layered ethereal pastel background */}
      <PastelBackground />

      <View style={[styles.safeContainer, { paddingTop: insets.top }]}>
        {/* Top Header with Minimalist Skip Button */}
        <View style={styles.topBar}>
          <Pressable
            onPress={handleSkipPress}
            hitSlop={{ top: 12, bottom: 12, left: 16, right: 16 }}
            style={[
              styles.skipButton,
              isLastSlide && styles.skipButtonHidden,
            ]}
            pointerEvents={isLastSlide ? 'none' : 'auto'}
            accessible={!isLastSlide}
            accessibilityRole="button"
            accessibilityLabel="Skip onboarding">
            <Text style={styles.skipText}>Skip</Text>
          </Pressable>
        </View>

        {/* Horizontal Paging Carousel */}
        <View style={styles.carouselContainer}>
          <AnimatedFlatList
            ref={flatListRef}
            data={ONBOARDING_SLIDES}
            keyExtractor={(item) => item.id}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            bounces={false}
            scrollEventThrottle={16}
            onScroll={scrollHandler}
            onViewableItemsChanged={handleViewableItemsChanged}
            viewabilityConfig={VIEWABILITY_CONFIG}
            getItemLayout={(_, index) => ({
              length: screenWidth,
              offset: screenWidth * index,
              index,
            })}
            renderItem={({ item, index }) => (
              <OnboardingSlide
                item={item}
                index={index}
                width={screenWidth}
                height={slideUsableHeight}
                scrollX={scrollX}
                isActive={currentIndex === index}
                reducedMotion={reducedMotion}
              />
            )}
          />
        </View>

        {/* Bottom Pinned Controls (Pagination + CTA Button) */}
        <View
          style={[
            styles.bottomControls,
            { paddingBottom: Math.max(insets.bottom, 12) + 8 },
          ]}>
          <View style={styles.paginationWrapper}>
            <OnboardingPagination
              total={ONBOARDING_SLIDES.length}
              scrollX={scrollX}
              screenWidth={screenWidth}
            />
          </View>

          <View style={styles.buttonWrapper}>
            <OnboardingButton
              label={currentButtonLabel}
              onPress={handleButtonPress}
            />
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: ONBOARDING_COLORS.warmOffWhite,
  },
  safeContainer: {
    flex: 1,
    justifyContent: 'space-between',
  },
  topBar: {
    height: 36,
    paddingHorizontal: 24,
    flexDirection: 'row',
    justifyContent: 'flex-end',
    alignItems: 'center',
    zIndex: 10,
  },
  skipButton: {
    paddingVertical: 6,
    paddingHorizontal: 8,
  },
  skipButtonHidden: {
    opacity: 0,
  },
  skipText: {
    fontSize: 15,
    fontWeight: '500',
    color: ONBOARDING_COLORS.secondaryText,
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif',
      default: 'system-ui',
    }),
  },
  carouselContainer: {
    flex: 1,
    justifyContent: 'center',
  },
  bottomControls: {
    paddingHorizontal: 24,
    alignItems: 'center',
    width: '100%',
  },
  paginationWrapper: {
    marginBottom: 14,
  },
  buttonWrapper: {
    width: '100%',
    alignItems: 'center',
  },
});
