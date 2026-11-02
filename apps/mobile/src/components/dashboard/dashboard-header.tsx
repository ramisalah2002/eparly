import React, { useState } from 'react';
import { useRouter } from 'expo-router';
import {
  Alert,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { Bell } from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { FONT_FAMILY } from '@/constants/typography';
import { MaxContentWidth } from '@/constants/theme';
import {
  formatMonographHeaderDate,
  formatNaturalCapitalization,
  getTimeBasedGreeting,
  sanitizeInitials,
} from '@/utils/formatters';

interface DashboardHeaderProps {
  userName?: string;
  userInitials?: string;
  hasUnreadNotification?: boolean;
  onNotificationsPress?: () => void;
}

/**
 * Eparly Monograph Dashboard Header (Concept 1).
 * Features an editorial lockup with natural-cased contextual date,
 * high-contrast personalized greeting, and a calibrated 36px utility cluster
 * aligned flush with the content grid.
 */
export function DashboardHeader({
  userName = 'Alex',
  userInitials = 'AL',
  hasUnreadNotification: propHasUnread = false,
  onNotificationsPress,
}: DashboardHeaderProps) {
  const router = useRouter();
  const [hasUnreadNotification, setHasUnreadNotification] = useState<boolean>(propHasUnread);

  // Time-based greeting prefix in English ("Good morning" / "Good afternoon" / "Good evening")
  const greetingPrefix = (() => {
    const frGreeting = getTimeBasedGreeting();
    if (frGreeting === 'Bonjour') return 'Good morning';
    if (frGreeting === 'Bon après-midi') return 'Good afternoon';
    return 'Good evening';
  })();

  const formattedName = formatNaturalCapitalization(userName);
  const greetingText = formattedName ? `${greetingPrefix}, ${formattedName}.` : `${greetingPrefix}.`;
  const contextDate = formatMonographHeaderDate();
  const cleanInitials = sanitizeInitials(userInitials);

  const handleProfilePress = () => {
    router.push('/(tabs)/profile');
  };

  const handleNotificationPress = () => {
    if (onNotificationsPress) {
      onNotificationsPress();
      return;
    }
    setHasUnreadNotification(false);
    Alert.alert(
      'Notifications',
      'No new alerts. Your accounts are synchronized and up to date.',
      [{ text: 'Dismiss' }]
    );
  };

  return (
    <View style={styles.container}>
      {/* EDITORIAL MONOGRAPH ROW: Date + Greeting (Left) & Control Cluster (Right) */}
      <View style={styles.primaryRow}>
        <View style={styles.leftColumn}>
          <Text style={styles.contextDate} numberOfLines={1}>
            {contextDate}
          </Text>
          <Text
            style={styles.greetingTitle}
            numberOfLines={1}
            ellipsizeMode="tail"
            adjustsFontSizeToFit
            minimumFontScale={0.85}>
            {greetingText}
          </Text>
        </View>

        {/* Right Section: Refined 36px Utility Cluster */}
        <View style={styles.rightActions}>
          <Pressable
            onPress={handleNotificationPress}
            style={({ pressed }) => [
              styles.iconButton,
              pressed && styles.buttonPressed,
            ]}
            hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
            accessibilityLabel="Notifications"
            accessibilityRole="button">
            <View style={styles.bellWrapper}>
              <Bell size={18} color={BRAND_COLORS.darkCharcoal} strokeWidth={2} />
              {hasUnreadNotification && <View style={styles.notificationDot} />}
            </View>
          </Pressable>

          <Pressable
            onPress={handleProfilePress}
            style={({ pressed }) => [
              styles.avatarButton,
              pressed && styles.buttonPressed,
            ]}
            hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
            accessibilityLabel="Open user profile"
            accessibilityRole="button">
            <View style={styles.avatarSurface}>
              <Text style={styles.avatarInitials}>{cleanInitials}</Text>
            </View>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingVertical: 2,
  },
  primaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    width: '100%',
  },
  leftColumn: {
    flex: 1,
    paddingRight: 12,
    justifyContent: 'center',
  },
  contextDate: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
    marginBottom: 2,
  },
  greetingTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 22,
    lineHeight: 27,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.4,
  },
  rightActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  iconButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
    borderWidth: 1,
    borderColor: 'rgba(36, 37, 53, 0.08)',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.03,
    shadowRadius: 3,
    elevation: 1,
  },
  bellWrapper: {
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
  },
  notificationDot: {
    position: 'absolute',
    top: -1,
    right: -1,
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: BRAND_COLORS.deepLavender,
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.pureWhite,
  },
  avatarButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarSurface: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#EDE7F6',
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.pureWhite,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  avatarInitials: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13,
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: 0.2,
  },
  buttonPressed: {
    transform: [{ scale: 0.94 }],
    opacity: 0.88,
  },
});
