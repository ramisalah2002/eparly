import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import {
  CheckCircle2,
  KeyRound,
  Mail,
  ShieldCheck,
  X,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import { forgotPasswordApi } from '@/services/auth-api';
import { triggerHaptic } from '@/utils/haptics';

export interface PasswordResetSheetProps {
  visible: boolean;
  email?: string;
  onClose: () => void;
}

/**
 * Editorial Password Reset Request Sheet.
 * Triggers a secure 6-digit verification code to the authenticated user's email
 * utilizing the existing production auth endpoint POST /v1/auth/forgot-password.
 */
export function PasswordResetSheet({
  visible,
  email,
  onClose,
}: PasswordResetSheetProps) {
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();
  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [successSent, setSuccessSent] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleRequestReset = async () => {
    if (!email || isLoading) return;
    try {
      setIsLoading(true);
      setErrorMessage(null);
      void triggerHaptic.light();

      const res = await forgotPasswordApi(email);
      setIsLoading(false);

      if (res.success) {
        setSuccessSent(true);
        void triggerHaptic.success();
      } else {
        setErrorMessage(res.error || 'Impossible d’envoyer le code de réinitialisation.');
      }
    } catch {
      setIsLoading(false);
      setErrorMessage('Erreur de connexion lors de la demande.');
    }
  };

  const handleSheetClose = () => {
    setSuccessSent(false);
    setErrorMessage(null);
    onClose();
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={handleSheetClose}>
      <View style={styles.overlay}>
        <AnimatedPressable
          style={styles.backdrop}
          onPress={handleSheetClose}
          scaleTarget={1.0}
          accessibilityLabel="Fermer"
          accessibilityRole="button"
        />

        <View
          style={[
            styles.sheetContainer,
            { paddingBottom: Math.max(insets.bottom, 16) + 12 },
          ]}>
          {/* Sheet Grabber Handle */}
          <View style={styles.handle} />

          {/* Header */}
          <View style={styles.headerRow}>
            <View>
              <Text style={styles.headerTitle}>Sécurité & Mot de passe</Text>
              <Text style={styles.headerSubtitle}>Réinitialisation sécurisée par email</Text>
            </View>

            <AnimatedPressable
              onPress={handleSheetClose}
              scaleTarget={scalePress}
              style={styles.closeBtn}
              accessibilityRole="button"
              accessibilityLabel="Fermer">
              <X size={16} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
            </AnimatedPressable>
          </View>

          {successSent ? (
            <View style={styles.statusBox}>
              <View style={styles.successIconCircle}>
                <CheckCircle2 size={36} color="#15803D" strokeWidth={2.4} />
              </View>
              <Text style={styles.statusTitle}>Code de validation envoyé !</Text>
              <Text style={styles.statusDesc}>
                Un code à 6 chiffres a été envoyé à {email}. Suivez les instructions reçues pour définir votre nouveau mot de passe.
              </Text>

              <AnimatedPressable
                onPress={handleSheetClose}
                scaleTarget={scaleAction}
                haptic="light"
                style={styles.primaryBtn}
                accessibilityRole="button"
                accessibilityLabel="Compris">
                <Text style={styles.primaryBtnText}>Compris</Text>
              </AnimatedPressable>
            </View>
          ) : (
            <View style={styles.contentWrapper}>
              <View style={styles.iconCircle}>
                <KeyRound size={26} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
              </View>

              <Text style={styles.bodyDescription}>
                Pour protéger la confidentialité de vos données bancaires, nous envoyons un code à usage unique à votre adresse email enregistrée.
              </Text>

              {/* Email pill */}
              <View style={styles.emailPill}>
                <Mail size={15} color={BRAND_COLORS.deepLavender} strokeWidth={2} />
                <Text style={styles.emailText}>{email}</Text>
              </View>

              {/* Security advice */}
              <View style={styles.securityCallout}>
                <ShieldCheck size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.securityText}>
                  Le code est valable 15 minutes. Vos sessions actives restent protégées.
                </Text>
              </View>

              {errorMessage && (
                <Text style={styles.errorText}>{errorMessage}</Text>
              )}

              <View style={styles.actionCol}>
                <AnimatedPressable
                  onPress={handleRequestReset}
                  scaleTarget={scaleAction}
                  haptic="light"
                  disabled={isLoading}
                  style={styles.primaryBtn}
                  accessibilityRole="button"
                  accessibilityLabel="Envoyer le code de réinitialisation">
                  {isLoading ? (
                    <ActivityIndicator size="small" color={BRAND_COLORS.pureWhite} />
                  ) : (
                    <Text style={styles.primaryBtnText}>Envoyer le code de réinitialisation</Text>
                  )}
                </AnimatedPressable>

                <AnimatedPressable
                  onPress={handleSheetClose}
                  scaleTarget={scalePress}
                  haptic="light"
                  style={styles.cancelBtn}
                  accessibilityRole="button"
                  accessibilityLabel="Annuler">
                  <Text style={styles.cancelBtnText}>Annuler</Text>
                </AnimatedPressable>
              </View>
            </View>
          )}
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
    marginBottom: 16,
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
  contentWrapper: {
    alignItems: 'center',
    gap: 12,
  },
  iconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 2,
  },
  bodyDescription: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    paddingHorizontal: 8,
  },
  emailPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    marginVertical: 4,
  },
  emailText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 13,
    color: BRAND_COLORS.darkCharcoal,
  },
  securityCallout: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    padding: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.16)',
    width: '100%',
  },
  securityText: {
    flex: 1,
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
  },
  errorText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12,
    color: '#E11D48',
    textAlign: 'center',
  },
  actionCol: {
    width: '100%',
    gap: 8,
    marginTop: 8,
  },
  primaryBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 13,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
  },
  primaryBtnText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 14.5,
    fontWeight: '700',
    color: BRAND_COLORS.pureWhite,
  },
  cancelBtn: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingVertical: 12,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
  },
  cancelBtnText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 14,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  statusBox: {
    alignItems: 'center',
    paddingVertical: 20,
    gap: 12,
  },
  successIconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: BRAND_COLORS.softMintBackground,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  statusTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  statusDesc: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    paddingHorizontal: 16,
    marginBottom: 12,
  },
});

