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
  AlertCircle,
  Calendar,
  Check,
  CheckCircle2,
  Copy,
  Hash,
  Mail,
  Send,
  User as UserIcon,
  X,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import { resendVerificationApi } from '@/services/auth-api';
import { StoredUserProfile } from '@/services/session-storage';
import { formatDateDisplay } from '@/utils/formatters';
import { triggerHaptic } from '@/utils/haptics';

export interface PersonalInfoSheetProps {
  visible: boolean;
  user: StoredUserProfile | null;
  onClose: () => void;
}

/**
 * Editorial Personal Information Inspector Sheet.
 * Displays user identity, real email verification status with actionable resend,
 * registration tenure, and masked account ID.
 */
export function PersonalInfoSheet({
  visible,
  user,
  onClose,
}: PersonalInfoSheetProps) {
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();
  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  const [copiedId, setCopiedId] = useState<boolean>(false);
  const [isResending, setIsResending] = useState<boolean>(false);
  const [resendStatus, setResendStatus] = useState<string | null>(null);

  const isVerified = Boolean(user?.emailVerifiedAt);

  const handleCopyId = () => {
    if (!user?.id) return;
    void triggerHaptic.light();
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 2000);
  };

  const handleResendVerification = async () => {
    if (!user?.email || isResending) return;
    try {
      setIsResending(true);
      setResendStatus(null);
      void triggerHaptic.light();

      const res = await resendVerificationApi(user.email);
      setIsResending(false);

      if (res.success) {
        setResendStatus('Email de confirmation renvoyé !');
        void triggerHaptic.success();
      } else {
        setResendStatus(res.error || 'Erreur lors de l’envoi');
      }
    } catch {
      setIsResending(false);
      setResendStatus('Erreur de connexion');
    }
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
          {/* Sheet Grabber Handle */}
          <View style={styles.handle} />

          {/* Sheet Header */}
          <View style={styles.headerRow}>
            <View>
              <Text style={styles.headerTitle}>Informations du compte</Text>
              <Text style={styles.headerSubtitle}>Identité & coordonnées personnelles</Text>
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

          {/* Details Card */}
          <View style={styles.detailsCard}>
            {/* Full Name */}
            <View style={styles.detailRow}>
              <View style={styles.detailLabelGroup}>
                <UserIcon size={15} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.detailLabel}>Nom complet</Text>
              </View>
              <Text style={styles.detailValue}>{user?.fullName || 'Non renseigné'}</Text>
            </View>

            <View style={styles.divider} />

            {/* Email Address */}
            <View style={styles.detailRow}>
              <View style={styles.detailLabelGroup}>
                <Mail size={15} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.detailLabel}>Adresse email</Text>
              </View>
              <Text style={styles.detailValue}>{user?.email}</Text>
            </View>

            <View style={styles.divider} />

            {/* Email Verification Status */}
            <View style={styles.detailRow}>
              <View style={styles.detailLabelGroup}>
                {isVerified ? (
                  <CheckCircle2 size={15} color="#15803D" strokeWidth={2} />
                ) : (
                  <AlertCircle size={15} color="#B45309" strokeWidth={2} />
                )}
                <Text style={styles.detailLabel}>Vérification email</Text>
              </View>
              {isVerified ? (
                <View style={styles.verifiedBadge}>
                  <Text style={styles.verifiedText}>Vérifié</Text>
                </View>
              ) : (
                <View style={styles.unverifiedBadge}>
                  <Text style={styles.unverifiedText}>En attente</Text>
                </View>
              )}
            </View>

            {/* Resend Verification Action if unverified */}
            {!isVerified && (
              <View style={styles.resendBlock}>
                <AnimatedPressable
                  onPress={handleResendVerification}
                  scaleTarget={scaleAction}
                  disabled={isResending}
                  style={styles.resendBtn}
                  accessibilityRole="button"
                  accessibilityLabel="Renvoyer l'email de confirmation">
                  {isResending ? (
                    <ActivityIndicator size="small" color={BRAND_COLORS.deepLavender} />
                  ) : (
                    <>
                      <Send size={12} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                      <Text style={styles.resendBtnText}>Renvoyer le lien de confirmation</Text>
                    </>
                  )}
                </AnimatedPressable>
                {resendStatus && (
                  <Text style={styles.resendFeedbackText}>{resendStatus}</Text>
                )}
              </View>
            )}

            <View style={styles.divider} />

            {/* Creation Date */}
            <View style={styles.detailRow}>
              <View style={styles.detailLabelGroup}>
                <Calendar size={15} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.detailLabel}>Date d’inscription</Text>
              </View>
              <Text style={styles.detailValue}>
                {formatDateDisplay(user?.createdAt, { includeYear: true })}
              </Text>
            </View>

            <View style={styles.divider} />

            {/* Account ID (Masked) */}
            <View style={styles.detailRow}>
              <View style={styles.detailLabelGroup}>
                <Hash size={15} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.detailLabel}>Identifiant de compte</Text>
              </View>
              <AnimatedPressable
                onPress={handleCopyId}
                scaleTarget={scalePress}
                style={styles.copyIdBtn}
                accessibilityRole="button"
                accessibilityLabel="Copier l'identifiant">
                <Text style={styles.maskedIdText}>
                  {user?.id ? `${user.id.slice(0, 8)}...${user.id.slice(-4)}` : '—'}
                </Text>
                {copiedId ? (
                  <Check size={12} color="#15803D" strokeWidth={2.4} />
                ) : (
                  <Copy size={12} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                )}
              </AnimatedPressable>
            </View>
          </View>

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
  detailsCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 18,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    marginBottom: 18,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 9,
  },
  detailLabelGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  detailLabel: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
  },
  detailValue: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  divider: {
    height: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
  },
  verifiedBadge: {
    backgroundColor: BRAND_COLORS.softMintBackground,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  verifiedText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11,
    color: '#15803D',
  },
  unverifiedBadge: {
    backgroundColor: '#FEF3C7',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  unverifiedText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11,
    color: '#B45309',
  },
  resendBlock: {
    paddingVertical: 6,
    alignItems: 'flex-start',
  },
  resendBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: 'rgba(128, 103, 161, 0.10)',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
  },
  resendBtnText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11.5,
    color: BRAND_COLORS.deepLavender,
  },
  resendFeedbackText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.deepLavender,
    marginTop: 4,
    marginLeft: 2,
  },
  copyIdBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: BRAND_COLORS.pureWhite,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.18)',
  },
  maskedIdText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    color: BRAND_COLORS.darkCharcoal,
  },
  primaryCloseBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 13,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
  },
  primaryCloseBtnText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 14.5,
    fontWeight: '700',
    color: BRAND_COLORS.pureWhite,
  },
});

