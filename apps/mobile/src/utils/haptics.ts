import * as Haptics from 'expo-haptics';
import { Platform } from 'react-native';

/**
 * Safe, guarded haptics utility for Eparly Mobile.
 * Gracefully ignores unsupported platforms (Web, Node.js test runners)
 * and catches runtime capability exceptions.
 */
export const triggerHaptic = {
  light: async () => {
    if (Platform.OS === 'web') return;
    try {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    } catch {
      // Graceful fallback on devices without haptic engine
    }
  },
  selection: async () => {
    if (Platform.OS === 'web') return;
    try {
      await Haptics.selectionAsync();
    } catch {
      // Graceful fallback
    }
  },
  success: async () => {
    if (Platform.OS === 'web') return;
    try {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch {
      // Graceful fallback
    }
  },
};

