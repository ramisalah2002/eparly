import React, { useCallback, useEffect, useState } from 'react';
import {
  Linking,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import {
  Bell,
  CreditCard,
  FileText,
  Globe,
  HelpCircle,
  KeyRound,
  Languages,
  LogOut,
  RotateCcw,
  Shield,
  ShieldCheck,
  UserCheck,
  Wallet,
} from 'lucide-react-native';

import {
  AccountsViewerSheet,
  LegalSheet,
  PasswordResetSheet,
  PersonalInfoSheet,
  ProfileIdentityCard,
  SettingsRow,
  SettingsSection,
  SignOutConfirmSheet,
} from '@/components/profile';
import { BRAND_COLORS } from '@/constants/colors';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { FONT_FAMILY } from '@/constants/typography';
import { useAuth } from '@/context/auth-context';
import { AccountItem, fetchAccounts } from '@/services/dashboard-api';
import { resetOnboarding } from '@/services/onboarding-storage';
import { triggerHaptic } from '@/utils/haptics';

/**
 * Editorial Profile & Settings Screen (Concept A+).
 * The Personal Monograph with Account Ledger Insight.
 */
export default function ProfileScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user, signOut, checkSession } = useAuth();

  const [accounts, setAccounts] = useState<AccountItem[]>([]);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isSigningOut, setIsSigningOut] = useState<boolean>(false);

  // Bottom Sheets State Machine
  const [isSignOutSheetVisible, setIsSignOutSheetVisible] = useState<boolean>(false);
  const [isPersonalInfoVisible, setIsPersonalInfoVisible] = useState<boolean>(false);
  const [isAccountsSheetVisible, setIsAccountsSheetVisible] = useState<boolean>(false);
  const [isPasswordResetVisible, setIsPasswordResetVisible] = useState<boolean>(false);
  const [isLegalSheetVisible, setIsLegalSheetVisible] = useState<boolean>(false);

  const loadData = useCallback(async (refresh = false) => {
    if (refresh) setIsRefreshing(true);
    const [accRes] = await Promise.all([
      fetchAccounts(),
      checkSession(),
    ]);

    if (accRes.success && accRes.data) {
      setAccounts(accRes.data);
    }
    if (refresh) setIsRefreshing(false);
  }, [checkSession]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSignOutConfirm = async () => {
    try {
      setIsSigningOut(true);
      void triggerHaptic.light();
      await signOut();
      setIsSigningOut(false);
      setIsSignOutSheetVisible(false);
      router.replace('/(auth)/sign-in');
    } catch {
      setIsSigningOut(false);
    }
  };

  const handleContactSupport = () => {
    void triggerHaptic.light();
    void Linking.openURL('mailto:support@eparly.com?subject=Assistance%20Eparly');
  };

  const handleResetOnboarding = async () => {
    void triggerHaptic.light();
    await resetOnboarding();
  };

  const primaryCurrency = user?.settings?.currency || 'EUR';
  const countryCode = user?.settings?.country || 'FR';

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={[
          styles.contentContainer,
          {
            paddingTop: Math.max(insets.top, Spacing.three) + Spacing.two,
            paddingBottom: insets.bottom + BottomTabInset + Spacing.four,
          },
        ]}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={isRefreshing}
            onRefresh={() => loadData(true)}
            tintColor={BRAND_COLORS.deepLavender}
            colors={[BRAND_COLORS.deepLavender]}
            progressBackgroundColor={BRAND_COLORS.pureWhite}
          />
        }>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>Mon Profil</Text>
          <Text style={styles.subtitle}>Compte & Préférences Eparly</Text>
        </View>

        {/* 1. Personal Monograph Identity Card */}
        <ProfileIdentityCard
          user={user}
          onPress={() => setIsPersonalInfoVisible(true)}
        />

        {/* 2. Account & Security Section */}
        <SettingsSection title="Compte & Sécurité">
          <SettingsRow
            icon={UserCheck}
            label="Informations du compte"
            subtitle="Identité, email et statut de vérification"
            onPress={() => setIsPersonalInfoVisible(true)}
            accessibilityLabel="Informations du compte"
          />

          <SettingsRow
            icon={KeyRound}
            label="Sécurité & Mot de passe"
            subtitle="Réinitialisation sécurisée par code email"
            onPress={() => setIsPasswordResetVisible(true)}
            accessibilityLabel="Sécurité et mot de passe"
          />

          <SettingsRow
            icon={ShieldCheck}
            label="Session chiffrée"
            subtitle="Stockage matériel souverain"
            value="Active"
            showChevron={false}
            accessibilityLabel="Session chiffrée active"
          />
        </SettingsSection>

        {/* 3. Financial Preferences Section */}
        <SettingsSection title="Préférences financières">
          <SettingsRow
            icon={CreditCard}
            label="Comptes & Relevés bancaires"
            subtitle="Sources et déduplication des écritures"
            value={`${accounts.length} actif${accounts.length > 1 ? 's' : ''}`}
            onPress={() => setIsAccountsSheetVisible(true)}
            accessibilityLabel={`Comptes bancaires, ${accounts.length} configurés`}
          />

          <SettingsRow
            icon={Wallet}
            label="Devise principale"
            subtitle="Consolidation monétaire des relevés"
            value={`${primaryCurrency} (€)`}
            showChevron={false}
            accessibilityLabel={`Devise principale : ${primaryCurrency}`}
          />

          <SettingsRow
            icon={Globe}
            label="Région & Formats"
            subtitle="Norme de dates et séparateurs décimaux"
            value={`${countryCode} • France`}
            showChevron={false}
            accessibilityLabel={`Région : ${countryCode}`}
          />
        </SettingsSection>

        {/* 4. App & Privacy Preferences Section */}
        <SettingsSection title="Application & Confidentialité">
          <SettingsRow
            icon={Languages}
            label="Langue de l'interface"
            subtitle="Configuration active de l'application"
            value="Français"
            showChevron={false}
            accessibilityLabel="Langue de l'interface : Français"
          />

          <SettingsRow
            icon={Bell}
            label="Notifications système"
            subtitle="Autorisations d'alertes financières"
            value="Standard OS"
            showChevron={false}
            accessibilityLabel="Notifications système gérées par l'OS"
          />

          <SettingsRow
            icon={FileText}
            label="Protection des données (RGPD)"
            subtitle="Gouvernance, export et droit à l'oubli"
            onPress={() => setIsLegalSheetVisible(true)}
            accessibilityLabel="Protection des données et RGPD"
          />
        </SettingsSection>

        {/* 5. Support & Legal Section */}
        <SettingsSection title="Support & Assistance">
          <SettingsRow
            icon={HelpCircle}
            label="Contacter le support Eparly"
            subtitle="Assistance dédiée sous 24h ouvrées"
            onPress={handleContactSupport}
            accessibilityLabel="Contacter le support Eparly"
          />

          <SettingsRow
            icon={Shield}
            label="Conditions & Mentions légales"
            subtitle="Règles d'utilisation du service"
            onPress={() => setIsLegalSheetVisible(true)}
            accessibilityLabel="Conditions générales et mentions légales"
          />
        </SettingsSection>

        {/* 6. Development Tools (Discrete, only in development) */}
        {__DEV__ && (
          <SettingsSection title="Outils de développement" caption="Visible uniquement en mode développement local">
            <SettingsRow
              icon={RotateCcw}
              label="Réinitialiser l'onboarding"
              subtitle="Permet de revoir le parcours d'accueil"
              onPress={handleResetOnboarding}
              accessibilityLabel="Réinitialiser l'onboarding"
            />
          </SettingsSection>
        )}

        {/* 7. Sign Out Action (Restrained Editorial Row) */}
        <View style={styles.signOutWrapper}>
          <SettingsRow
            icon={LogOut}
            iconColor="#E11D48"
            iconBgColor="#FFF0F3"
            label="Se déconnecter"
            subtitle="Fermer la session sur cet appareil"
            destructive
            onPress={() => setIsSignOutSheetVisible(true)}
            accessibilityLabel="Se déconnecter"
          />
        </View>

        {/* App Version Footnote */}
        <Text style={styles.appVersionText}>
          Eparly v1.0.0 (Build 42) • Gestion financière personnelle
        </Text>
      </ScrollView>

      {/* Connected Bottom Sheets */}
      <SignOutConfirmSheet
        visible={isSignOutSheetVisible}
        onClose={() => setIsSignOutSheetVisible(false)}
        onConfirm={handleSignOutConfirm}
        isSigningOut={isSigningOut}
      />

      <PersonalInfoSheet
        visible={isPersonalInfoVisible}
        user={user}
        onClose={() => setIsPersonalInfoVisible(false)}
      />

      <AccountsViewerSheet
        visible={isAccountsSheetVisible}
        onClose={() => setIsAccountsSheetVisible(false)}
        onAccountUpdated={() => loadData(false)}
      />

      <PasswordResetSheet
        visible={isPasswordResetVisible}
        email={user?.email}
        onClose={() => setIsPasswordResetVisible(false)}
      />

      <LegalSheet
        visible={isLegalSheetVisible}
        onClose={() => setIsLegalSheetVisible(false)}
      />
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
    paddingHorizontal: 4,
  },
  title: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 26,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.5,
  },
  subtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
    letterSpacing: -0.1,
  },
  signOutWrapper: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(225, 29, 72, 0.18)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.03,
    shadowRadius: 6,
    elevation: 2,
    marginBottom: 20,
    overflow: 'hidden',
  },
  appVersionText: {
    textAlign: 'center',
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
    marginBottom: Spacing.three,
  },
});
