import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BRAND_COLORS } from '@/constants/colors';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';

export default function SavingsScreen() {
  const insets = useSafeAreaInsets();

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
        <View style={styles.header}>
          <Text style={styles.title}>Épargne & Optimisation</Text>
          <Text style={styles.subtitle}>Plans d’action personnalisés</Text>
        </View>

        <View style={styles.card}>
          <View style={styles.iconCircle}>
            <Text style={styles.iconEmoji}>🌱</Text>
          </View>
          <Text style={styles.cardTitle}>Opportunités d’économies</Text>
          <Text style={styles.cardDescription}>
            Identifiez les marges d’économies réalistes sur vos contrats récurrents sans dégrader votre niveau de service.
          </Text>

          <View style={styles.featuresList}>
            <View style={styles.featureItem}>
              <Text style={styles.featureDot}>•</Text>
              <Text style={styles.featureText}>
                Comparaisons objectives basées sur des offres du marché vérifiées
              </Text>
            </View>
            <View style={styles.featureItem}>
              <Text style={styles.featureDot}>•</Text>
              <Text style={styles.featureText}>
                Prise en compte des frais de résiliation et périodes d’engagement
              </Text>
            </View>
            <View style={styles.featureItem}>
              <Text style={styles.featureDot}>•</Text>
              <Text style={styles.featureText}>
                Plan d’action pas à pas pour changer de contrat en toute sérénité
              </Text>
            </View>
          </View>
        </View>
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
    marginBottom: Spacing.four,
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
  card: {
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
  },
  iconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: BRAND_COLORS.softMintBackground,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: Spacing.three,
  },
  iconEmoji: {
    fontSize: 28,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: Spacing.two,
    textAlign: 'center',
  },
  cardDescription: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: Spacing.four,
    maxWidth: 300,
  },
  featuresList: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    padding: Spacing.three,
    borderRadius: 16,
    width: '100%',
    gap: Spacing.two,
  },
  featureItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
  },
  featureDot: {
    fontSize: 14,
    color: '#34A853',
    fontWeight: '700',
  },
  featureText: {
    fontSize: 12,
    color: BRAND_COLORS.darkCharcoal,
    flex: 1,
    lineHeight: 18,
  },
});
