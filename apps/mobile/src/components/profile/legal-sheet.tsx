import React from 'react';
import {
  Linking,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import {
  FileText,
  Lock,
  Mail,
  Shield,
  Trash2,
  X,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import { triggerHaptic } from '@/utils/haptics';

export interface LegalSheetProps {
  visible: boolean;
  onClose: () => void;
}

/**
 * Editorial Privacy & Legal Governance Sheet (Concept A+).
 * Transparently details GDPR rights, data isolation, and formal contact procedures
 * for data export and account erasure without fake self-serve endpoints.
 */
export function LegalSheet({ visible, onClose }: LegalSheetProps) {
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();
  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  const handleContactPrivacy = () => {
    void triggerHaptic.light();
    void Linking.openURL('mailto:privacy@eparly.com?subject=Demande%20RGPD%20Eparly');
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <AnimatedPressable
          style={styles.backdrop}
          onPress={onClose}
          scaleTarget={1.0}
          accessibilityLabel="Fermer"
          accessibilityRole="button"
        />

        <View
          style={[
            styles.sheetContainer,
            { paddingBottom: Math.max(insets.bottom, 16) + 12 },
          ]}>
          {/* Drag Handle */}
          <View style={styles.handle} />

          {/* Header */}
          <View style={styles.headerRow}>
            <View>
              <Text style={styles.headerTitle}>Confidentialité & Données</Text>
              <Text style={styles.headerSubtitle}>Gouvernance & Protection RGPD</Text>
            </View>

            <AnimatedPressable
              onPress={onClose}
              scaleTarget={scalePress}
              style={styles.closeBtn}
              accessibilityRole="button"
              accessibilityLabel="Fermer">
              <X size={16} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
            </AnimatedPressable>
          </View>

          <ScrollView
            showsVerticalScrollIndicator={false}
            contentContainerStyle={styles.scrollContent}>
            {/* 1. Data Isolation & Security */}
            <View style={styles.infoCard}>
              <View style={styles.cardHeaderRow}>
                <Lock size={16} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                <Text style={styles.cardHeaderTitle}>Protection & Chiffrement</Text>
              </View>
              <Text style={styles.cardBodyText}>
                Vos écritures bancaires et jetons de session sont stockés dans des enclaves sécurisées.
                Eparly ne vend aucune donnée financière à des tiers ni à des courtiers publicitaires.
              </Text>
            </View>

            {/* 2. GDPR Rights & Portability */}
            <View style={styles.infoCard}>
              <View style={styles.cardHeaderRow}>
                <Shield size={16} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                <Text style={styles.cardHeaderTitle}>Vos droits RGPD (Art. 15 à 20)</Text>
              </View>
              <Text style={styles.cardBodyText}>
                Vous disposez d’un droit d’accès, de rectification, de portabilité et d’effacement complet de vos données financières.
              </Text>

              <View style={styles.gdprActionBlock}>
                <AnimatedPressable
                  onPress={handleContactPrivacy}
                  scaleTarget={scalePress}
                  style={styles.emailBtn}
                  accessibilityRole="button"
                  accessibilityLabel="Contacter le délégué à la protection des données">
                  <Mail size={13} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                  <Text style={styles.emailBtnText}>Contacter le DPO : privacy@eparly.com</Text>
                </AnimatedPressable>
              </View>
            </View>

            {/* 3. Account Deletion Procedure */}
            <View style={styles.infoCard}>
              <View style={styles.cardHeaderRow}>
                <Trash2 size={16} color="#E11D48" strokeWidth={2.2} />
                <Text style={[styles.cardHeaderTitle, { color: '#E11D48' }]}>
                  Suppression de compte
                </Text>
              </View>
              <Text style={styles.cardBodyText}>
                Pour demander la fermeture définitive de votre compte et la purge de vos données, envoyez une demande à notre équipe support. Votre demande sera traitée sous 30 jours ouvrés conformément à la réglementation.
              </Text>
            </View>

            {/* 4. Legal Disclaimers */}
            <View style={styles.legalNotice}>
              <FileText size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
              <Text style={styles.legalNoticeText}>
                Eparly est une application de gestion financière personnelle. Hébergement souverain européen.
              </Text>
            </View>
          </ScrollView>

          {/* Close Action */}
          <AnimatedPressable
            onPress={onClose}
            scaleTarget={scaleAction}
            haptic="light"
            style={styles.primaryCloseBtn}
            accessibilityRole="button"
            accessibilityLabel="Fermer">
            <Text style={styles.primaryCloseBtnText}>Fermer</Text>
          </AnimatedPressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(36, 37, 53, 0.45)',
  },
  backdrop: {
    ...StyleSheet.absoluteFill,
  },
  sheetContainer: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    maxHeight: '88%',
    paddingHorizontal: Spacing.four,
    paddingTop: 10,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 10,
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(36, 37, 53, 0.15)',
    alignSelf: 'center',
    marginBottom: 14,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  headerTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  headerSubtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
  },
  closeBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.18)',
  },
  scrollContent: {
    gap: 12,
    paddingBottom: 16,
  },
  infoCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    gap: 8,
  },
  cardHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  cardHeaderTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  cardBodyText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    lineHeight: 18,
  },
  gdprActionBlock: {
    marginTop: 4,
  },
  emailBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: BRAND_COLORS.pureWhite,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    alignSelf: 'flex-start',
  },
  emailBtnText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    color: BRAND_COLORS.deepLavender,
  },
  legalNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  legalNoticeText: {
    flex: 1,
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    lineHeight: 15,
  },
  primaryCloseBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 13,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    marginTop: 6,
  },
  primaryCloseBtnText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 14.5,
    fontWeight: '700',
    color: BRAND_COLORS.pureWhite,
  },
});

