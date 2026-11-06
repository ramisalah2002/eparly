import React from 'react';
import { Modal, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { LogOut, ShieldCheck } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import { triggerHaptic } from '@/utils/haptics';

export interface SignOutConfirmSheetProps {
  visible: boolean;
  onClose: () => void;
  onConfirm: () => void;
  isSigningOut?: boolean;
}

/**
 * Editorial Sign Out Confirmation Sheet (Concept A+).
 * Replaces the jarring red button with a quiet, reassuring confirmation
 * explaining that encrypted tokens are purged while account data remains safe.
 */
export function SignOutConfirmSheet({
  visible,
  onClose,
  onConfirm,
  isSigningOut = false,
}: SignOutConfirmSheetProps) {
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();

  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  const handleConfirm = () => {
    void triggerHaptic.light();
    onConfirm();
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

          {/* Icon Badge */}
          <View style={styles.iconCircle}>
            <LogOut size={26} color="#E11D48" strokeWidth={2.2} />
          </View>

          {/* Title & Explanations */}
          <Text style={styles.title}>Se déconnecter d’Eparly</Text>
          <Text style={styles.description}>
            Votre session locale sera fermée et vos clés chiffrées sécurisées sur cet appareil.
            Toutes vos écritures, soldes et budgets restent sauvegardés sur votre compte.
          </Text>

          {/* Security Reassurance Callout */}
          <View style={styles.securityCallout}>
            <ShieldCheck size={16} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
            <Text style={styles.securityText}>
              Protection active : aucun jeton ni mot de passe ne reste en mémoire après déconnexion.
            </Text>
          </View>

          {/* Action Buttons */}
          <View style={styles.actionsColumn}>
            <AnimatedPressable
              onPress={handleConfirm}
              scaleTarget={scaleAction}
              haptic="light"
              disabled={isSigningOut}
              style={styles.destructiveBtn}
              accessibilityRole="button"
              accessibilityLabel="Confirmer la déconnexion">
              <Text style={styles.destructiveBtnText}>
                {isSigningOut ? 'Déconnexion en cours...' : 'Confirmer la déconnexion'}
              </Text>
            </AnimatedPressable>

            <AnimatedPressable
              onPress={onClose}
              scaleTarget={scalePress}
              haptic="light"
              disabled={isSigningOut}
              style={styles.cancelBtn}
              accessibilityRole="button"
              accessibilityLabel="Rester connecté">
              <Text style={styles.cancelBtnText}>Rester connecté</Text>
            </AnimatedPressable>
          </View>
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
    alignItems: 'center',
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
    marginBottom: 16,
  },
  iconCircle: {
    width: 58,
    height: 58,
    borderRadius: 29,
    backgroundColor: '#FFF0F3',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  title: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    letterSpacing: -0.2,
    marginBottom: 8,
  },
  description: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    paddingHorizontal: 12,
    marginBottom: 16,
  },
  securityCallout: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 12,
    paddingVertical: 9,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.18)',
    marginBottom: 20,
    width: '100%',
  },
  securityText: {
    flex: 1,
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
    lineHeight: 16,
  },
  actionsColumn: {
    width: '100%',
    gap: 8,
  },
  destructiveBtn: {
    backgroundColor: '#E11D48',
    paddingVertical: 13,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
  },
  destructiveBtnText: {
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
});
