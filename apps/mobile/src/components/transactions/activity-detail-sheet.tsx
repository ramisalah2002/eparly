import React, { useState } from 'react';
import {
  Modal,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { useRouter } from 'expo-router';
import {
  Calendar,
  Check,
  CheckCircle2,
  Copy,
  CreditCard,
  FileText,
  Sparkles,
  Tag,
  X,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { MoneyAmount } from '@/components/dashboard/money-amount';
import { getCategoryTheme } from '@/constants/category-theme';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { getActivityCopy } from '@/constants/activity-copy';
import { TransactionItem } from '@/services/dashboard-api';
import { formatDateDisplay } from '@/utils/formatters';
import { triggerHaptic } from '@/utils/haptics';

interface ActivityDetailSheetProps {
  transaction: TransactionItem | null;
  onClose: () => void;
  locale?: string;
}

/**
 * Editorial Transaction Inspector Sheet (Concept A+).
 * Eliminates nested cards and oversized technical text in favor of an elegant,
 * typographic inspector featuring hero amounts, clean key-value rows,
 * raw bank description disclosure, and verified subscription alternative actions.
 */
export function ActivityDetailSheet({
  transaction,
  onClose,
  locale = 'en-IE',
}: ActivityDetailSheetProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();
  const copy = getActivityCopy(locale);
  const [copiedRaw, setCopiedRaw] = useState<boolean>(false);

  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  if (!transaction) return null;

  const isPositive = transaction.amountMinor > 0;
  const theme = getCategoryTheme(transaction.categoryName, isPositive);
  const IconComponent = theme.icon;

  const displayLabel = transaction.normalizedLabel || transaction.rawLabel || copy.expenseType;
  const isSubscription =
    transaction.categoryName?.toLowerCase().includes('abonnement') ||
    transaction.categoryName?.toLowerCase().includes('subscription');

  const resolveTypeLabel = () => {
    if (isSubscription) return copy.subscriptionType;
    if (transaction.transactionType === 'INCOME' || isPositive) return copy.incomeType;
    if (transaction.transactionType === 'TRANSFER') return copy.transferType;
    if (transaction.transactionType === 'FEE') return copy.feeType;
    if (transaction.transactionType === 'REFUND') return copy.refundType;
    return copy.expenseType;
  };

  const handleCopyRaw = () => {
    void triggerHaptic.light();
    setCopiedRaw(true);
    setTimeout(() => setCopiedRaw(false), 2000);
  };

  const handleExploreSubscriptions = () => {
    void triggerHaptic.selection();
    onClose();
    router.push('/(tabs)/subscriptions');
  };

  return (
    <Modal
      visible={transaction !== null}
      transparent
      animationType="fade"
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <AnimatedPressable
          style={styles.backdrop}
          onPress={onClose}
          scaleTarget={1.0}
          accessibilityLabel={copy.close}
          accessibilityRole="button"
        />

        <View style={[styles.sheetContainer, { paddingBottom: Math.max(insets.bottom, 16) + 12 }]}>
          {/* Sheet Grabber Handle */}
          <View style={styles.handle} />

          {/* Sheet Header */}
          <View style={styles.headerRow}>
            <Text style={styles.headerTitle}>{copy.transactionDetailTitle}</Text>
            <AnimatedPressable
              onPress={onClose}
              scaleTarget={scalePress}
              style={styles.closeBtn}
              accessibilityRole="button"
              accessibilityLabel={copy.close}>
              <X size={16} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
            </AnimatedPressable>
          </View>

          {/* Hero Section: Category Icon, Merchant & Amount */}
          <View style={styles.heroSection}>
            <View style={[styles.heroIconBadge, { backgroundColor: theme.backgroundColor }]}>
              <IconComponent size={26} color={theme.color} strokeWidth={2.4} />
            </View>

            <Text style={styles.heroMerchantTitle} numberOfLines={2}>
              {displayLabel}
            </Text>

            <View style={styles.heroAmountRow}>
              <MoneyAmount
                amountMinor={transaction.amountMinor}
                currency={transaction.currency || 'EUR'}
                locale={locale}
                size="hero"
                showSign={isPositive}
                color={isPositive ? '#1E7558' : BRAND_COLORS.darkCharcoal}
                accessibilityLabelPrefix={displayLabel}
              />
            </View>

            <View style={[styles.categoryPill, { backgroundColor: theme.backgroundColor }]}>
              <Text style={[styles.categoryPillText, { color: theme.color }]}>
                {transaction.categoryName || copy.showAllCategories}
              </Text>
            </View>
          </View>

          {/* Structured Key-Value Metadata Card */}
          <View style={styles.metadataCard}>
            {/* Booking Date */}
            <View style={styles.metaRow}>
              <View style={styles.metaLabelGroup}>
                <Calendar size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.metaLabel}>{copy.dateLabel}</Text>
              </View>
              <Text style={styles.metaValue}>
                {formatDateDisplay(transaction.bookingDate, { includeYear: true })}
              </Text>
            </View>

            <View style={styles.metaDivider} />

            {/* Transaction Type */}
            <View style={styles.metaRow}>
              <View style={styles.metaLabelGroup}>
                <Tag size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.metaLabel}>{copy.typeLabel}</Text>
              </View>
              <Text style={styles.metaValue}>{resolveTypeLabel()}</Text>
            </View>

            <View style={styles.metaDivider} />

            {/* Status */}
            <View style={styles.metaRow}>
              <View style={styles.metaLabelGroup}>
                <CheckCircle2 size={14} color="#15803D" strokeWidth={2} />
                <Text style={styles.metaLabel}>{copy.statusLabel}</Text>
              </View>
              <View style={styles.statusPill}>
                <Text style={styles.statusPillText}>{copy.statusCleared}</Text>
              </View>
            </View>

            <View style={styles.metaDivider} />

            {/* Account */}
            <View style={styles.metaRow}>
              <View style={styles.metaLabelGroup}>
                <CreditCard size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.metaLabel}>{copy.accountLabel}</Text>
              </View>
              <Text style={styles.metaValue}>{copy.mainAccount}</Text>
            </View>

            {/* Raw Bank Label Disclosure */}
            {transaction.rawLabel && (
              <>
                <View style={styles.metaDivider} />
                <View style={styles.rawLabelBlock}>
                  <View style={styles.rawHeaderRow}>
                    <View style={styles.metaLabelGroup}>
                      <FileText size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                      <Text style={styles.metaLabel}>{copy.rawLabel}</Text>
                    </View>

                    <AnimatedPressable
                      onPress={handleCopyRaw}
                      scaleTarget={scalePress}
                      style={styles.copyBtn}
                      accessibilityRole="button"
                      accessibilityLabel={copiedRaw ? copy.copiedRawLabel : copy.copyRawLabel}>
                      {copiedRaw ? (
                        <>
                          <Check size={11} color="#15803D" strokeWidth={2.4} />
                          <Text style={[styles.copyBtnText, { color: '#15803D' }]}>{copy.copiedRawLabel}</Text>
                        </>
                      ) : (
                        <>
                          <Copy size={11} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
                          <Text style={styles.copyBtnText}>{copy.copyRawLabel}</Text>
                        </>
                      )}
                    </AnimatedPressable>
                  </View>

                  <Text style={styles.rawLabelText}>{transaction.rawLabel}</Text>
                </View>
              </>
            )}
          </View>

          {/* Contextual Subscription Action */}
          {isSubscription && (
            <AnimatedPressable
              onPress={handleExploreSubscriptions}
              scaleTarget={scaleAction}
              haptic="selection"
              style={styles.subActionBtn}
              accessibilityRole="button"
              accessibilityLabel={copy.discoverAlternative}>
              <Sparkles size={15} color="#4A3B72" strokeWidth={2.2} />
              <Text style={styles.subActionText}>{copy.discoverAlternative}</Text>
            </AnimatedPressable>
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
    backgroundColor: 'rgba(26, 26, 26, 0.45)',
  },
  backdrop: {
    ...StyleSheet.absoluteFill,
  },
  sheetContainer: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 20,
    paddingTop: 10,
    maxHeight: '90%',
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
    backgroundColor: 'rgba(0, 0, 0, 0.12)',
    alignSelf: 'center',
    marginBottom: 12,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(36, 37, 53, 0.06)',
    marginBottom: 12,
  },
  headerTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  closeBtn: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  heroSection: {
    alignItems: 'center',
    paddingVertical: 8,
    marginBottom: 12,
  },
  heroIconBadge: {
    width: 48,
    height: 48,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 10,
  },
  heroMerchantTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    letterSpacing: -0.2,
    marginBottom: 6,
    paddingHorizontal: 16,
  },
  heroAmountRow: {
    marginBottom: 8,
  },
  categoryPill: {
    paddingHorizontal: 10,
    paddingVertical: 3.5,
    borderRadius: 8,
  },
  categoryPillText: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 11.5,
    fontWeight: '600',
  },
  metadataCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 18,
    padding: 14,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.18)',
    marginBottom: 12,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 7,
  },
  metaLabelGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  metaLabel: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    letterSpacing: -0.1,
  },
  metaValue: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 13,
    color: BRAND_COLORS.darkCharcoal,
    fontWeight: '600',
  },
  metaDivider: {
    height: 1,
    backgroundColor: 'rgba(36, 37, 53, 0.06)',
    marginVertical: 2,
  },
  statusPill: {
    backgroundColor: '#DCFCE7',
    paddingHorizontal: 8,
    paddingVertical: 2.5,
    borderRadius: 6,
  },
  statusPillText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 11,
    color: '#15803D',
    fontWeight: '700',
  },
  rawLabelBlock: {
    paddingVertical: 6,
  },
  rawHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 5,
  },
  copyBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 6,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
  },
  copyBtnText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
  },
  rawLabelText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12,
    lineHeight: 16,
    color: BRAND_COLORS.secondaryText,
  },
  subActionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(128, 103, 161, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(128, 103, 161, 0.25)',
    paddingVertical: 12,
    borderRadius: 16,
    gap: 7,
  },
  subActionText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13,
    fontWeight: '700',
    color: '#4A3B72',
    letterSpacing: -0.1,
  },
});

