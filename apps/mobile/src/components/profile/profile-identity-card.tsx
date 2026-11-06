import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import {
  AlertCircle,
  CheckCircle2,
  ChevronRight,
  Shield,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { StoredUserProfile } from '@/services/session-storage';
import { getAvatarInitials } from '@/utils/formatters';
import { triggerHaptic } from '@/utils/haptics';

export interface ProfileIdentityCardProps {
  user: StoredUserProfile | null;
  onPress: () => void;
}

/**
 * Editorial Personal Monograph Identity Card (Concept A+).
 * Replaces the previous oversized 72px card with a compact 52px monogram,
 * accurate email verification badge, and membership date derived from createdAt.
 */
export function ProfileIdentityCard({ user, onPress }: ProfileIdentityCardProps) {
  const isReducedMotion = useReducedMotion();
  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.cardPress;

  const initials = getAvatarInitials(user?.fullName, user?.email);
  const isVerified = Boolean(user?.emailVerifiedAt);

  // Derive readable membership date
  const memberSince = React.useMemo(() => {
    if (!user?.createdAt) return 'octobre 2026';
    try {
      const date = new Date(user.createdAt);
      if (isNaN(date.getTime())) return 'octobre 2026';
      return new Intl.DateTimeFormat('fr-FR', {
        month: 'long',
        year: 'numeric',
      }).format(date);
    } catch {
      return 'octobre 2026';
    }
  }, [user?.createdAt]);

  const handlePress = () => {
    void triggerHaptic.light();
    onPress();
  };

  return (
    <AnimatedPressable
      onPress={handlePress}
      scaleTarget={scalePress}
      activeOpacity={0.92}
      style={styles.cardContainer}
      accessibilityRole="button"
      accessibilityLabel={`Profil de ${user?.fullName || 'Utilisateur'}, ${user?.email || ''}, ${isVerified ? 'Email vérifié' : 'Email non vérifié'}`}>
      <View style={styles.cardHeader}>
        {/* Monogram Capsule */}
        <View style={styles.monogramCircle}>
          <Text style={styles.monogramText}>{initials}</Text>
        </View>

        {/* User Identity Details */}
        <View style={styles.identityDetails}>
          <Text style={styles.fullName} numberOfLines={1}>
            {user?.fullName || 'Utilisateur Eparly'}
          </Text>

          <Text style={styles.email} numberOfLines={1}>
            {user?.email || 'compte@eparly.com'}
          </Text>

          {/* Badges Row */}
          <View style={styles.badgeRow}>
            {isVerified ? (
              <View style={styles.verifiedPill}>
                <CheckCircle2 size={11} color="#15803D" strokeWidth={2.4} />
                <Text style={styles.verifiedText}>Email vérifié</Text>
              </View>
            ) : (
              <View style={styles.unverifiedPill}>
                <AlertCircle size={11} color="#B45309" strokeWidth={2.4} />
                <Text style={styles.unverifiedText}>Non vérifié</Text>
              </View>
            )}

            <View style={styles.tenurePill}>
              <Shield size={10} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
              <Text style={styles.tenureText}>Membre depuis {memberSince}</Text>
            </View>
          </View>
        </View>

        {/* Navigation Indicator */}
        <View style={styles.chevronWrapper}>
          <ChevronRight size={18} color="#A89BBE" strokeWidth={2.2} />
        </View>
      </View>
    </AnimatedPressable>
  );
}

const styles = StyleSheet.create({
  cardContainer: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 22,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.04,
    shadowRadius: 8,
    elevation: 2,
    marginBottom: 20,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  monogramCircle: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.mutedLavender,
    justifyContent: 'center',
    alignItems: 'center',
  },
  monogramText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  identityDetails: {
    flex: 1,
    gap: 2,
  },
  fullName: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  email: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    marginBottom: 4,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
  },
  verifiedPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: BRAND_COLORS.softMintBackground,
    paddingHorizontal: 7,
    paddingVertical: 2.5,
    borderRadius: 6,
  },
  verifiedText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 10.5,
    color: '#15803D',
    fontWeight: '600',
  },
  unverifiedPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: '#FEF3C7',
    paddingHorizontal: 7,
    paddingVertical: 2.5,
    borderRadius: 6,
  },
  unverifiedText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 10.5,
    color: '#B45309',
    fontWeight: '600',
  },
  tenurePill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 7,
    paddingVertical: 2.5,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.18)',
  },
  tenureText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 10.5,
    color: BRAND_COLORS.secondaryText,
  },
  chevronWrapper: {
    paddingLeft: 4,
  },
});

