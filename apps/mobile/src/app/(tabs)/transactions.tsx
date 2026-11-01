import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Modal,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';

import {
  Calendar,
  Check,
  CheckCircle2,
  FileText,
  Layers,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Sparkles,
  Tag,
  Upload,
  X,
} from 'lucide-react-native';

import { ImportCsvModal } from '@/components/transactions/import-csv-modal';
import { getCategoryTheme } from '@/constants/category-theme';
import { BRAND_COLORS } from '@/constants/colors';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { fetchRecentTransactions, TransactionItem } from '@/services/dashboard-api';
import { formatCurrencyMinor, formatDateDisplay } from '@/utils/formatters';

export type DurationFilter = 'ALL' | '30_DAYS' | '3_MONTHS' | 'THIS_YEAR';

export const DURATION_OPTIONS: { id: DurationFilter; label: string; shortLabel: string }[] = [
  { id: 'ALL', label: 'Toutes les dates', shortLabel: 'Toutes' },
  { id: '30_DAYS', label: '30 derniers jours', shortLabel: '30 jours' },
  { id: '3_MONTHS', label: '3 derniers mois', shortLabel: '3 mois' },
  { id: 'THIS_YEAR', label: 'Cette année', shortLabel: 'Cette année' },
];

function isDateInDuration(bookingDateStr: string | null | undefined, duration: DurationFilter): boolean {
  if (duration === 'ALL') return true;
  if (!bookingDateStr) return false;

  const date = new Date(bookingDateStr.length === 10 ? `${bookingDateStr}T00:00:00` : bookingDateStr);
  if (isNaN(date.getTime())) return true;

  const now = new Date();
  const endOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);

  switch (duration) {
    case '30_DAYS': {
      const thirtyDaysAgo = new Date();
      thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
      thirtyDaysAgo.setHours(0, 0, 0, 0);
      return date >= thirtyDaysAgo && date <= endOfToday;
    }
    case '3_MONTHS': {
      const ninetyDaysAgo = new Date();
      ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90);
      ninetyDaysAgo.setHours(0, 0, 0, 0);
      return date >= ninetyDaysAgo && date <= endOfToday;
    }
    case 'THIS_YEAR': {
      return date.getFullYear() === now.getFullYear();
    }
    default:
      return true;
  }
}

export default function TransactionsScreen() {
  const insets = useSafeAreaInsets();
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isImportModalVisible, setIsImportModalVisible] = useState<boolean>(false);
  const [isCategoryModalVisible, setIsCategoryModalVisible] = useState<boolean>(false);
  const [selectedDuration, setSelectedDuration] = useState<DurationFilter>('ALL');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionItem | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');

  const loadTransactions = useCallback(async (refresh = false) => {
    if (refresh) setIsRefreshing(true);
    else setIsLoading(true);

    const res = await fetchRecentTransactions(100);
    if (res.success && res.data) {
      setTransactions(res.data);
    }

    if (refresh) setIsRefreshing(false);
    else setIsLoading(false);
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadTransactions();
  }, [loadTransactions]);

  // Transactions filtered by selected duration
  const durationFilteredTransactions = useMemo(() => {
    return transactions.filter((tx) => isDateInDuration(tx.bookingDate, selectedDuration));
  }, [transactions, selectedDuration]);

  // Compute categories and counts based on duration (Abonnements always displayed first)
  const categoryCounts = useMemo(() => {
    const counts = new Map<string, number>();

    durationFilteredTransactions.forEach((tx) => {
      const cat = tx.categoryName || 'Non catégorisé';
      counts.set(cat, (counts.get(cat) || 0) + 1);
    });

    if (!counts.has('Abonnements')) {
      counts.set('Abonnements', 0);
    }

    return Array.from(counts.entries())
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => {
        const isAbonnementA = a.name.toLowerCase().includes('abonnement');
        const isAbonnementB = b.name.toLowerCase().includes('abonnement');
        if (isAbonnementA && !isAbonnementB) return -1;
        if (!isAbonnementA && isAbonnementB) return 1;
        return b.count - a.count;
      });
  }, [durationFilteredTransactions]);

  const effectiveSelectedCategory =
    selectedCategory && categoryCounts.some((c) => c.name === selectedCategory)
      ? selectedCategory
      : null;

  const isFilterActive = effectiveSelectedCategory !== null || selectedDuration !== 'ALL';

  const filteredTransactions = useMemo(() => {
    return durationFilteredTransactions.filter((tx) => {
      // Category filter
      if (effectiveSelectedCategory) {
        const itemCat = tx.categoryName || 'Non catégorisé';
        if (itemCat !== effectiveSelectedCategory) {
          return false;
        }
      }

      // Search filter (merchants, notes, amounts, categories)
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const label = (tx.normalizedLabel || tx.rawLabel || '').toLowerCase();
        const raw = (tx.rawLabel || '').toLowerCase();
        const category = (tx.categoryName || '').toLowerCase();
        const majorAmount = (Math.abs(tx.amountMinor) / 100).toFixed(2);
        const integerAmount = Math.floor(Math.abs(tx.amountMinor) / 100).toString();
        const normalizedAmount = majorAmount.replace('.', ',');

        return (
          label.includes(q) ||
          raw.includes(q) ||
          category.includes(q) ||
          majorAmount.includes(q) ||
          normalizedAmount.includes(q) ||
          integerAmount === q
        );
      }

      return true;
    });
  }, [durationFilteredTransactions, effectiveSelectedCategory, searchQuery]);

  // Summary banner for active filters (Category and/or Duration)
  const activeFilterSummary = useMemo(() => {
    if (!isFilterActive) return null;

    let totalMinor = 0;
    filteredTransactions.forEach((tx) => {
      totalMinor += tx.amountMinor;
    });

    const durationObj = DURATION_OPTIONS.find((d) => d.id === selectedDuration);
    const durationLabel = durationObj && selectedDuration !== "ALL" ? durationObj.shortLabel : null;

    if (effectiveSelectedCategory) {
      const theme = getCategoryTheme(
        effectiveSelectedCategory === "Non catégorisé" ? null : effectiveSelectedCategory
      );
      return {
        title: effectiveSelectedCategory,
        subtitle: durationLabel
          ? `${durationLabel} • ${filteredTransactions.length} opération${filteredTransactions.length > 1 ? "s" : ""}`
          : `${filteredTransactions.length} opération${filteredTransactions.length > 1 ? "s" : ""}`,
        totalMinor,
        theme,
      };
    }

    return {
      title: durationObj ? durationObj.label : "Période filtrée",
      subtitle: `${filteredTransactions.length} opération${filteredTransactions.length > 1 ? "s" : ""}`,
      totalMinor,
      theme: {
        icon: Calendar,
        color: BRAND_COLORS.deepLavender,
        backgroundColor: BRAND_COLORS.veryLightLavender,
      },
    };
  }, [isFilterActive, effectiveSelectedCategory, selectedDuration, filteredTransactions]);

  return (
    <View style={styles.container}>
      {/* Top Header */}
      <View
        style={[
          styles.header,
          { paddingTop: Math.max(insets.top, Spacing.three) + Spacing.three },
        ]}>
        {/* Fresh mint green top-to-bottom gradient */}
        <LinearGradient
          colors={['#C4EBDD', '#E2F6EE', '#FFFFFF']}
          locations={[0, 0.45, 1]}
          start={{ x: 0.5, y: 0 }}
          end={{ x: 0.5, y: 1 }}
          style={StyleSheet.absoluteFill}
        />

        <View style={styles.headerTitleRow}>
          <View style={styles.headerTitleCol}>
            <Text style={styles.headerTitle}>Transactions</Text>
            <Text style={styles.headerSubtitle}>
              {filteredTransactions.length} opération{filteredTransactions.length > 1 ? 's' : ''}
              {filteredTransactions.length !== transactions.length ? ` sur ${transactions.length}` : ' enregistrées'}
            </Text>
          </View>

          <TouchableOpacity
            onPress={() => setIsImportModalVisible(true)}
            activeOpacity={0.8}
            style={styles.importBtn}
            accessibilityRole="button"
            accessibilityLabel="Importer des transactions CSV">
            <View style={styles.importBtnIconBadge}>
              <Upload size={13} color={BRAND_COLORS.pureWhite} strokeWidth={2.4} />
            </View>
            <Text style={styles.importBtnText}>Importer CSV</Text>
          </TouchableOpacity>
        </View>

        {/* Search & Filter Controls Row */}
        <View style={styles.searchRow}>
          <View style={styles.searchBar}>
            <Search size={20} color={BRAND_COLORS.secondaryText} strokeWidth={2} style={styles.searchIcon} />
            <TextInput
              value={searchQuery}
              onChangeText={setSearchQuery}
              placeholder="Search merchants, notes, amounts"
              placeholderTextColor={BRAND_COLORS.secondaryText}
              style={styles.searchInput}
              clearButtonMode="while-editing"
              autoCapitalize="none"
              autoCorrect={false}
              returnKeyType="search"
            />
            {searchQuery.length > 0 && (
              <TouchableOpacity
                onPress={() => setSearchQuery('')}
                activeOpacity={0.7}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                style={styles.searchClearBtn}
                accessibilityRole="button"
                accessibilityLabel="Effacer la recherche">
                <X size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2.4} />
              </TouchableOpacity>
            )}
          </View>

          <TouchableOpacity
            onPress={() => setIsCategoryModalVisible(true)}
            activeOpacity={0.75}
            style={[
              styles.filterBtnContainer,
              isFilterActive && styles.filterBtnContainerActive,
            ]}
            accessibilityRole="button"
            accessibilityLabel="Filtrer les opérations">
            <SlidersHorizontal
              size={20}
              color={isFilterActive ? BRAND_COLORS.deepLavender : BRAND_COLORS.secondaryText}
              strokeWidth={2.2}
            />
            {isFilterActive && <View style={styles.filterActiveDot} />}
          </TouchableOpacity>
        </View>

        {/* Selected Filter Summary Banner */}
        {activeFilterSummary && (
          <View style={styles.categorySummaryBanner}>
            <View style={styles.categorySummaryLeft}>
              <View
                style={[
                  styles.categorySummaryIcon,
                  { backgroundColor: activeFilterSummary.theme.backgroundColor },
                ]}>
                <activeFilterSummary.theme.icon
                  size={16}
                  color={activeFilterSummary.theme.color}
                  strokeWidth={2.2}
                />
              </View>
              <View>
                <Text style={styles.categorySummaryTitle}>
                  {activeFilterSummary.title}
                </Text>
                <Text style={styles.categorySummarySubtitle}>
                  {activeFilterSummary.subtitle} •{' '}
                  <Text style={styles.categorySummaryAmount}>
                    {formatCurrencyMinor(activeFilterSummary.totalMinor, 'EUR', { showSign: true })}
                  </Text>
                </Text>
              </View>
            </View>

            <TouchableOpacity
              onPress={() => {
                setSelectedCategory(null);
                setSelectedDuration('ALL');
              }}
              style={styles.categorySummaryClearBtn}
              accessibilityRole="button"
              accessibilityLabel="Réinitialiser les filtres">
              <X size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* Transaction List */}
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={BRAND_COLORS.mutedLavender} />
        </View>
      ) : (
        <FlatList
          data={filteredTransactions}
          keyExtractor={(item) => item.id}
          contentContainerStyle={[
            styles.listContent,
            { paddingBottom: insets.bottom + BottomTabInset + Spacing.four },
          ]}
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={() => loadTransactions(true)}
              tintColor={BRAND_COLORS.mutedLavender}
            />
          }
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <View style={styles.emptyIconBadge}>
                <Text style={styles.emptyIconText}>💳</Text>
              </View>
              <Text style={styles.emptyTitle}>
                {searchQuery || isFilterActive
                  ? 'Aucun résultat trouvé'
                  : 'Aucune transaction enregistrée'}
              </Text>
              <Text style={styles.emptySubtitle}>
                {searchQuery || isFilterActive
                  ? 'Modifiez vos filtres ou votre recherche pour trouver des opérations.'
                  : 'Importez votre relevé bancaire CSV pour suivre vos dépenses et vos revenus.'}
              </Text>
              {!searchQuery && !isFilterActive && (
                <TouchableOpacity
                  onPress={() => setIsImportModalVisible(true)}
                  activeOpacity={0.8}
                  style={styles.emptyImportBtn}
                  accessibilityRole="button"
                  accessibilityLabel="Importer un relevé bancaire CSV">
                  <Upload size={15} color={BRAND_COLORS.pureWhite} strokeWidth={2.4} />
                  <Text style={styles.emptyImportBtnText}>Importer un fichier CSV</Text>
                </TouchableOpacity>
              )}
            </View>
          }
          renderItem={({ item }) => {
            const isPositive = item.amountMinor > 0;
            const displayLabel = item.normalizedLabel || item.rawLabel || 'Transaction';
            const theme = getCategoryTheme(item.categoryName, isPositive);
            const IconComponent = theme.icon;
            const isSubscription = item.categoryName?.toLowerCase().includes('abonnement');

            if (isSubscription) {
              return (
                <TouchableOpacity
                  activeOpacity={0.85}
                  onPress={() => setSelectedTransaction(item)}
                  style={[
                    styles.subCard,
                    isPositive ? styles.cardPositive : styles.cardNegative,
                  ]}>
                  {/* Top row: Brand & Title on left, Amount on right */}
                  <View style={styles.subCardTopRow}>
                    <View style={styles.subCardBrandGroup}>
                      <View
                        style={[
                          styles.subCardLogoBadge,
                          { backgroundColor: theme.backgroundColor },
                        ]}>
                        <IconComponent size={19} color={theme.color} strokeWidth={2.2} />
                      </View>
                      <Text style={styles.subCardTitle} numberOfLines={1}>
                        {displayLabel}
                      </Text>
                    </View>

                    <Text
                      style={[
                        styles.subCardAmount,
                        isPositive ? styles.positiveAmount : styles.negativeAmount,
                      ]}>
                      {formatCurrencyMinor(item.amountMinor, item.currency || 'EUR', {
                        showSign: true,
                      })}
                    </Text>
                  </View>

                  {/* Middle row: Date, Periodicity (1 month), Category */}
                  <View style={styles.subCardMetaRow}>
                    <Text style={styles.subCardDate}>
                      {formatDateDisplay(item.bookingDate, { includeYear: true })}
                    </Text>
                    <Text style={styles.subCardPeriod}>1 mois</Text>
                    <Text style={styles.subCardCategory}>Abonnements</Text>
                  </View>

                  {/* Bottom Action Button: Discover alternative */}
                  <TouchableOpacity
                    onPress={() => router.push('/(tabs)/subscriptions')}
                    activeOpacity={0.8}
                    style={styles.subCardActionBtn}
                    accessibilityRole="button"
                    accessibilityLabel={`Découvrir une alternative pour ${displayLabel}`}>
                    <Sparkles size={14} color="#4A3B72" strokeWidth={2.2} style={styles.subCardActionIcon} />
                    <Text style={styles.subCardActionText}>Découvrir une alternative</Text>
                  </TouchableOpacity>
                </TouchableOpacity>
              );
            }

            return (
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={() => setSelectedTransaction(item)}
                style={[
                  styles.itemRow,
                  isPositive ? styles.cardPositive : styles.cardNegative,
                ]}>
                {/* Visual Category Icon Badge */}
                <View
                  style={[
                    styles.itemIconBadge,
                    {
                      backgroundColor: theme.backgroundColor,
                    },
                  ]}>
                  <IconComponent size={18} color={theme.color} strokeWidth={2.2} />
                </View>

                {/* Details */}
                <View style={styles.itemDetails}>
                  <Text style={styles.itemLabel} numberOfLines={1}>
                    {displayLabel}
                  </Text>
                  <Text style={styles.itemDate}>
                    {formatDateDisplay(item.bookingDate, { includeYear: true })}
                  </Text>
                </View>

                {/* Amount */}
                <Text
                  style={[
                    styles.itemAmount,
                    isPositive ? styles.positiveAmount : styles.negativeAmount,
                  ]}>
                  {formatCurrencyMinor(item.amountMinor, item.currency || 'EUR', {
                    showSign: true,
                  })}
                </Text>
              </TouchableOpacity>
            );
          }}
        />
      )}

      {/* CSV Import Modal */}
      <ImportCsvModal
        visible={isImportModalVisible}
        onClose={() => setIsImportModalVisible(false)}
        onImportSuccess={() => loadTransactions(true)}
      />

      {/* Category Selection Modal */}
      <Modal
        visible={isCategoryModalVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setIsCategoryModalVisible(false)}>
        <View style={styles.modalOverlay}>
          <TouchableOpacity
            style={styles.modalBackdrop}
            activeOpacity={1}
            onPress={() => setIsCategoryModalVisible(false)}
          />

          <View style={[styles.modalSheet, { paddingBottom: Math.max(insets.bottom, Spacing.three) + Spacing.two }]}>
            {/* Sheet Handle */}
            <View style={styles.modalHandle} />

            {/* Sheet Header */}
            <View style={styles.modalHeaderRow}>
              <View style={styles.modalHeaderTitleCol}>
                <Text style={styles.modalHeaderTitle}>Filtres</Text>
                <Text style={styles.modalHeaderSubtitle}>
                  Affinez par période et catégorie
                </Text>
              </View>
              <View style={styles.modalHeaderRightActions}>
                {isFilterActive && (
                  <TouchableOpacity
                    onPress={() => {
                      setSelectedCategory(null);
                      setSelectedDuration('ALL');
                    }}
                    style={styles.modalResetBtn}
                    accessibilityRole="button"
                    accessibilityLabel="Réinitialiser tous les filtres">
                    <RotateCcw size={12} color={BRAND_COLORS.deepLavender} strokeWidth={2.4} />
                    <Text style={styles.modalResetBtnText}>Réinitialiser</Text>
                  </TouchableOpacity>
                )}
                <TouchableOpacity
                  onPress={() => setIsCategoryModalVisible(false)}
                  style={styles.modalCloseBtn}
                  accessibilityRole="button"
                  accessibilityLabel="Fermer la sélection de filtres">
                  <X size={16} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
                </TouchableOpacity>
              </View>
            </View>

            {/* Période Filter Section */}
            <View style={styles.modalSectionHeader}>
              <Calendar size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
              <Text style={styles.modalSectionLabel}>PÉRIODE</Text>
            </View>

            <View style={styles.durationPillsRow}>
              {DURATION_OPTIONS.map((opt) => {
                const isActive = selectedDuration === opt.id;
                return (
                  <TouchableOpacity
                    key={opt.id}
                    onPress={() => setSelectedDuration(opt.id)}
                    activeOpacity={0.75}
                    style={[
                      styles.durationPill,
                      isActive && styles.durationPillActive,
                    ]}
                    accessibilityRole="button"
                    accessibilityLabel={`Période : ${opt.label}`}>
                    <Text
                      style={[
                        styles.durationPillText,
                        isActive && styles.durationPillTextActive,
                      ]}>
                      {opt.shortLabel}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            {/* Catégories Section Header */}
            <View style={[styles.modalSectionHeader, { marginTop: 14 }]}>
              <Layers size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
              <Text style={styles.modalSectionLabel}>CATÉGORIES</Text>
            </View>

            {/* Categories List */}
            <ScrollView
              style={styles.modalCategoryList}
              showsVerticalScrollIndicator={false}>
              {/* Option: Toutes */}
              <TouchableOpacity
                onPress={() => {
                  setSelectedCategory(null);
                  setIsCategoryModalVisible(false);
                }}
                activeOpacity={0.7}
                style={[
                  styles.modalCategoryItem,
                  effectiveSelectedCategory === null && styles.modalCategoryItemActive,
                ]}>
                <View style={styles.modalCategoryLeft}>
                  <View style={[styles.modalCategoryIconCircle, { backgroundColor: BRAND_COLORS.veryLightLavender }]}>
                    <Layers size={18} color={BRAND_COLORS.deepLavender} strokeWidth={2.2} />
                  </View>
                  <View style={styles.modalCategoryTextGroup}>
                    <Text style={[styles.modalCategoryName, effectiveSelectedCategory === null && styles.modalCategoryNameActive]}>
                      Toutes les catégories
                    </Text>
                    <Text style={styles.modalCategoryCount}>
                      {durationFilteredTransactions.length} opération{durationFilteredTransactions.length > 1 ? 's' : ''}
                    </Text>
                  </View>
                </View>

                {effectiveSelectedCategory === null && (
                  <Check size={18} color={BRAND_COLORS.deepLavender} strokeWidth={2.6} />
                )}
              </TouchableOpacity>

              {/* Individual Categories (Abonnements always first) */}
              {categoryCounts.map((cat) => {
                const isSelected = effectiveSelectedCategory === cat.name;
                const theme = getCategoryTheme(cat.name === 'Non catégorisé' ? null : cat.name);
                const IconComp = theme.icon;

                return (
                  <TouchableOpacity
                    key={cat.name}
                    onPress={() => {
                      setSelectedCategory(isSelected ? null : cat.name);
                      setIsCategoryModalVisible(false);
                    }}
                    activeOpacity={0.7}
                    style={[
                      styles.modalCategoryItem,
                      isSelected && styles.modalCategoryItemActive,
                    ]}>
                    <View style={styles.modalCategoryLeft}>
                      <View style={[styles.modalCategoryIconCircle, { backgroundColor: theme.backgroundColor }]}>
                        <IconComp size={18} color={theme.color} strokeWidth={2.2} />
                      </View>
                      <View style={styles.modalCategoryTextGroup}>
                        <Text style={[styles.modalCategoryName, isSelected && styles.modalCategoryNameActive]}>
                          {cat.name}
                        </Text>
                        <Text style={styles.modalCategoryCount}>
                          {cat.count} opération{cat.count > 1 ? 's' : ''}
                        </Text>
                      </View>
                    </View>

                    {isSelected && (
                      <Check size={18} color={BRAND_COLORS.deepLavender} strokeWidth={2.6} />
                    )}
                  </TouchableOpacity>
                );
              })}
            </ScrollView>

            {/* Bottom Apply Button */}
            <TouchableOpacity
              onPress={() => setIsCategoryModalVisible(false)}
              activeOpacity={0.85}
              style={styles.modalApplyBtn}
              accessibilityRole="button"
              accessibilityLabel="Appliquer les filtres">
              <Text style={styles.modalApplyBtnText}>
                Afficher les opérations ({filteredTransactions.length})
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>

      {/* Transaction Details Modal */}
      <Modal
        visible={selectedTransaction !== null}
        transparent
        animationType="fade"
        onRequestClose={() => setSelectedTransaction(null)}>
        <View style={styles.modalOverlay}>
          <TouchableOpacity
            style={styles.modalBackdrop}
            activeOpacity={1}
            onPress={() => setSelectedTransaction(null)}
          />

          {selectedTransaction && (() => {
            const isPositive = selectedTransaction.amountMinor > 0;
            const theme = getCategoryTheme(selectedTransaction.categoryName, isPositive);
            const IconComponent = theme.icon;
            const displayLabel = selectedTransaction.normalizedLabel || selectedTransaction.rawLabel || 'Transaction';
            const isSubscription = selectedTransaction.categoryName?.toLowerCase().includes('abonnement');

            return (
              <View style={[styles.detailModalSheet, { paddingBottom: Math.max(insets.bottom, Spacing.three) + Spacing.two }]}>
                {/* Handle */}
                <View style={styles.modalHandle} />

                {/* Header with Close */}
                <View style={styles.detailModalHeaderRow}>
                  <Text style={styles.detailModalHeaderTitle}>{"Détail de l'opération"}</Text>
                  <TouchableOpacity
                    onPress={() => setSelectedTransaction(null)}
                    style={styles.modalCloseBtn}
                    accessibilityRole="button"
                    accessibilityLabel="Fermer les détails">
                    <X size={16} color={BRAND_COLORS.secondaryText} strokeWidth={2.2} />
                  </TouchableOpacity>
                </View>

                {/* Hero Badge & Amount */}
                <View style={styles.detailHeroSection}>
                  <View style={[styles.detailHeroIconBadge, { backgroundColor: theme.backgroundColor }]}>
                    <IconComponent size={28} color={theme.color} strokeWidth={2.4} />
                  </View>

                  <Text style={styles.detailHeroTitle} numberOfLines={2}>
                    {displayLabel}
                  </Text>

                  <Text
                    style={[
                      styles.detailHeroAmount,
                      isPositive ? styles.positiveAmount : styles.negativeAmount,
                    ]}>
                    {formatCurrencyMinor(selectedTransaction.amountMinor, selectedTransaction.currency || 'EUR', {
                      showSign: true,
                    })}
                  </Text>

                  <View style={[styles.detailCategoryPill, { backgroundColor: theme.backgroundColor }]}>
                    <Text style={[styles.detailCategoryPillText, { color: theme.color }]}>
                      {selectedTransaction.categoryName || 'Non catégorisé'}
                    </Text>
                  </View>
                </View>

                {/* Info Card List */}
                <View style={styles.detailInfoCard}>
                  {/* Date */}
                  <View style={styles.detailInfoRow}>
                    <View style={styles.detailInfoLabelCol}>
                      <Calendar size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                      <Text style={styles.detailInfoLabel}>{"Date d'opération"}</Text>
                    </View>
                    <Text style={styles.detailInfoValue}>
                      {formatDateDisplay(selectedTransaction.bookingDate, { includeYear: true })}
                    </Text>
                  </View>

                  <View style={styles.detailInfoDivider} />

                  {/* Type */}
                  <View style={styles.detailInfoRow}>
                    <View style={styles.detailInfoLabelCol}>
                      <Tag size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                      <Text style={styles.detailInfoLabel}>Type</Text>
                    </View>
                    <Text style={styles.detailInfoValue}>
                      {isSubscription
                        ? 'Abonnement récurrent'
                        : isPositive
                        ? 'Revenu crédité'
                        : 'Dépense'}
                    </Text>
                  </View>

                  <View style={styles.detailInfoDivider} />

                  {/* Statut */}
                  <View style={styles.detailInfoRow}>
                    <View style={styles.detailInfoLabelCol}>
                      <CheckCircle2 size={14} color="#15803D" strokeWidth={2} />
                      <Text style={styles.detailInfoLabel}>Statut</Text>
                    </View>
                    <View style={styles.detailStatusBadge}>
                      <Text style={styles.detailStatusText}>Validé</Text>
                    </View>
                  </View>

                  {/* Raw bank label */}
                  {selectedTransaction.rawLabel ? (
                    <>
                      <View style={styles.detailInfoDivider} />
                      <View style={styles.detailRawLabelBlock}>
                        <View style={styles.detailInfoLabelCol}>
                          <FileText size={14} color={BRAND_COLORS.secondaryText} strokeWidth={2} />
                          <Text style={styles.detailInfoLabel}>Libellé bancaire brut</Text>
                        </View>
                        <Text style={styles.detailRawLabelText}>
                          {selectedTransaction.rawLabel}
                        </Text>
                      </View>
                    </>
                  ) : null}
                </View>

                {/* Subscription CTA if applicable */}
                {isSubscription && (
                  <TouchableOpacity
                    onPress={() => {
                      setSelectedTransaction(null);
                      router.push('/(tabs)/subscriptions');
                    }}
                    activeOpacity={0.85}
                    style={styles.detailSubActionBtn}
                    accessibilityRole="button"
                    accessibilityLabel="Découvrir une alternative à cet abonnement">
                    <Sparkles size={15} color="#4A3B72" strokeWidth={2.2} />
                    <Text style={styles.detailSubActionText}>
                      Découvrir une alternative moins chère
                    </Text>
                  </TouchableOpacity>
                )}
              </View>
            );
          })()}
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  header: {
    backgroundColor: BRAND_COLORS.pureWhite,
    position: 'relative',
    paddingHorizontal: Spacing.three + 2,
    paddingBottom: Spacing.three + 2,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.06,
    shadowRadius: 10,
    elevation: 4,
    zIndex: 10,
  },
  headerTitleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 14,
    marginBottom: Spacing.three + 2,
  },
  headerTitleCol: {
    flex: 1,
  },
  importBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingLeft: 8,
    paddingRight: 14,
    paddingVertical: 7,
    borderRadius: 9999,
    gap: 8,
    shadowColor: BRAND_COLORS.deepLavender,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.22,
    shadowRadius: 5,
    elevation: 3,
  },
  importBtnIconBadge: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: 'rgba(255, 255, 255, 0.22)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  importBtnText: {
    color: BRAND_COLORS.pureWhite,
    fontSize: 12.5,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  emptyImportBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 14,
    gap: 8,
    marginTop: 14,
  },
  emptyImportBtnText: {
    color: BRAND_COLORS.pureWhite,
    fontSize: 13,
    fontWeight: '700',
  },
  headerTitle: {
    fontSize: 26,
    fontWeight: '800',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.4,
  },
  headerSubtitle: {
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    marginTop: 3,
    fontWeight: '500',
  },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  searchBar: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(0, 0, 0, 0.09)',
    paddingHorizontal: 16,
    height: 50,
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1.5 },
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 2,
  },
  searchIcon: {
    marginRight: 11,
  },
  searchInput: {
    flex: 1,
    fontSize: 15.5,
    color: BRAND_COLORS.darkCharcoal,
    paddingVertical: 0,
    fontWeight: '400',
  },
  searchClearBtn: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: 'rgba(0, 0, 0, 0.06)',
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 6,
  },
  filterBtnContainer: {
    width: 50,
    height: 50,
    borderRadius: 12,
    backgroundColor: BRAND_COLORS.pureWhite,
    borderWidth: 1,
    borderColor: 'rgba(0, 0, 0, 0.09)',
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 1.5 },
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 2,
  },
  filterBtnContainerActive: {
    backgroundColor: BRAND_COLORS.veryLightLavender,
    borderColor: 'rgba(128, 103, 161, 0.25)',
  },
  filterActiveDot: {
    position: 'absolute',
    top: 9,
    right: 9,
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: BRAND_COLORS.deepLavender,
  },
  modalOverlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(26, 26, 26, 0.45)',
  },
  modalBackdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
  modalSheet: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.two,
    maxHeight: '88%',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.1,
    shadowRadius: 16,
    elevation: 10,
  },
  modalHandle: {
    width: 38,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(0, 0, 0, 0.15)',
    alignSelf: 'center',
    marginBottom: Spacing.two,
  },
  modalHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingBottom: Spacing.two,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 0, 0, 0.06)',
    marginBottom: Spacing.two,
  },
  modalHeaderTitleCol: {
    flex: 1,
    paddingRight: Spacing.two,
  },
  modalHeaderTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  modalHeaderSubtitle: {
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  modalHeaderRightActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  modalResetBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 9999,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    marginRight: 8,
  },
  modalResetBtnText: {
    fontSize: 11.5,
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
  },
  modalCloseBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: 'rgba(0, 0, 0, 0.05)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalSectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 8,
    paddingHorizontal: 2,
  },
  modalSectionLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: BRAND_COLORS.secondaryText,
    letterSpacing: 0.6,
  },
  durationPillsRow: {
    flexDirection: 'row',
    gap: 6,
    marginBottom: 4,
  },
  durationPill: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.25)',
  },
  durationPillActive: {
    backgroundColor: BRAND_COLORS.deepLavender,
    borderColor: BRAND_COLORS.deepLavender,
  },
  durationPillText: {
    fontSize: 11.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  durationPillTextActive: {
    color: BRAND_COLORS.pureWhite,
    fontWeight: '700',
  },
  modalCategoryList: {
    maxHeight: 280,
  },
  modalCategoryItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 14,
    marginBottom: 6,
  },
  modalCategoryItemActive: {
    backgroundColor: BRAND_COLORS.veryLightLavender,
  },
  modalCategoryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flex: 1,
  },
  modalCategoryIconCircle: {
    width: 38,
    height: 38,
    borderRadius: 19,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalCategoryTextGroup: {
    flex: 1,
  },
  modalCategoryName: {
    fontSize: 14.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  modalCategoryNameActive: {
    fontWeight: '700',
    color: BRAND_COLORS.deepLavender,
  },
  modalCategoryCount: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
  },
  modalApplyBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingVertical: 13,
    borderRadius: 9999,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 12,
    shadowColor: BRAND_COLORS.deepLavender,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 2,
  },
  modalApplyBtnText: {
    color: BRAND_COLORS.pureWhite,
    fontSize: 13.5,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  categorySummaryBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: BRAND_COLORS.veryLightLavender,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 9,
    marginTop: 12,
  },
  categorySummaryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  categorySummaryIcon: {
    width: 32,
    height: 32,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  categorySummaryTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  categorySummarySubtitle: {
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
  },
  categorySummaryAmount: {
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  categorySummaryClearBtn: {
    padding: 6,
    borderRadius: 12,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  listContent: {
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.three,
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    width: '100%',
  },
  subCard: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 24,
    padding: Spacing.three + 2,
    marginBottom: Spacing.two + 4,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.25)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.04,
    shadowRadius: 6,
    elevation: 2,
  },
  subCardTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  subCardBrandGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    marginRight: 10,
    gap: 10,
  },
  subCardLogoBadge: {
    width: 38,
    height: 38,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  subCardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    flex: 1,
  },
  subCardAmount: {
    fontSize: 16,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    fontVariant: ['tabular-nums'],
  },
  subCardMetaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 2,
    marginBottom: 14,
  },
  subCardDate: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
  subCardPeriod: {
    fontSize: 13,
    color: BRAND_COLORS.darkCharcoal,
    fontWeight: '700',
  },
  subCardCategory: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
  subCardActionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#D1C4E9',
    paddingVertical: 12,
    borderRadius: 9999,
    gap: 6,
  },
  subCardActionIcon: {
    marginRight: 2,
  },
  subCardActionText: {
    fontSize: 13.5,
    fontWeight: '700',
    color: '#4A3B72',
  },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.pureWhite,
    padding: Spacing.three,
    borderRadius: 16,
    marginBottom: Spacing.two,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.2)',
  },
  cardPositive: {
    backgroundColor: '#F0FDF4',
    borderColor: 'rgba(34, 197, 94, 0.22)',
  },
  cardNegative: {
    backgroundColor: '#FEF2F2',
    borderColor: 'rgba(239, 68, 68, 0.18)',
  },
  itemIconBadge: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: Spacing.three,
  },
  itemDetails: {
    flex: 1,
    paddingRight: Spacing.two,
  },
  itemLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 4,
  },
  itemDate: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
  },
  itemAmount: {
    fontSize: 15,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  positiveAmount: {
    color: '#15803D',
  },
  negativeAmount: {
    color: '#E05656',
  },
  emptyContainer: {
    paddingVertical: Spacing.six,
    alignItems: 'center',
    paddingHorizontal: Spacing.three,
  },
  emptyIconBadge: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: Spacing.three,
  },
  emptyIconText: {
    fontSize: 26,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: Spacing.one,
    textAlign: 'center',
  },
  emptySubtitle: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 19,
    maxWidth: 280,
  },
  detailModalSheet: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
    maxHeight: '90%',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.12,
    shadowRadius: 16,
    elevation: 10,
  },
  detailModalHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingBottom: Spacing.two,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 0, 0, 0.06)',
  },
  detailModalHeaderTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  detailHeroSection: {
    alignItems: 'center',
    paddingVertical: Spacing.three,
  },
  detailHeroIconBadge: {
    width: 60,
    height: 60,
    borderRadius: 30,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  detailHeroTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    marginBottom: 6,
    paddingHorizontal: Spacing.two,
  },
  detailHeroAmount: {
    fontSize: 28,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
    marginBottom: 10,
  },
  detailCategoryPill: {
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 9999,
  },
  detailCategoryPillText: {
    fontSize: 12,
    fontWeight: '700',
  },
  detailInfoCard: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.2)',
    padding: Spacing.three,
    marginBottom: Spacing.two + 4,
  },
  detailInfoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 6,
  },
  detailInfoLabelCol: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  detailInfoLabel: {
    fontSize: 13,
    color: BRAND_COLORS.secondaryText,
    fontWeight: '500',
  },
  detailInfoValue: {
    fontSize: 13.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  detailInfoDivider: {
    height: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.05)',
    marginVertical: 4,
  },
  detailStatusBadge: {
    backgroundColor: '#E6F4EA',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  detailStatusText: {
    fontSize: 11.5,
    fontWeight: '700',
    color: '#137333',
  },
  detailRawLabelBlock: {
    paddingTop: 6,
    gap: 6,
  },
  detailRawLabelText: {
    fontSize: 12,
    color: BRAND_COLORS.darkCharcoal,
    lineHeight: 17,
    backgroundColor: 'rgba(0, 0, 0, 0.03)',
    padding: 10,
    borderRadius: 10,
  },
  detailSubActionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#D1C4E9',
    paddingVertical: 13,
    borderRadius: 9999,
    gap: 8,
    marginTop: 4,
  },
  detailSubActionText: {
    fontSize: 13.5,
    fontWeight: '700',
    color: '#4A3B72',
  },
});
