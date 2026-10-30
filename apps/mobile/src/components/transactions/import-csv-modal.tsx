import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import {
  AlertCircle,
  CheckCircle2,
  FileSpreadsheet,
  FileText,
  Upload,
  X,
} from 'lucide-react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { Spacing } from '@/constants/theme';
import {
  confirmCsvImport,
  CsvFilePayload,
  getOrCreateDefaultAccount,
  ImportJobDetails,
  ImportPreviewResponse,
  previewCsvImport,
} from '@/services/import-api';
import { formatCurrencyMinor, formatDateDisplay } from '@/utils/formatters';

interface ImportCsvModalProps {
  visible: boolean;
  onClose: () => void;
  onImportSuccess?: (job: ImportJobDetails) => void;
}

type Step = 'PICK' | 'PREVIEW' | 'IMPORTING' | 'SUCCESS' | 'ERROR';

export function ImportCsvModal({
  visible,
  onClose,
  onImportSuccess,
}: ImportCsvModalProps) {
  const [step, setStep] = useState<Step>('PICK');
  const [selectedFile, setSelectedFile] = useState<CsvFilePayload | null>(null);
  const [previewData, setPreviewData] = useState<ImportPreviewResponse | null>(null);
  const [importResult, setImportResult] = useState<ImportJobDetails | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [accountId, setAccountId] = useState<string | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState<boolean>(false);

  const resetState = () => {
    setStep('PICK');
    setSelectedFile(null);
    setPreviewData(null);
    setImportResult(null);
    setErrorMessage(null);
    setAccountId(null);
    setIsAnalyzing(false);
  };

  const handleClose = () => {
    resetState();
    onClose();
  };

  const handlePickDocument = async () => {
    try {
      setErrorMessage(null);

      const result = await DocumentPicker.getDocumentAsync({
        type: [
          'text/csv',
          'text/comma-separated-values',
          'application/csv',
          'text/plain',
          '*/*',
        ],
        copyToCacheDirectory: true,
      });

      if (result.canceled || !result.assets || result.assets.length === 0) {
        return;
      }

      const asset = result.assets[0];
      const payload: CsvFilePayload = {
        uri: asset.uri,
        name: asset.name || 'releve.csv',
        mimeType: asset.mimeType || 'text/csv',
        size: asset.size,
        fileObj: (asset as any).file,
      };

      setSelectedFile(payload);
      setIsAnalyzing(true);
      setStep('PREVIEW');

      // 1. Resolve or create user's default bank account
      const accountRes = await getOrCreateDefaultAccount();
      if (!accountRes.success || !accountRes.data) {
        setErrorMessage(accountRes.error || 'Impossible d’accéder à votre compte bancaire.');
        setStep('ERROR');
        setIsAnalyzing(false);
        return;
      }

      const currentAccountId = accountRes.data.id;
      setAccountId(currentAccountId);

      // 2. Request backend CSV preview & validation
      const previewRes = await previewCsvImport(currentAccountId, payload);
      if (!previewRes.success || !previewRes.data) {
        setErrorMessage(previewRes.error || 'Erreur lors de l’analyse du fichier CSV.');
        setStep('ERROR');
        setIsAnalyzing(false);
        return;
      }

      setPreviewData(previewRes.data);
      setIsAnalyzing(false);
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Erreur inattendue');
      setStep('ERROR');
      setIsAnalyzing(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!accountId || !selectedFile || !previewData) {
      return;
    }

    try {
      setStep('IMPORTING');
      setErrorMessage(null);

      const mappingToApply = previewData.suggestedMapping || previewData.appliedMapping;
      if (!mappingToApply) {
        setErrorMessage('Mapping de colonnes introuvable.');
        setStep('ERROR');
        return;
      }

      const chosenCharset = previewData.charsetAmbiguous
        ? (previewData.detectedCharset || previewData.charsetCandidates?.[0] || 'WINDOWS_1252')
        : previewData.detectedCharset;

      const res = await confirmCsvImport({
        accountId,
        file: selectedFile,
        fileHash: previewData.fileHash,
        mapping: mappingToApply,
        charset: chosenCharset,
      });

      if (!res.success || !res.data) {
        setErrorMessage(res.error || 'Échec de l’enregistrement de l’import.');
        setStep('ERROR');
        return;
      }

      const job = res.data.importJob;
      setImportResult(job);
      setStep('SUCCESS');
      onImportSuccess?.(job);
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Erreur lors de l’import');
      setStep('ERROR');
    }
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={handleClose}>
      <View style={styles.modalOverlay}>
        <View style={styles.modalBackdrop} />

        <View style={styles.modalCard}>
          {/* Modal Header */}
          <View style={styles.modalHeader}>
            <View style={styles.headerTitleGroup}>
              <View style={styles.headerIconCircle}>
                <FileSpreadsheet size={20} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
              </View>
              <View>
                <Text style={styles.modalTitle}>Importer un relevé CSV</Text>
                <Text style={styles.modalSubtitle}>Formats bancaires automatiques</Text>
              </View>
            </View>

            <TouchableOpacity
              onPress={handleClose}
              style={styles.closeBtn}
              accessibilityLabel="Fermer"
              accessibilityRole="button">
              <X size={18} color={BRAND_COLORS.darkCharcoal} strokeWidth={2.2} />
            </TouchableOpacity>
          </View>

          <ScrollView
            showsVerticalScrollIndicator={false}
            contentContainerStyle={styles.scrollContent}>
            {/* STEP 1: PICK FILE */}
            {step === 'PICK' && (
              <View style={styles.stepContainer}>
                <View style={styles.dropZone}>
                  <View style={styles.dropZoneIconCircle}>
                    <Upload size={28} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                  </View>
                  <Text style={styles.dropZoneTitle}>Sélectionnez votre relevé bancaire</Text>
                  <Text style={styles.dropZoneDesc}>
                    {"Fichiers .csv (jusqu'à 5 Mo). Détection automatique des séparateurs, devises et colonnes."}
                  </Text>

                  <TouchableOpacity
                    onPress={handlePickDocument}
                    activeOpacity={0.8}
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel="Choisir un fichier CSV">
                    <Text style={styles.primaryBtnText}>Choisir un fichier</Text>
                  </TouchableOpacity>
                </View>

                {/* Info Callout */}
                <View style={styles.infoCallout}>
                  <Text style={styles.infoCalloutTitle}>Banques prises en charge</Text>
                  <Text style={styles.infoCalloutText}>
                    Boursorama, BNP Paribas, Société Générale, Crédit Agricole, Revolut, N26, CIC, et tous les exports CSV standards.
                  </Text>
                </View>
              </View>
            )}

            {/* STEP 2: PREVIEW & VALIDATION */}
            {step === 'PREVIEW' && (
              <View style={styles.stepContainer}>
                {isAnalyzing ? (
                  <View style={styles.analyzingBox}>
                    <ActivityIndicator size="large" color={BRAND_COLORS.deepLavender} />
                    <Text style={styles.analyzingText}>Analyse et déduplication du fichier...</Text>
                    {selectedFile && (
                      <Text style={styles.analyzingFileName}>{selectedFile.name}</Text>
                    )}
                  </View>
                ) : previewData ? (
                  <View style={styles.previewContainer}>
                    {/* File badge summary */}
                    <View style={styles.fileSummaryCard}>
                      <View style={styles.fileSummaryRow}>
                        <FileText size={20} color={BRAND_COLORS.deepLavender} strokeWidth={2} />
                        <View style={styles.fileSummaryTextCol}>
                          <Text style={styles.fileSummaryName} numberOfLines={1}>
                            {selectedFile?.name}
                          </Text>
                          <Text style={styles.fileSummaryMeta}>
                            {previewData.totalRows} opération{previewData.totalRows > 1 ? 's' : ''} détectée{previewData.totalRows > 1 ? 's' : ''} • Encodage {previewData.detectedCharset || 'UTF-8'}
                          </Text>
                        </View>
                      </View>

                      {/* Insertion summary stats */}
                      <View style={styles.statsRow}>
                        <View style={styles.statBox}>
                          <Text style={styles.statNumber}>{previewData.wouldInsert}</Text>
                          <Text style={styles.statLabel}>À ajouter</Text>
                        </View>
                        <View style={styles.statDivider} />
                        <View style={styles.statBox}>
                          <Text style={[styles.statNumber, { color: BRAND_COLORS.secondaryText }]}>
                            {previewData.wouldSkip}
                          </Text>
                          <Text style={styles.statLabel}>Doublons ignorés</Text>
                        </View>
                      </View>
                    </View>

                    {/* Blocking errors alert if any */}
                    {previewData.blocking && previewData.errors && previewData.errors.length > 0 && (
                      <View style={styles.errorCallout}>
                        <AlertCircle size={20} color="#E11D48" strokeWidth={2.2} />
                        <View style={styles.errorCalloutContent}>
                          <Text style={styles.errorCalloutTitle}>Erreurs bloquantes détectées</Text>
                          {previewData.errors.slice(0, 3).map((err, i) => (
                            <Text key={i} style={styles.errorCalloutItem}>
                              Ligne {err.line} : {err.message}
                            </Text>
                          ))}
                        </View>
                      </View>
                    )}

                    {/* Sample preview rows */}
                    {previewData.sample && previewData.sample.length > 0 && (
                      <View style={styles.sampleSection}>
                        <Text style={styles.sampleSectionTitle}>Aperçu des opérations détectées</Text>
                        <View style={styles.sampleList}>
                          {previewData.sample.slice(0, 3).map((item, index) => {
                            const isExpense = item.amountMinor < 0;
                            return (
                              <View key={index} style={styles.sampleRow}>
                                <View style={styles.sampleRowLeft}>
                                  <Text style={styles.sampleLabel} numberOfLines={1}>
                                    {item.label}
                                  </Text>
                                  <Text style={styles.sampleDate}>
                                    {formatDateDisplay(item.bookingDate)}
                                  </Text>
                                </View>
                                <Text
                                  style={[
                                    styles.sampleAmount,
                                    isExpense ? styles.expenseAmount : styles.incomeAmount,
                                  ]}>
                                  {formatCurrencyMinor(item.amountMinor, item.currency, {
                                    showSign: true,
                                  })}
                                </Text>
                              </View>
                            );
                          })}
                        </View>
                      </View>
                    )}

                    {/* Action Buttons */}
                    <View style={styles.actionButtonsCol}>
                      {!previewData.blocking && (
                        <TouchableOpacity
                          onPress={handleConfirmImport}
                          activeOpacity={0.8}
                          style={styles.primaryBtn}
                          accessibilityRole="button"
                          accessibilityLabel="Confirmer l'import">
                          <Text style={styles.primaryBtnText}>
                            {`Confirmer l'import (${previewData.wouldInsert} opération${previewData.wouldInsert > 1 ? 's' : ''})`}
                          </Text>
                        </TouchableOpacity>
                      )}

                      <TouchableOpacity
                        onPress={handlePickDocument}
                        activeOpacity={0.7}
                        style={styles.secondaryBtn}
                        accessibilityRole="button"
                        accessibilityLabel="Choisir un autre fichier">
                        <Text style={styles.secondaryBtnText}>Choisir un autre fichier</Text>
                      </TouchableOpacity>
                    </View>
                  </View>
                ) : null}
              </View>
            )}

            {/* STEP 3: IMPORTING IN PROGRESS */}
            {step === 'IMPORTING' && (
              <View style={styles.statusBox}>
                <ActivityIndicator size="large" color={BRAND_COLORS.deepLavender} />
                <Text style={styles.statusTitle}>Importation en cours...</Text>
                <Text style={styles.statusDesc}>
                  Enregistrement et sécurisation des écritures comptables
                </Text>
              </View>
            )}

            {/* STEP 4: SUCCESS */}
            {step === 'SUCCESS' && importResult && (
              <View style={styles.statusBox}>
                <View style={styles.successIconCircle}>
                  <CheckCircle2 size={44} color="#10B981" strokeWidth={2.2} />
                </View>
                <Text style={styles.statusTitle}>Importation réussie !</Text>
                <Text style={styles.statusDesc}>
                  Vos transactions ont été intégrées avec succès à votre compte.
                </Text>

                {/* Results badge */}
                <View style={styles.successStatsCard}>
                  <View style={styles.successStatRow}>
                    <Text style={styles.successStatLabel}>Opérations ajoutées :</Text>
                    <Text style={styles.successStatValue}>+{importResult.insertedRows}</Text>
                  </View>
                  {importResult.skippedRows > 0 && (
                    <View style={styles.successStatRow}>
                      <Text style={styles.successStatLabel}>Doublons ignorés :</Text>
                      <Text style={styles.successStatValue}>{importResult.skippedRows}</Text>
                    </View>
                  )}
                </View>

                <TouchableOpacity
                  onPress={handleClose}
                  activeOpacity={0.8}
                  style={styles.primaryBtn}
                  accessibilityRole="button"
                  accessibilityLabel="Voir mes transactions">
                  <Text style={styles.primaryBtnText}>Voir mes transactions</Text>
                </TouchableOpacity>
              </View>
            )}

            {/* STEP 5: ERROR */}
            {step === 'ERROR' && (
              <View style={styles.statusBox}>
                <View style={styles.errorIconCircle}>
                  <AlertCircle size={44} color="#E11D48" strokeWidth={2.2} />
                </View>
                <Text style={styles.statusTitle}>{"Impossible d'importer"}</Text>
                <Text style={styles.errorDetailsText}>{errorMessage || 'Une erreur est survenue.'}</Text>

                <View style={styles.actionButtonsCol}>
                  <TouchableOpacity
                    onPress={handlePickDocument}
                    activeOpacity={0.8}
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel="Réessayer">
                    <Text style={styles.primaryBtnText}>Réessayer avec un autre fichier</Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    onPress={handleClose}
                    activeOpacity={0.7}
                    style={styles.secondaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel="Annuler">
                    <Text style={styles.secondaryBtnText}>Annuler</Text>
                  </TouchableOpacity>
                </View>
              </View>
            )}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalOverlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
  },
  modalBackdrop: {
    ...StyleSheet.absoluteFill,
  },
  modalCard: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    maxHeight: '90%',
    paddingBottom: Platform.OS === 'ios' ? 34 : 20,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.12,
    shadowRadius: 16,
    elevation: 8,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.four,
    paddingBottom: Spacing.three,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(168, 155, 190, 0.15)',
  },
  headerTitleGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  headerIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  modalSubtitle: {
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
  },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.three,
  },
  stepContainer: {
    gap: 16,
  },
  dropZone: {
    borderWidth: 2,
    borderColor: 'rgba(128, 103, 161, 0.25)',
    borderStyle: 'dashed',
    borderRadius: 20,
    padding: Spacing.four,
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    gap: 10,
  },
  dropZoneIconCircle: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  dropZoneTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
  },
  dropZoneDesc: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 8,
  },
  primaryBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: 14,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryBtnText: {
    color: BRAND_COLORS.pureWhite,
    fontSize: 15,
    fontWeight: '700',
  },
  secondaryBtn: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 14,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  secondaryBtnText: {
    color: BRAND_COLORS.secondaryText,
    fontSize: 14,
    fontWeight: '600',
  },
  infoCallout: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 16,
    padding: Spacing.three,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    gap: 4,
  },
  infoCalloutTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  infoCalloutText: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    lineHeight: 17,
  },
  analyzingBox: {
    paddingVertical: 40,
    alignItems: 'center',
    gap: 14,
  },
  analyzingText: {
    fontSize: 15,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  analyzingFileName: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
  },
  previewContainer: {
    gap: 16,
  },
  fileSummaryCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 18,
    padding: Spacing.three + 2,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    gap: 12,
  },
  fileSummaryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  fileSummaryTextCol: {
    flex: 1,
  },
  fileSummaryName: {
    fontSize: 15,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  fileSummaryMeta: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  statsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 12,
    paddingVertical: 10,
  },
  statBox: {
    flex: 1,
    alignItems: 'center',
    gap: 2,
  },
  statNumber: {
    fontSize: 18,
    fontWeight: '800',
    color: BRAND_COLORS.deepLavender,
  },
  statLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: BRAND_COLORS.secondaryText,
  },
  statDivider: {
    width: 1,
    height: 24,
    backgroundColor: 'rgba(168, 155, 190, 0.2)',
  },
  errorCallout: {
    flexDirection: 'row',
    backgroundColor: '#FEF2F2',
    borderWidth: 1,
    borderColor: '#FECDD3',
    borderRadius: 16,
    padding: Spacing.three,
    gap: 10,
  },
  errorCalloutContent: {
    flex: 1,
    gap: 4,
  },
  errorCalloutTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#991B1B',
  },
  errorCalloutItem: {
    fontSize: 12,
    color: '#B91C1C',
  },
  sampleSection: {
    gap: 8,
  },
  sampleSectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: BRAND_COLORS.secondaryText,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  sampleList: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    overflow: 'hidden',
  },
  sampleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 0, 0, 0.04)',
  },
  sampleRowLeft: {
    flex: 1,
    paddingRight: 10,
    gap: 2,
  },
  sampleLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  sampleDate: {
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
  },
  sampleAmount: {
    fontSize: 14,
    fontWeight: '700',
  },
  expenseAmount: {
    color: BRAND_COLORS.darkCharcoal,
  },
  incomeAmount: {
    color: '#10B981',
  },
  actionButtonsCol: {
    gap: 8,
    marginTop: 4,
  },
  statusBox: {
    paddingVertical: 32,
    alignItems: 'center',
    gap: 12,
  },
  successIconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: 'rgba(16, 185, 129, 0.12)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  errorIconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: 'rgba(225, 29, 72, 0.12)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  statusTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
  },
  statusDesc: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    paddingHorizontal: 20,
  },
  errorDetailsText: {
    fontSize: 13,
    color: '#E11D48',
    textAlign: 'center',
    paddingHorizontal: 16,
  },
  successStatsCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 16,
    padding: Spacing.three,
    width: '100%',
    gap: 8,
    marginVertical: 8,
  },
  successStatRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  successStatLabel: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
  successStatValue: {
    fontSize: 14,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
});
