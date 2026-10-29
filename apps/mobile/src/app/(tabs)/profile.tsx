import React from 'react';
import { useRouter } from 'expo-router';
import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BRAND_COLORS } from '@/constants/colors';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/auth-context';
import { resetOnboarding } from '@/services/onboarding-storage';
import { getAvatarInitials } from '@/utils/formatters';

export default function ProfileScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user, signOut } = useAuth();

  const initials = getAvatarInitials(user?.fullName, user?.email);

  const handleSignOut = async () => {
    Alert.alert('Déconnexion', 'Êtes-vous sûr de vouloir vous déconnecter ?', [
      { text: 'Annuler', style: 'cancel' },
      {
        text: 'Déconnexion',
        style: 'destructive',
        onPress: async () => {
          await signOut();
          router.replace('/(auth)/sign-in');
        },
      },
    ]);
  };

  const handleResetOnboarding = async () => {
    await resetOnboarding();
    Alert.alert(
      'Onboarding réinitialisé',
      'Le parcours de découverte a été réinitialisé. Rechargez l’application pour revoir l’onboarding.'
    );
  };

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={[
          styles.contentContainer,
          {
            paddingTop: Math.max(insets.top, Spacing.three) + Spacing.two,
            paddingBottom: insets.bottom + BottomTabInset + Spacing.four,
          },
        ]}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>Mon Profil</Text>
          <Text style={styles.subtitle}>Compte & Sécurité Eparly</Text>
        </View>

        {/* Profile Card */}
        <View style={styles.profileCard}>
          <View style={styles.avatarCircle}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>

          <Text style={styles.userName}>{user?.fullName || 'Utilisateur Eparly'}</Text>
          <Text style={styles.userEmail}>{user?.email}</Text>

          <View style={styles.badgeRow}>
            <View style={styles.statusBadge}>
              <View style={styles.activeDot} />
              <Text style={styles.statusText}>{user?.status || 'ACTIVE'}</Text>
            </View>
            <View style={styles.verifiedBadge}>
              <Text style={styles.verifiedText}>Email vérifié</Text>
            </View>
          </View>
        </View>

        {/* Settings Group */}
        <View style={styles.settingsGroup}>
          <Text style={styles.groupTitle}>Sécurité & Session</Text>

          <View style={styles.settingRow}>
            <Text style={styles.settingLabel}>Chiffrement des jetons</Text>
            <Text style={styles.settingValue}>SecureStore matériel</Text>
          </View>

          <View style={styles.divider} />

          <View style={styles.settingRow}>
            <Text style={styles.settingLabel}>Rotation de session</Text>
            <Text style={styles.settingValue}>Single-flight active</Text>
          </View>
        </View>

        {/* Development Tools */}
        <View style={styles.settingsGroup}>
          <Text style={styles.groupTitle}>Préférences</Text>

          <TouchableOpacity
            onPress={handleResetOnboarding}
            style={styles.settingActionRow}
            activeOpacity={0.7}>
            <Text style={styles.actionRowLabel}>Revoir le parcours d’onboarding</Text>
            <Text style={styles.actionRowChevron}>›</Text>
          </TouchableOpacity>
        </View>

        {/* Sign Out Button */}
        <TouchableOpacity
          onPress={handleSignOut}
          activeOpacity={0.8}
          style={styles.signOutButton}
          accessibilityLabel="Se déconnecter"
          accessibilityRole="button">
          <Text style={styles.signOutText}>Se déconnecter</Text>
        </TouchableOpacity>

        {/* App Version */}
        <Text style={styles.appVersionText}>Eparly v1.0.0 — Milestone 1 Dashboard</Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  contentContainer: {
    paddingHorizontal: Spacing.three,
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    width: '100%',
  },
  header: {
    marginBottom: Spacing.three,
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  subtitle: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  profileCard: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 24,
    padding: Spacing.four,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.28)',
    shadowColor: '#242535',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 2,
    alignItems: 'center',
    marginBottom: Spacing.three,
  },
  avatarCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    borderWidth: 2,
    borderColor: BRAND_COLORS.mutedLavender,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: Spacing.three,
  },
  avatarText: {
    fontSize: 24,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  userName: {
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 4,
  },
  userEmail: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    marginBottom: Spacing.three,
  },
  badgeRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  activeDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#34A853',
    marginRight: 6,
  },
  statusText: {
    fontSize: 11,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  verifiedBadge: {
    backgroundColor: BRAND_COLORS.softMintBackground,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  verifiedText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#2E7D32',
  },
  settingsGroup: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 20,
    padding: Spacing.three + 2,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.2)',
    marginBottom: Spacing.three,
  },
  groupTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: BRAND_COLORS.secondaryText,
    marginBottom: Spacing.two,
    textTransform: 'uppercase',
    letterSpacing: 0.3,
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
  },
  settingLabel: {
    fontSize: 14,
    color: BRAND_COLORS.darkCharcoal,
  },
  settingValue: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
  divider: {
    height: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
    marginVertical: 6,
  },
  settingActionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  actionRowLabel: {
    fontSize: 14,
    color: BRAND_COLORS.darkCharcoal,
    fontWeight: '500',
  },
  actionRowChevron: {
    fontSize: 18,
    color: BRAND_COLORS.secondaryText,
  },
  signOutButton: {
    backgroundColor: '#FFF0F0',
    borderWidth: 1,
    borderColor: 'rgba(225, 29, 72, 0.2)',
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    marginTop: Spacing.one,
    marginBottom: Spacing.three,
  },
  signOutText: {
    color: '#E11D48',
    fontSize: 15,
    fontWeight: '700',
  },
  appVersionText: {
    textAlign: 'center',
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    marginBottom: Spacing.four,
  },
});
