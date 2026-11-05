import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import {
  Check,
  CreditCard,
  Edit2,
  FileSpreadsheet,
  Info,
  X,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import { AccountItem, fetchAccounts, updateAccount } from '@/services/dashboard-api';
import { formatDateDisplay } from '@/utils/formatters';
import { triggerHaptic } from '@/utils/haptics';

export interface AccountsViewerSheetProps {
  visible: boolean;
  onClose: () => void;
  onAccountUpdated?: () => void;
}

/**
 * Editorial Accounts & Data Sources Sheet.
 * Displays all user financial accounts from GET /v1/accounts and
 * provides inline account renaming backed by PATCH /v1/accounts/{id}.
 */
export function AccountsViewerSheet({
  visible,
  onClose,
  onAccountUpdated,
}: AccountsViewerSheetProps) {
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();
  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  const [accounts, setAccounts] = useState<AccountItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState<string>('');
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;

    let isMounted = true;
    setIsLoading(true);
    setSaveError(null);
    setEditingId(null);

    fetchAccounts().then((res) => {
      if (isMounted) {
        if (res.success && res.data) {
          setAccounts(res.data);
        }
        setIsLoading(false);
      }
    });

    return () => {
      isMounted = false;
    };
  }, [visible]);

  const handleStartEdit = (account: AccountItem) => {
    void triggerHaptic.light();
    setEditingId(account.id);
    setEditName(account.name);
    setSaveError(null);
  };

  const handleSaveEdit = async (account: AccountItem) => {
    if (!editName.trim() || isSaving) return;
    try {
      setIsSaving(true);
      setSaveError(null);
      void triggerHaptic.light();

      const res = await updateAccount(account.id, editName.trim());
      setIsSaving(false);

      if (res.success && res.data) {
        setAccounts((prev) =>
          prev.map((acc) => (acc.id === account.id ? res.data! : acc))
        );
        setEditingId(null);
        void triggerHaptic.success();
        onAccountUpdated?.();
      } else {
        setSaveError(res.error || 'Erreur lors de la modification');
      }
    } catch {
      setIsSaving(false);
      setSaveError('Erreur de connexion');
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
          {/* Drag Handle */}
          <View style={styles.handle} />

          {/* Header */}
          <View style={styles.headerRow}>
            <View>
              <Text style={styles.headerTitle}>Comptes & Sources bancaires</Text>
              <Text style={styles.headerSubtitle}>
                {accounts.length} compte{accounts.length > 1 ? 's' : ''} configuré{accounts.length > 1 ? 's' : ''}
              </Text>
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

          {/* Content List */}
          {isLoading ? (
            <View style={styles.loadingBox}>
              <ActivityIndicator size="small" color={BRAND_COLORS.deepLavender} />
              <Text style={styles.loadingText}>Chargement des comptes...</Text>
            </View>
          ) : (
            <ScrollView
              showsVerticalScrollIndicator={false}
              contentContainerStyle={styles.scrollList}>
              {accounts.map((account) => {
                const isEditing = editingId === account.id;

                return (
                  <View key={account.id} style={styles.accountCard}>
                    <View style={styles.accountCardHeader}>
                      <View style={styles.accountIconCircle}>
                        <FileSpreadsheet
                          size={18}
                          color={BRAND_COLORS.deepLavender}
                          strokeWidth={2.2}
                        />
                      </View>

                      {isEditing ? (
                        <View style={styles.editRow}>
                          <TextInput
                            value={editName}
                            onChangeText={setEditName}
                            style={styles.editInput}
                            autoFocus
                            placeholder="Nom du compte"
                            maxLength={50}
                          />
                          <AnimatedPressable
                            onPress={() => handleSaveEdit(account)}
                            scaleTarget={scalePress}
                            disabled={isSaving}
                            style={styles.saveBtn}
                            accessibilityLabel="Enregistrer le nom">
                            {isSaving ? (
                              <ActivityIndicator size="small" color={BRAND_COLORS.pureWhite} />
                            ) : (
                              <Check size={14} color={BRAND_COLORS.pureWhite} strokeWidth={2.4} />
                            )}
                          </AnimatedPressable>
                        </View>
                      ) : (
                        <View style={styles.accountTitleCol}>
                          <Text style={styles.accountName} numberOfLines={1}>
                            {account.name}
                          </Text>
                          <Text style={styles.accountMeta}>
                            Source : {account.sourceType || 'CSV'} • Devise : {account.currency || 'EUR'}
                          </Text>
                        </View>
                      )}

                      {!isEditing && (
                        <AnimatedPressable
                          onPress={() => handleStartEdit(account)}
                          scaleTarget={scalePress}
                          style={styles.renameBtn}
                          accessibilityLabel="Renommer le compte">
                          <Edit2 size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                        </AnimatedPressable>
                      )}
                    </View>

                    {/* Metadata Footer */}
                    <View style={styles.accountFooter}>
                      <Text style={styles.accountDateText}>
                        Créé le {formatDateDisplay(account.createdAt, { includeYear: true })}
                      </Text>
                      <View style={styles.currencyBadge}>
                        <CreditCard size={10} color={BRAND_COLORS.deepLavender} strokeWidth={2} />
                        <Text style={styles.currencyBadgeText}>{account.currency || 'EUR'}</Text>
                      </View>
                    </View>
                  </View>
                );
              })}

              {saveError && (
                <Text style={styles.errorText}>{saveError}</Text>
              )}

              {/* Informational Callout */}
              <View style={styles.infoCallout}>
                <Info size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                <Text style={styles.infoCalloutText}>
                  Les écritures et déduplications sont automatiquement rattachées à votre compte principal.
                </Text>
              </View>
            </ScrollView>
          )}

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
  loadingBox: {
    paddingVertical: 36,
    alignItems: 'center',
    gap: 10,
  },
  loadingText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
  },
  scrollList: {
    gap: 10,
    paddingBottom: 16,
  },
  accountCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    gap: 10,
  },
  accountCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  accountIconCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
  },
  accountTitleCol: {
    flex: 1,
    gap: 1,
  },
  accountName: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 14.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  accountMeta: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
  },
  renameBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: BRAND_COLORS.pureWhite,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.18)',
  },
  editRow: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  editInput: {
    flex: 1,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderWidth: 1,
    borderColor: BRAND_COLORS.deepLavender,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    fontFamily: FONT_FAMILY.medium,
    fontSize: 13.5,
    color: BRAND_COLORS.darkCharcoal,
  },
  saveBtn: {
    width: 30,
    height: 30,
    borderRadius: 8,
    backgroundColor: BRAND_COLORS.deepLavender,
    justifyContent: 'center',
    alignItems: 'center',
  },
  accountFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 6,
    borderTopWidth: 1,
    borderTopColor: 'rgba(0, 0, 0, 0.04)',
  },
  accountDateText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
  },
  currencyBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: BRAND_COLORS.pureWhite,
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.18)',
  },
  currencyBadgeText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 10.5,
    color: BRAND_COLORS.deepLavender,
  },
  errorText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12,
    color: '#E11D48',
    textAlign: 'center',
    marginTop: 4,
  },
  infoCallout: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.16)',
    marginTop: 6,
  },
  infoCalloutText: {
    flex: 1,
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
    lineHeight: 16,
  },
  primaryCloseBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 13,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    marginTop: 8,
  },
  primaryCloseBtnText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 14.5,
    fontWeight: '700',
    color: BRAND_COLORS.pureWhite,
  },
});

