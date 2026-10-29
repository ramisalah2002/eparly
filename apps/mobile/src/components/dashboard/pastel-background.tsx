import React from 'react';
import { StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { BRAND_COLORS } from '@/constants/colors';

interface PastelBackgroundProps {
  children?: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  pointerEvents?: 'box-none' | 'none' | 'box-only' | 'auto';
}

/**
 * Quicken-inspired soft pastel gradient background.
 * Blends soft pastel mint (#E4F6F0), lavender mist (#F0ECF5),
 * warm off-white (#FAF9F6), and warm beige (#F1E5D7) into an
 * airy, luminous atmosphere that elevates white floating cards.
 */
export function PastelBackground({ children, style, pointerEvents }: PastelBackgroundProps) {
  return (
    <View style={[styles.container, style]} pointerEvents={pointerEvents}>
      {/* Primary diagonal pastel gradient */}
      <LinearGradient
        colors={[
          '#E6F7F1', // Soft pastel mint top-left
          '#EEF5F2', // Subtle aqua-mint transition
          BRAND_COLORS.warmOffWhite, // Warm white center
          '#F4EEF7', // Lavender mist transition
          BRAND_COLORS.veryLightBeige, // Warm beige bottom-right
        ]}
        locations={[0, 0.25, 0.55, 0.8, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={StyleSheet.absoluteFill}
      />

      {/* Secondary soft lavender wash from top-right */}
      <LinearGradient
        colors={[
          'rgba(240, 236, 245, 0.85)', // Lavender mist top-right
          'rgba(240, 236, 245, 0.35)',
          'rgba(250, 249, 246, 0)', // Soft fade towards bottom-left
        ]}
        locations={[0, 0.35, 0.75]}
        start={{ x: 1, y: 0 }}
        end={{ x: 0.1, y: 0.8 }}
        style={StyleSheet.absoluteFill}
      />

      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
});
