import React, { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import * as DocumentPicker from 'expo-document-picker';
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  FileSpreadsheet,
  FileText,
  FileX,
  RotateCcw,
  ShieldCheck,
  Upload,
  WifiOff,
  X,
} from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { FONT_FAMILY } from '@/constants/typography';
import { Spacing } from '@/constants/theme';
import {
  confirmCsvImport,
  CsvFilePayload,
  getOrCreateDefaultAccount,
  ImportIssue,
  ImportJobDetails,
  ImportPreviewResponse,
  previewCsvImport,
} from '@/services/import-api';
import { formatCurrencyMinor, formatDateDisplay } from '@/utils/formatters';
import { triggerHaptic } from '@/utils/haptics';
import {
  determinePreviewStep,
  isNetworkError,
  sanitizeErrorMessage,
} from './import-helpers';

export { determinePreviewStep, isNetworkError, sanitizeErrorMessage };

export interface ImportCsvModalProps {
  visible: boolean;
  onClose: () => void;
  onImportSuccess?: (job: ImportJobDetails) => void;
  locale?: string;
}

export type ImportModalStep =
  | 'PICK'
  | 'ANALYZING'
  | 'PREVIEW'
  | 'IMPORTING'
  | 'SUCCESS'
  | 'DUPLICATE_ONLY'
  | 'ERROR_NETWORK'
  | 'ERROR_INVALID_FILE'
  | 'ERROR_UNREADABLE'
  | 'ERROR_SERVER';

export function ImportCsvModal({
  visible,
  onClose,
  onImportSuccess,
  locale = 'fr-FR',
}: ImportCsvModalProps) {
  const insets = useSafeAreaInsets();
  const isReducedMotion = useReducedMotion();
  const isFrench = locale.toLowerCase().startsWith('fr');

  const [step, setStep] = useState<ImportModalStep>('PICK');
  const [selectedFile, setSelectedFile] = useState<CsvFilePayload | null>(null);
  const [previewData, setPreviewData] = useState<ImportPreviewResponse | null>(null);
  const [importResult, setImportResult] = useState<ImportJobDetails | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [issueList, setIssueList] = useState<ImportIssue[]>([]);
  const [accountId, setAccountId] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState<boolean>(false);

  const resetState = useCallback(() => {
    setStep('PICK');
    setSelectedFile(null);
    setPreviewData(null);
    setImportResult(null);
    setErrorMessage(null);
    setIssueList([]);
    setAccountId(null);
    setIsProcessing(false);
  }, []);

  const handleClose = () => {
    if (step === 'IMPORTING') return; // Prevent corrupting in-flight import
    resetState();
    onClose();
  };

  /**
   * Runs the two-stage analysis pipeline:
   * 1. Resolve or create user's primary bank account
   * 2. Request backend CSV preview & validation
   */
  const executeAnalysis = async (filePayload: CsvFilePayload) => {
    try {
      setIsProcessing(true);
      setStep('ANALYZING');
      setErrorMessage(null);
      setIssueList([]);

      // 1. Resolve or retrieve default account
      const accountRes = await getOrCreateDefaultAccount();
      if (!accountRes.success || !accountRes.data) {
        setIsProcessing(false);
        const err = accountRes.error || '';
        if (isNetworkError(err)) {
          setStep('ERROR_NETWORK');
          setErrorMessage(isFrench ? 'Impossible de joindre le serveur pour initialiser le compte.' : 'Unable to connect to server.');
        } else {
          setStep('ERROR_SERVER');
          setErrorMessage(sanitizeErrorMessage(err));
        }
        return;
      }

      const currentAccountId = accountRes.data.id;
      setAccountId(currentAccountId);

      // 2. Request backend preview and deduplication check
      const previewRes = await previewCsvImport(currentAccountId, filePayload);
      setIsProcessing(false);

      if (!previewRes.success || !previewRes.data) {
        const rawErr = previewRes.error || '';
        if (isNetworkError(rawErr)) {
          setStep('ERROR_NETWORK');
          setErrorMessage(
            isFrench
              ? 'Connexion interrompue lors de l’analyse du fichier.'
              : 'Connection interrupted during statement analysis.'
          );
        } else if (
          rawErr.toLowerCase().includes('impossible de lire') ||
          rawErr.toLowerCase().includes('bytes')
        ) {
          setStep('ERROR_UNREADABLE');
          setErrorMessage(
            isFrench
              ? 'Le fichier sélectionné est illisible ou inaccessible localement.'
              : 'The selected local file cannot be read.'
          );
        } else {
          setStep('ERROR_INVALID_FILE');
          setErrorMessage(sanitizeErrorMessage(rawErr));
        }
        return;
      }

      const data = previewRes.data;
      setPreviewData(data);

      const nextStep = determinePreviewStep(data);
      if (nextStep === 'ERROR_INVALID_FILE') {
        setStep('ERROR_INVALID_FILE');
        setIssueList(data.errors || []);
        setErrorMessage(
          isFrench
            ? 'Ce fichier comporte des anomalies bloquantes empêchant son intégration.'
            : 'Blocking validation issues prevent importing this statement.'
        );
        return;
      }

      if (nextStep === 'DUPLICATE_ONLY') {
        setStep('DUPLICATE_ONLY');
        void triggerHaptic.selection();
        return;
      }

      // Valid preview with new transactions to insert
      setStep('PREVIEW');
      void triggerHaptic.selection();
    } catch (err) {
      setIsProcessing(false);
      const msg = err instanceof Error ? err.message : '';
      if (isNetworkError(msg)) {
        setStep('ERROR_NETWORK');
        setErrorMessage(
          isFrench ? 'Connexion réseau indisponible.' : 'Network connection unavailable.'
        );
      } else {
        setStep('ERROR_SERVER');
        setErrorMessage(sanitizeErrorMessage(msg));
      }
    }
  };

  /**
   * Opens the native document picker.
   */
  const handlePickDocument = async () => {
    try {
      setErrorMessage(null);
      setIssueList([]);

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
        fileObj: (asset as { file?: File | Blob }).file,
      };

      setSelectedFile(payload);
      await executeAnalysis(payload);
    } catch {
      setStep('ERROR_UNREADABLE');
      setErrorMessage(
        isFrench
          ? 'Impossible d’accéder au document sélectionné.'
          : 'Unable to access the chosen file.'
      );
    }
  };

  /**
   * Executes atomic database confirmation with the validated file hash.
   */
  const handleConfirmImport = async () => {
    if (!accountId || !selectedFile || !previewData || isProcessing) {
      return;
    }

    try {
      setIsProcessing(true);
      setStep('IMPORTING');
      setErrorMessage(null);

      const mappingToApply = previewData.suggestedMapping || previewData.appliedMapping;
      if (!mappingToApply) {
        setIsProcessing(false);
        setStep('ERROR_INVALID_FILE');
        setErrorMessage(
          isFrench
            ? 'Format de colonnes bancaires non reconnu.'
            : 'Bank column structure not recognized.'
        );
        return;
      }

      const chosenCharset = previewData.charsetAmbiguous
        ? previewData.detectedCharset || previewData.charsetCandidates?.[0] || 'WINDOWS_1252'
        : previewData.detectedCharset;

      const res = await confirmCsvImport({
        accountId,
        file: selectedFile,
        fileHash: previewData.fileHash,
        mapping: mappingToApply,
        charset: chosenCharset,
      });

      setIsProcessing(false);

      if (!res.success || !res.data) {
        const rawErr = res.error || '';
        if (isNetworkError(rawErr)) {
          setStep('ERROR_NETWORK');
          setErrorMessage(
            isFrench
              ? 'Connexion perdue pendant l’enregistrement.'
              : 'Connection lost during ledger commit.'
          );
        } else {
          setStep('ERROR_SERVER');
          setErrorMessage(sanitizeErrorMessage(rawErr));
        }
        return;
      }

      const job = res.data.importJob;
      setImportResult(job);
      setStep('SUCCESS');
      void triggerHaptic.success();
      onImportSuccess?.(job);
    } catch (err) {
      setIsProcessing(false);
      const msg = err instanceof Error ? err.message : '';
      if (isNetworkError(msg)) {
        setStep('ERROR_NETWORK');
        setErrorMessage(
          isFrench
            ? 'Connexion réseau indisponible pendant l’import.'
            : 'Network connection lost during import.'
        );
      } else {
        setStep('ERROR_SERVER');
        setErrorMessage(sanitizeErrorMessage(msg));
      }
    }
  };

  /**
   * Safe retry handler that re-runs the last operation on the existing file
   * without forcing a document picker re-open.
   */
  const handleRetry = async () => {
    if (!selectedFile) {
      void handlePickDocument();
      return;
    }

    if (previewData && previewData.fileHash && accountId) {
      // Retry confirmation
      await handleConfirmImport();
    } else {
      // Retry analysis
      await executeAnalysis(selectedFile);
    }
  };

  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={handleClose}>
      <View style={styles.modalOverlay}>
        {/* Backdrop: dismissable unless actively processing an import */}
        <AnimatedPressable
          style={styles.modalBackdrop}
          onPress={step !== 'IMPORTING' && step !== 'ANALYZING' ? handleClose : undefined}
          accessibilityLabel="Fermer la fenêtre modale"
          accessibilityRole="button"
          scaleTarget={1.0}
        />

        <View
          style={[
            styles.modalCard,
            { paddingBottom: Math.max(insets.bottom, 20) + 10 },
          ]}>
          {/* Sheet Handle */}
          <View style={styles.handle} />

          {/* Modal Header */}
          <View style={styles.modalHeader}>
            <View style={styles.headerTitleGroup}>
              <View style={styles.headerIconCircle}>
                <FileSpreadsheet
                  size={19}
                  color={BRAND_COLORS.deepLavender}
                  strokeWidth={2.2}
                />
              </View>
              <View>
                <Text style={styles.modalTitle}>
                  {isFrench ? 'Importer un relevé' : 'Import Bank Statement'}
                </Text>
                <Text style={styles.modalSubtitle}>
                  {isFrench ? 'Formats bancaires standards' : 'Standard bank statement formats'}
                </Text>
              </View>
            </View>

            <AnimatedPressable
              onPress={handleClose}
              scaleTarget={scalePress}
              disabled={isProcessing || step === 'IMPORTING'}
              style={[
                styles.closeBtn,
                (isProcessing || step === 'IMPORTING') && styles.disabledBtn,
              ]}
              accessibilityLabel={isFrench ? 'Fermer' : 'Close'}
              accessibilityRole="button">
              <X size={15} color={BRAND_COLORS.darkCharcoal} strokeWidth={2.2} />
            </AnimatedPressable>
          </View>

          <ScrollView
            showsVerticalScrollIndicator={false}
            bounces={false}
            contentContainerStyle={styles.scrollContent}>
            {/* =========================================================================
                STATE 1: FILE SELECTION (PICK)
               ========================================================================= */}
            {step === 'PICK' && (
              <View style={styles.stateWrapper}>
                <View style={styles.editorialPickCard}>
                  <View style={styles.pickIconBadge}>
                    <Upload size={24} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                  </View>
                  <Text style={styles.pickCardTitle}>
                    {isFrench ? 'Sélectionnez votre relevé bancaire' : 'Select your bank statement'}
                  </Text>
                  <Text style={styles.pickCardDescription}>
                    {isFrench
                      ? 'Relevé au format CSV ou TSV (jusqu’à 5 Mo). Vos écritures seront analysées avec détection automatique des colonnes.'
                      : 'CSV or TSV statement (up to 5 MB). Columns and dates will be validated automatically.'}
                  </Text>

                  {/* Format chips */}
                  <View style={styles.formatChipRow}>
                    <View style={styles.formatChip}>
                      <Text style={styles.formatChipText}>.CSV</Text>
                    </View>
                    <View style={styles.formatChip}>
                      <Text style={styles.formatChipText}>.TSV</Text>
                    </View>
                    <View style={styles.formatChip}>
                      <Text style={styles.formatChipText}>UTF-8 / ISO</Text>
                    </View>
                    <View style={styles.formatChip}>
                      <Text style={styles.formatChipText}>Max 5 Mo</Text>
                    </View>
                  </View>

                  <AnimatedPressable
                    onPress={handlePickDocument}
                    scaleTarget={scaleAction}
                    haptic="light"
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={isFrench ? 'Choisir un fichier' : 'Select a file'}>
                    <Text style={styles.primaryBtnText}>
                      {isFrench ? 'Choisir un fichier' : 'Select statement file'}
                    </Text>
                  </AnimatedPressable>
                </View>

                {/* Privacy & Anti-Duplicate Footnote */}
                <View style={styles.securityPill}>
                  <ShieldCheck size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                  <Text style={styles.securityPillText}>
                    {isFrench
                      ? 'Protection anti-doublon active • Aucune écriture dupliquée'
                      : 'Active duplicate protection • No duplicated records'}
                  </Text>
                </View>
              </View>
            )}

            {/* =========================================================================
                STATE 2: ANALYSIS & PARSING (ANALYZING)
               ========================================================================= */}
            {step === 'ANALYZING' && (
              <View style={styles.statusStateBox}>
                <View style={styles.spinnerCircle}>
                  <ActivityIndicator size="small" color={BRAND_COLORS.deepLavender} />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench ? 'Analyse du relevé...' : 'Analyzing statement...'}
                </Text>
                <Text style={styles.statusDesc}>
                  {isFrench
                    ? 'Détection de l’encodage, des colonnes et contrôle des doublons.'
                    : 'Detecting character encoding, column mapping, and duplicate records.'}
                </Text>
                {selectedFile && (
                  <View style={styles.fileCapsule}>
                    <FileText size={13} color={BRAND_COLORS.deepLavender} strokeWidth={2} />
                    <Text style={styles.fileCapsuleName} numberOfLines={1} ellipsizeMode="middle">
                      {selectedFile.name}
                    </Text>
                  </View>
                )}
              </View>
            )}

            {/* =========================================================================
                STATE 3: PREVIEW (PREVIEW with wouldInsert > 0)
               ========================================================================= */}
            {step === 'PREVIEW' && previewData && (
              <View style={styles.stateWrapper}>
                {/* File summary pill */}
                <View style={styles.documentSummaryPill}>
                  <View style={styles.docPillLeft}>
                    <FileText size={17} color={BRAND_COLORS.deepLavender} strokeWidth={2} />
                    <View style={styles.docPillTextCol}>
                      <Text style={styles.docPillName} numberOfLines={1} ellipsizeMode="middle">
                        {selectedFile?.name || 'releve.csv'}
                      </Text>
                      <Text style={styles.docPillMeta}>
                        {previewData.totalRows} {isFrench ? 'écritures détectées' : 'rows detected'} •{' '}
                        {previewData.detectedCharset || 'UTF-8'}
                      </Text>
                    </View>
                  </View>
                </View>

                {/* Ledger metrics overview card */}
                <View style={styles.ledgerMetricsCard}>
                  <View style={styles.ledgerMetricBox}>
                    <Text style={styles.ledgerMetricNumber}>+{previewData.wouldInsert}</Text>
                    <Text style={styles.ledgerMetricLabel}>
                      {isFrench ? 'À intégrer au journal' : 'To ledger'}
                    </Text>
                  </View>
                  <View style={styles.ledgerMetricDivider} />
                  <View style={styles.ledgerMetricBox}>
                    <Text style={[styles.ledgerMetricNumber, { color: BRAND_COLORS.secondaryText }]}>
                      {previewData.wouldSkip}
                    </Text>
                    <Text style={styles.ledgerMetricLabel}>
                      {isFrench ? 'Doublons ignorés' : 'Duplicates skipped'}
                    </Text>
                  </View>
                </View>

                {/* Non-blocking warnings banner if present */}
                {previewData.warnings && previewData.warnings.length > 0 && (
                  <View style={styles.warningBanner}>
                    <AlertTriangle size={15} color="#B45309" strokeWidth={2.2} />
                    <Text style={styles.warningBannerText}>
                      {isFrench
                        ? `${previewData.warnings.length} remarque(s) mineure(s) non bloquante(s) sur le format.`
                        : `${previewData.warnings.length} non-blocking format notice(s).`}
                    </Text>
                  </View>
                )}

                {/* 3 Sample Transactions Preview */}
                {previewData.sample && previewData.sample.length > 0 && (
                  <View style={styles.sampleContainer}>
                    <View style={styles.sampleSectionHeader}>
                      <Text style={styles.sampleSectionTitle}>
                        {isFrench ? 'Extrait des opérations détectées' : 'Sample detected records'}
                      </Text>
                      <Text style={styles.sampleSectionCaption}>
                        {isFrench ? '3 premières lignes' : 'First 3 lines'}
                      </Text>
                    </View>

                    <View style={styles.sampleListCard}>
                      {previewData.sample.slice(0, 3).map((item, idx) => {
                        const isIncome = item.amountMinor > 0;
                        return (
                          <View
                            key={idx}
                            style={[
                              styles.sampleItemRow,
                              idx === previewData.sample!.slice(0, 3).length - 1 && styles.lastSampleItemRow,
                            ]}>
                            <View style={styles.sampleRowLeft}>
                              <Text style={styles.sampleItemLabel} numberOfLines={1}>
                                {item.label}
                              </Text>
                              <Text style={styles.sampleItemDate}>
                                {formatDateDisplay(item.bookingDate)}
                              </Text>
                            </View>
                            <Text
                              style={[
                                styles.sampleItemAmount,
                                isIncome ? styles.sampleIncomeAmount : styles.sampleExpenseAmount,
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

                {/* Actions */}
                <View style={styles.actionCol}>
                  <AnimatedPressable
                    onPress={handleConfirmImport}
                    scaleTarget={scaleAction}
                    haptic="light"
                    disabled={isProcessing}
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={
                      isFrench
                        ? `Confirmer l'import de ${previewData.wouldInsert} opérations`
                        : `Confirm import of ${previewData.wouldInsert} transactions`
                    }>
                    <Text style={styles.primaryBtnText}>
                      {isFrench
                        ? `Confirmer l’import (+${previewData.wouldInsert} opération${previewData.wouldInsert > 1 ? 's' : ''})`
                        : `Confirm import (+${previewData.wouldInsert} transaction${previewData.wouldInsert > 1 ? 's' : ''})`}
                    </Text>
                  </AnimatedPressable>

                  <AnimatedPressable
                    onPress={handlePickDocument}
                    scaleTarget={scalePress}
                    haptic="light"
                    disabled={isProcessing}
                    style={styles.secondaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={
                      isFrench ? 'Choisir un autre fichier' : 'Select another file'
                    }>
                    <Text style={styles.secondaryBtnText}>
                      {isFrench ? 'Choisir un autre fichier' : 'Select another file'}
                    </Text>
                  </AnimatedPressable>
                </View>
              </View>
            )}

            {/* =========================================================================
                STATE 4: IMPORT IN PROGRESS (IMPORTING)
               ========================================================================= */}
            {step === 'IMPORTING' && (
              <View style={styles.statusStateBox}>
                <View style={styles.spinnerCircle}>
                  <ActivityIndicator size="small" color={BRAND_COLORS.deepLavender} />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench ? 'Intégration au journal...' : 'Ledgering transactions...'}
                </Text>
                <Text style={styles.statusDesc}>
                  {isFrench
                    ? 'Enregistrement sécurisé des opérations et mise à jour de vos comptes.'
                    : 'Securely committing entries and refreshing account balance.'}
                </Text>
              </View>
            )}

            {/* =========================================================================
                STATE 5: SUCCESSFUL IMPORT (SUCCESS)
               ========================================================================= */}
            {step === 'SUCCESS' && importResult && (
              <View style={styles.statusStateBox}>
                <View style={styles.successIconCircle}>
                  <CheckCircle2
                    size={36}
                    color={BRAND_COLORS.mutedMintGreen}
                    strokeWidth={2.4}
                  />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench ? 'Importation terminée' : 'Import completed'}
                </Text>
                <Text style={styles.statusDesc}>
                  {isFrench
                    ? 'Vos transactions ont été intégrées avec succès à votre journal.'
                    : 'Your bank transactions have been ledgered successfully.'}
                </Text>

                {/* Success metrics */}
                <View style={styles.resultDetailsCard}>
                  <View style={styles.resultDetailRow}>
                    <Text style={styles.resultDetailLabel}>
                      {isFrench ? 'Opérations ajoutées' : 'Transactions added'}
                    </Text>
                    <Text style={styles.resultDetailValueAdded}>
                      +{importResult.insertedRows}
                    </Text>
                  </View>

                  {importResult.skippedRows > 0 && (
                    <View style={styles.resultDetailRow}>
                      <Text style={styles.resultDetailLabel}>
                        {isFrench ? 'Doublons ignorés' : 'Duplicates skipped'}
                      </Text>
                      <Text style={styles.resultDetailValueMuted}>
                        {importResult.skippedRows}
                      </Text>
                    </View>
                  )}

                  <View style={styles.resultDetailRow}>
                    <Text style={styles.resultDetailLabel}>
                      {isFrench ? 'Compte destination' : 'Destination account'}
                    </Text>
                    <Text style={styles.resultDetailValueMuted}>
                      {isFrench ? 'Compte principal' : 'Primary account'}
                    </Text>
                  </View>
                </View>

                <AnimatedPressable
                  onPress={handleClose}
                  scaleTarget={scaleAction}
                  haptic="light"
                  style={styles.primaryBtn}
                  accessibilityRole="button"
                  accessibilityLabel={
                    isFrench ? 'Consulter mon journal' : 'View my journal'
                  }>
                  <Text style={styles.primaryBtnText}>
                    {isFrench ? 'Consulter mon journal' : 'View my journal'}
                  </Text>
                </AnimatedPressable>
              </View>
            )}

            {/* =========================================================================
                STATE 6: DUPLICATE-ONLY OUTCOME (DUPLICATE_ONLY when wouldInsert === 0)
               ========================================================================= */}
            {step === 'DUPLICATE_ONLY' && (
              <View style={styles.statusStateBox}>
                <View style={styles.shieldIconCircle}>
                  <ShieldCheck
                    size={36}
                    color={BRAND_COLORS.deepLavender}
                    strokeWidth={2.2}
                  />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench
                    ? 'Toutes les opérations sont déjà importées'
                    : 'All transactions already imported'}
                </Text>
                <Text style={styles.statusDesc}>
                  {isFrench
                    ? `Les ${previewData?.wouldSkip || previewData?.totalRows || 0} opérations détectées dans ce fichier figurent déjà dans votre journal. Aucune nouvelle écriture n’a été ajoutée pour éviter les doublons.`
                    : `All ${previewData?.wouldSkip || previewData?.totalRows || 0} transactions in this statement already exist in your journal. No duplicate records were added.`}
                </Text>

                {/* Anti-duplicate stats badge */}
                <View style={styles.duplicateStatsPill}>
                  <View style={styles.duplicateStatItem}>
                    <Text style={styles.duplicateStatNumber}>0</Text>
                    <Text style={styles.duplicateStatLabel}>
                      {isFrench ? 'À ajouter' : 'To add'}
                    </Text>
                  </View>
                  <View style={styles.ledgerMetricDivider} />
                  <View style={styles.duplicateStatItem}>
                    <Text style={[styles.duplicateStatNumber, { color: BRAND_COLORS.deepLavender }]}>
                      {previewData?.wouldSkip || previewData?.totalRows || 0}
                    </Text>
                    <Text style={styles.duplicateStatLabel}>
                      {isFrench ? 'Doublons évités' : 'Duplicates avoided'}
                    </Text>
                  </View>
                </View>

                <View style={styles.actionCol}>
                  <AnimatedPressable
                    onPress={handleClose}
                    scaleTarget={scaleAction}
                    haptic="light"
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={isFrench ? 'Compris' : 'Understood'}>
                    <Text style={styles.primaryBtnText}>
                      {isFrench ? 'Compris' : 'Understood'}
                    </Text>
                  </AnimatedPressable>

                  <AnimatedPressable
                    onPress={handlePickDocument}
                    scaleTarget={scalePress}
                    haptic="light"
                    style={styles.secondaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={
                      isFrench ? 'Choisir un autre fichier' : 'Select another file'
                    }>
                    <Text style={styles.secondaryBtnText}>
                      {isFrench ? 'Choisir un autre fichier' : 'Select another file'}
                    </Text>
                  </AnimatedPressable>
                </View>
              </View>
            )}

            {/* =========================================================================
                STATE 7: NETWORK INTERRUPTION (ERROR_NETWORK)
               ========================================================================= */}
            {step === 'ERROR_NETWORK' && (
              <View style={styles.statusStateBox}>
                <View style={styles.warningIconCircle}>
                  <WifiOff size={34} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench ? 'Connexion interrompue' : 'Connection interrupted'}
                </Text>
                <Text style={styles.statusDesc}>
                  {errorMessage ||
                    (isFrench
                      ? 'Impossible de joindre le serveur. Vérifiez votre connexion internet.'
                      : 'Unable to reach the server. Please check your internet connection.')}
                </Text>

                <View style={styles.actionCol}>
                  <AnimatedPressable
                    onPress={handleRetry}
                    scaleTarget={scaleAction}
                    haptic="light"
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={isFrench ? 'Réessayer' : 'Retry'}>
                    <View style={styles.btnRow}>
                      <RotateCcw size={15} color={BRAND_COLORS.pureWhite} strokeWidth={2.2} />
                      <Text style={styles.primaryBtnText}>
                        {isFrench ? 'Réessayer' : 'Retry'}
                      </Text>
                    </View>
                  </AnimatedPressable>

                  <AnimatedPressable
                    onPress={handleClose}
                    scaleTarget={scalePress}
                    haptic="light"
                    style={styles.secondaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={isFrench ? 'Annuler' : 'Cancel'}>
                    <Text style={styles.secondaryBtnText}>
                      {isFrench ? 'Annuler' : 'Cancel'}
                    </Text>
                  </AnimatedPressable>
                </View>
              </View>
            )}

            {/* =========================================================================
                STATE 8A: INVALID FILE FORMAT / BLOCKING ERRORS (ERROR_INVALID_FILE)
               ========================================================================= */}
            {step === 'ERROR_INVALID_FILE' && (
              <View style={styles.statusStateBox}>
                <View style={styles.errorIconCircle}>
                  <FileX size={34} color="#DC2626" strokeWidth={2.2} />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench ? 'Fichier non reconnu' : 'Unrecognized file'}
                </Text>
                <Text style={styles.statusDesc}>
                  {errorMessage ||
                    (isFrench
                      ? 'Le fichier CSV ne contient pas les colonnes attendues (Date, Libellé, Montant) ou présente un encodage non supporté.'
                      : 'The CSV statement is missing required columns (Date, Label, Amount) or uses an unsupported format.')}
                </Text>

                {/* Issue details list if provided by backend */}
                {issueList.length > 0 && (
                  <View style={styles.issueDetailsCard}>
                    <Text style={styles.issueDetailsHeading}>
                      {isFrench ? 'Détails des anomalies' : 'Validation issues'}
                    </Text>
                    {issueList.slice(0, 3).map((issue, idx) => (
                      <Text key={idx} style={styles.issueItemText}>
                        • {isFrench ? `Ligne ${issue.line}` : `Row ${issue.line}`} : {issue.message}
                      </Text>
                    ))}
                  </View>
                )}

                <View style={styles.actionCol}>
                  <AnimatedPressable
                    onPress={handlePickDocument}
                    scaleTarget={scaleAction}
                    haptic="light"
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={
                      isFrench ? 'Choisir un autre fichier' : 'Select another file'
                    }>
                    <Text style={styles.primaryBtnText}>
                      {isFrench ? 'Choisir un autre fichier' : 'Select another file'}
                    </Text>
                  </AnimatedPressable>

                  <AnimatedPressable
                    onPress={handleClose}
                    scaleTarget={scalePress}
                    haptic="light"
                    style={styles.secondaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={isFrench ? 'Annuler' : 'Cancel'}>
                    <Text style={styles.secondaryBtnText}>
                      {isFrench ? 'Annuler' : 'Cancel'}
                    </Text>
                  </AnimatedPressable>
                </View>
              </View>
            )}

            {/* =========================================================================
                STATE 8B: UNREADABLE LOCAL FILE (ERROR_UNREADABLE)
               ========================================================================= */}
            {step === 'ERROR_UNREADABLE' && (
              <View style={styles.statusStateBox}>
                <View style={styles.errorIconCircle}>
                  <AlertCircle size={34} color="#DC2626" strokeWidth={2.2} />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench ? 'Lecture du fichier impossible' : 'Cannot read file'}
                </Text>
                <Text style={styles.statusDesc}>
                  {errorMessage ||
                    (isFrench
                      ? 'Le système n’a pas pu accéder au fichier local sélectionné. Veuillez sélectionner à nouveau votre export.'
                      : 'The local file could not be accessed. Please select the file again.')}
                </Text>

                <View style={styles.actionCol}>
                  <AnimatedPressable
                    onPress={handlePickDocument}
                    scaleTarget={scaleAction}
                    haptic="light"
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={
                      isFrench ? 'Sélectionner à nouveau' : 'Select again'
                    }>
                    <Text style={styles.primaryBtnText}>
                      {isFrench ? 'Sélectionner à nouveau' : 'Select again'}
                    </Text>
                  </AnimatedPressable>

                  <AnimatedPressable
                    onPress={handleClose}
                    scaleTarget={scalePress}
                    haptic="light"
                    style={styles.secondaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={isFrench ? 'Annuler' : 'Cancel'}>
                    <Text style={styles.secondaryBtnText}>
                      {isFrench ? 'Annuler' : 'Cancel'}
                    </Text>
                  </AnimatedPressable>
                </View>
              </View>
            )}

            {/* =========================================================================
                STATE 8C: SERVER EXECUTION ERROR (ERROR_SERVER)
               ========================================================================= */}
            {step === 'ERROR_SERVER' && (
              <View style={styles.statusStateBox}>
                <View style={styles.errorIconCircle}>
                  <AlertCircle size={34} color="#DC2626" strokeWidth={2.2} />
                </View>
                <Text style={styles.statusTitle}>
                  {isFrench ? 'Traitement interrompu' : 'Processing interrupted'}
                </Text>
                <Text style={styles.statusDesc}>
                  {errorMessage ||
                    (isFrench
                      ? 'Une erreur est survenue lors de l’enregistrement. Aucun doublon n’a été sauvegardé.'
                      : 'An error occurred during ledger commit. No duplicate records were saved.')}
                </Text>

                <View style={styles.actionCol}>
                  <AnimatedPressable
                    onPress={handleRetry}
                    scaleTarget={scaleAction}
                    haptic="light"
                    style={styles.primaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={isFrench ? 'Réessayer' : 'Retry'}>
                    <View style={styles.btnRow}>
                      <RotateCcw size={15} color={BRAND_COLORS.pureWhite} strokeWidth={2.2} />
                      <Text style={styles.primaryBtnText}>
                        {isFrench ? 'Réessayer' : 'Retry'}
                      </Text>
                    </View>
                  </AnimatedPressable>

                  <AnimatedPressable
                    onPress={handlePickDocument}
                    scaleTarget={scalePress}
                    haptic="light"
                    style={styles.secondaryBtn}
                    accessibilityRole="button"
                    accessibilityLabel={
                      isFrench ? 'Choisir un autre fichier' : 'Select another file'
                    }>
                    <Text style={styles.secondaryBtnText}>
                      {isFrench ? 'Choisir un autre fichier' : 'Select another file'}
                    </Text>
                  </AnimatedPressable>
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
    backgroundColor: 'rgba(36, 37, 53, 0.45)',
  },
  modalBackdrop: {
    ...StyleSheet.absoluteFill,
  },
  modalCard: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    maxHeight: '90%',
    paddingTop: 10,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: -6 },
    shadowOpacity: 0.12,
    shadowRadius: 20,
    elevation: 10,
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(36, 37, 53, 0.15)',
    alignSelf: 'center',
    marginBottom: 8,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.three,
    paddingBottom: Spacing.three,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(168, 155, 190, 0.15)',
  },
  headerTitleGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flex: 1,
  },
  headerIconCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 16.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  modalSubtitle: {
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
  disabledBtn: {
    opacity: 0.5,
  },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.four,
    paddingBottom: Spacing.three,
  },
  stateWrapper: {
    gap: 14,
  },
  editorialPickCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 20,
    padding: Spacing.four,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    gap: 10,
  },
  pickIconBadge: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 2,
  },
  pickCardTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 16,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    letterSpacing: -0.2,
  },
  pickCardDescription: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    paddingHorizontal: 8,
  },
  formatChipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    justifyContent: 'center',
    marginVertical: 4,
  },
  formatChip: {
    backgroundColor: BRAND_COLORS.pureWhite,
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
  },
  formatChipText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11,
    color: BRAND_COLORS.darkCharcoal,
  },
  securityPill: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 6,
  },
  securityPillText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
  },
  statusStateBox: {
    paddingVertical: 28,
    alignItems: 'center',
    gap: 12,
  },
  spinnerCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  statusTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    letterSpacing: -0.2,
    paddingHorizontal: 16,
  },
  statusDesc: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
    paddingHorizontal: 20,
  },
  fileCapsule: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    marginTop: 4,
    maxWidth: '90%',
  },
  fileCapsuleName: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 12,
    color: BRAND_COLORS.darkCharcoal,
  },
  documentSummaryPill: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
  },
  docPillLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    flex: 1,
  },
  docPillTextCol: {
    flex: 1,
  },
  docPillName: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  docPillMeta: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
  },
  ledgerMetricsCard: {
    flexDirection: 'row',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 16,
    paddingVertical: 14,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
  },
  ledgerMetricBox: {
    flex: 1,
    alignItems: 'center',
    gap: 2,
  },
  ledgerMetricNumber: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 22,
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
    letterSpacing: -0.3,
  },
  ledgerMetricLabel: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
  },
  ledgerMetricDivider: {
    width: 1,
    height: 32,
    backgroundColor: 'rgba(168, 155, 190, 0.22)',
    alignSelf: 'center',
  },
  warningBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#FFFBEB',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#FDE68A',
  },
  warningBannerText: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    color: '#92400E',
    flex: 1,
  },
  sampleContainer: {
    gap: 6,
  },
  sampleSectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    paddingHorizontal: 2,
  },
  sampleSectionTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 12,
    fontWeight: '700',
    color: BRAND_COLORS.secondaryText,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  sampleSectionCaption: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
  },
  sampleListCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    overflow: 'hidden',
  },
  sampleItemRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 9,
    paddingHorizontal: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 0, 0, 0.04)',
  },
  lastSampleItemRow: {
    borderBottomWidth: 0,
  },
  sampleRowLeft: {
    flex: 1,
    paddingRight: 10,
    gap: 1,
  },
  sampleItemLabel: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 13,
    color: BRAND_COLORS.darkCharcoal,
  },
  sampleItemDate: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
  },
  sampleItemAmount: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13.5,
    fontWeight: '700',
  },
  sampleExpenseAmount: {
    color: BRAND_COLORS.darkCharcoal,
  },
  sampleIncomeAmount: {
    color: BRAND_COLORS.mutedMintGreen,
  },
  actionCol: {
    gap: 8,
    marginTop: 4,
    width: '100%',
  },
  primaryBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 13,
    paddingHorizontal: 18,
    borderRadius: 14,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryBtnText: {
    fontFamily: FONT_FAMILY.bold,
    color: BRAND_COLORS.pureWhite,
    fontSize: 14.5,
    fontWeight: '700',
    letterSpacing: -0.1,
  },
  secondaryBtn: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 14,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.22)',
  },
  secondaryBtnText: {
    fontFamily: FONT_FAMILY.semiBold,
    color: BRAND_COLORS.secondaryText,
    fontSize: 13.5,
    fontWeight: '600',
  },
  btnRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
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
  shieldIconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: 'rgba(128, 103, 161, 0.12)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  warningIconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  errorIconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: 'rgba(220, 38, 38, 0.10)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 4,
  },
  resultDetailsCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 12,
    width: '100%',
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    gap: 8,
    marginVertical: 4,
  },
  resultDetailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  resultDetailLabel: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
  },
  resultDetailValueAdded: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 14,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  resultDetailValueMuted: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 13,
    color: BRAND_COLORS.darkCharcoal,
  },
  duplicateStatsPill: {
    flexDirection: 'row',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 14,
    paddingVertical: 12,
    paddingHorizontal: 20,
    width: '100%',
    borderWidth: 1,
    borderColor: BRAND_COLORS.cardBorder,
    marginVertical: 4,
  },
  duplicateStatItem: {
    flex: 1,
    alignItems: 'center',
    gap: 2,
  },
  duplicateStatNumber: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 20,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  duplicateStatLabel: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
  },
  issueDetailsCard: {
    backgroundColor: '#FEF2F2',
    borderRadius: 12,
    padding: 12,
    width: '100%',
    borderWidth: 1,
    borderColor: '#FECDD3',
    gap: 4,
  },
  issueDetailsHeading: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 12,
    fontWeight: '700',
    color: '#991B1B',
    marginBottom: 2,
  },
  issueItemText: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: '#B91C1C',
    lineHeight: 16,
  },
});
