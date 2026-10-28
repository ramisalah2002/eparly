import { LinearGradient } from 'expo-linear-gradient';
import React from 'react';
import { StyleSheet, View } from 'react-native';

import { ONBOARDING_COLORS } from '@/constants/onboarding';

export function PastelBackground() {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {/* 1. Base gradient: warm off-white to delicate soft mint */}
      <LinearGradient
        colors={[
          ONBOARDING_COLORS.warmOffWhite,
          '#F4FAF7',
          ONBOARDING_COLORS.softMintBackground,
        ]}
        locations={[0, 0.55, 1]}
        start={{ x: 0.5, y: 0 }}
        end={{ x: 0.5, y: 1 }}
        style={StyleSheet.absoluteFill}
      />

      {/* 2. Soft Lavender aura in the upper-right region */}
      <View style={styles.lavenderGlow} />

      {/* 3. Soft Warm Sand Beige aura in the opposite (lower-left) region */}
      <View style={styles.beigeGlow} />

      {/* 4. Diffusion overlay gradient to ensure smooth, imperceptible boundaries */}
      <LinearGradient
        colors={[
          'rgba(250, 249, 246, 0.65)',
          'rgba(244, 250, 247, 0.35)',
          'rgba(250, 249, 246, 0.70)',
        ]}
        locations={[0, 0.5, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  lavenderGlow: {
    position: 'absolute',
    top: -60,
    right: -70,
    width: 320,
    height: 320,
    borderRadius: 160,
    backgroundColor: 'rgba(168, 155, 190, 0.20)',
  },
  beigeGlow: {
    position: 'absolute',
    bottom: 80,
    left: -80,
    width: 300,
    height: 300,
    borderRadius: 150,
    backgroundColor: 'rgba(216, 195, 168, 0.22)',
  },
});
