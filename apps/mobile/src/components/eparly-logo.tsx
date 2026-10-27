import { Image } from 'expo-image';
import React from 'react';
import { StyleProp, StyleSheet, View, ViewStyle } from 'react-native';

interface EparlyLogoProps {
  width?: number;
  height?: number;
  theme?: 'dark' | 'light' | 'original';
  style?: StyleProp<ViewStyle>;
}

export function EparlyLogo({
  width = 240,
  height = 135,
  theme = 'light',
  style,
}: EparlyLogoProps) {
  const source =
    theme === 'dark'
      ? require('@/assets/images/eparly_logo_white.png')
      : require('@/assets/images/eparly_logo.png');

  return (
    <View style={[styles.container, style]}>
      <Image
        source={source}
        style={{ width, height }}
        contentFit="contain"
        priority="high"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
  },
});
