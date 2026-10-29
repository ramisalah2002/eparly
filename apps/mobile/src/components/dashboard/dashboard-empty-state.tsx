import React from 'react';
import { Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { BarChart3, ShieldCheck, Wallet, Zap } from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';

interface DashboardEmptyStateProps {
  onImportPress?: () => void;
}

export function DashboardEmptyState({ onImportPress }: DashboardEmptyStateProps) {
  const handleImportPress = () => {
    if (onImportPress) {
      onImportPress();
    } else {
      Alert.alert(
        'Import de relevés CSV',
        'Pour importer vos relevés bancaires, exportez un fichier CSV depuis votre banque en ligne (format standard) puis importez-le dans Eparly.',
        [{ text: 'Compris' }]
      );
    }
  };

  return (
    <View style={styles.container}>
      {/* Visual Emblem */}
      <View style={styles.emblemContainer}>
        <View style={styles.outerCircle}>
          <View style={styles.innerCircle}>
            <Wallet size={26} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
          </View>
        </View>
      </View>

      {/* Welcome text */}
      <Text style={styles.title}>Bienvenue sur votre espace Eparly</Text>
      <Text style={styles.description}>
        Votre tableau de bord financier s’animera dès l’import de vos premiers relevés de compte.
        Aucune donnée bancaire n’est inventée : vos graphiques et synthèses refléteront fidèlement vos opérations réelles.
      </Text>

      {/* Value pillars */}
      <View style={styles.pillarsContainer}>
        <View style={styles.pillarItem}>
          <View style={styles.pillarIconBadge}>
            <ShieldCheck size={16} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
          </View>
          <View style={styles.pillarTextContainer}>
            <Text style={styles.pillarTitle}>100% Confidentiel</Text>
            <Text style={styles.pillarText}>
              Vos données restent strictement chiffrées et cloisonnées sur votre compte.
            </Text>
          </View>
        </View>

        <View style={styles.pillarItem}>
          <View style={styles.pillarIconBadge}>
            <Zap size={16} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
          </View>
          <View style={styles.pillarTextContainer}>
            <Text style={styles.pillarTitle}>Catégorisation automatique</Text>
            <Text style={styles.pillarText}>
              Classement automatique de vos postes de dépenses et identification de vos abonnements.
            </Text>
          </View>
        </View>

        <View style={styles.pillarItem}>
          <View style={styles.pillarIconBadge}>
            <BarChart3 size={16} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
          </View>
          <View style={styles.pillarTextContainer}>
            <Text style={styles.pillarTitle}>Rigueur financière</Text>
            <Text style={styles.pillarText}>
              Zéro solde simulé, calculs précis en centimes selon les standards bancaires.
            </Text>
          </View>
        </View>
      </View>

      {/* CTA Button */}
      <TouchableOpacity
        onPress={handleImportPress}
        activeOpacity={0.85}
        style={styles.primaryCta}
        accessibilityLabel="Importer des transactions"
        accessibilityRole="button">
        <Text style={styles.primaryCtaText}>Importer des transactions</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 24,
    padding: Spacing.four,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.3)',
    shadowColor: '#242535',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 12,
    elevation: 2,
    alignItems: 'center',
    marginBottom: Spacing.four,
  },
  emblemContainer: {
    marginVertical: Spacing.two,
  },
  outerCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
  },
  innerCircle: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: BRAND_COLORS.pureWhite,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 1,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    marginTop: Spacing.two,
    marginBottom: Spacing.two,
  },
  description: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 19,
    marginBottom: Spacing.four,
    maxWidth: 320,
  },
  pillarsContainer: {
    alignSelf: 'stretch',
    gap: Spacing.three,
    marginBottom: Spacing.four,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    padding: Spacing.three,
    borderRadius: 16,
  },
  pillarItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two + 2,
  },
  pillarIconBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: BRAND_COLORS.pureWhite,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 1,
  },
  pillarTextContainer: {
    flex: 1,
  },
  pillarTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 2,
  },
  pillarText: {
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    lineHeight: 16,
  },
  primaryCta: {
    backgroundColor: BRAND_COLORS.mutedLavender,
    paddingVertical: 14,
    paddingHorizontal: Spacing.four,
    borderRadius: 16,
    alignSelf: 'stretch',
    alignItems: 'center',
    shadowColor: BRAND_COLORS.mutedLavender,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.25,
    shadowRadius: 6,
    elevation: 2,
  },
  primaryCtaText: {
    color: BRAND_COLORS.pureWhite,
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
});
