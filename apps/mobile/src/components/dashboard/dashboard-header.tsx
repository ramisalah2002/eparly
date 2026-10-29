import React, { useState } from 'react';
import { useRouter } from 'expo-router';
import {
  Alert,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Bell } from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { getTimeBasedGreeting } from '@/utils/formatters';

interface DashboardHeaderProps {
  userName?: string;
  userInitials?: string;
  onNotificationsPress?: () => void;
}

export function DashboardHeader({
  userName = 'Younes',
  userInitials = 'YE',
  onNotificationsPress,
}: DashboardHeaderProps) {
  const router = useRouter();
  const [hasUnreadNotification, setHasUnreadNotification] = useState<boolean>(true);

  // Time-based greeting in English
  const greetingPrefix = (() => {
    const frGreeting = getTimeBasedGreeting();
    if (frGreeting === 'Bonjour') return 'Good morning';
    if (frGreeting === 'Bon après-midi') return 'Good afternoon';
    return 'Good evening';
  })();

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
      {/* Left Section: Time-based Welcome Message */}
      <View style={styles.greetingContainer}>
        <Text style={styles.greetingTitle} numberOfLines={1}>
          {greetingPrefix}, {userName}
        </Text>
        <Text style={styles.greetingSubtitle} numberOfLines={1}>
          Your financial overview
        </Text>
      </View>

      {/* Right Section: Notification icon on the left of user avatar circle */}
      <View style={styles.rightActions}>
        <TouchableOpacity
          onPress={handleNotificationPress}
          activeOpacity={0.7}
          style={styles.iconButton}
          accessibilityLabel="Notifications"
          accessibilityRole="button">
          <View style={styles.bellWrapper}>
            <Bell size={19} color={BRAND_COLORS.darkCharcoal} strokeWidth={2.2} />
            {hasUnreadNotification && <View style={styles.notificationDot} />}
          </View>
        </TouchableOpacity>

        <TouchableOpacity
          onPress={handleProfilePress}
          activeOpacity={0.8}
          style={styles.avatarButton}
          accessibilityLabel="Open profile"
          accessibilityRole="button">
          <View style={styles.avatarCircle}>
            <Text style={styles.avatarInitials}>{userInitials}</Text>
          </View>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
    maxWidth: MaxContentWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.three,
    paddingVertical: 10,
    minHeight: 52,
  },
  greetingContainer: {
    flex: 1,
    paddingRight: Spacing.three,
  },
  greetingTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.3,
  },
  greetingSubtitle: {
    fontSize: 12,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
  },
  rightActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  iconButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.90)',
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
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
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: BRAND_COLORS.deepLavender,
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.pureWhite,
  },
  avatarButton: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.mutedLavender,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 3,
    elevation: 2,
  },
  avatarInitials: {
    fontSize: 14,
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: 0.5,
  },
});
