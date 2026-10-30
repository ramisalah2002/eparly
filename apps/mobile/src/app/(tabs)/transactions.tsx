import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Upload, X } from 'lucide-react-native';

import { ImportCsvModal } from '@/components/transactions/import-csv-modal';
import { getCategoryTheme } from '@/constants/category-theme';
import { BRAND_COLORS } from '@/constants/colors';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { fetchRecentTransactions, TransactionItem } from '@/services/dashboard-api';
import { formatCurrencyMinor, formatDateDisplay } from '@/utils/formatters';

type FilterType = 'ALL' | 'EXPENSE' | 'INCOME';

export default function TransactionsScreen() {
  const insets = useSafeAreaInsets();
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isImportModalVisible, setIsImportModalVisible] = useState<boolean>(false);
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
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

  const isExpense = (tx: TransactionItem) =>
    tx.amountMinor < 0 || tx.transactionType === 'EXPENSE' || tx.transactionType === 'FEE';

  const isIncome = (tx: TransactionItem) =>
    tx.amountMinor > 0 || tx.transactionType === 'INCOME' || tx.transactionType === 'REFUND';

  // Transactions filtered by type tab (Toutes / Dépenses / Revenus)
  const typeTransactions = useMemo(() => {
    return transactions.filter((tx) => {
      if (filter === 'EXPENSE') return isExpense(tx);
      if (filter === 'INCOME') return isIncome(tx);
      return true;
    });
  }, [transactions, filter]);

  // Compute categories and counts based on the type filter
  const categoryCounts = useMemo(() => {
    const counts = new Map<string, number>();

    typeTransactions.forEach((tx) => {
      const cat = tx.categoryName || 'Non catégorisé';
      counts.set(cat, (counts.get(cat) || 0) + 1);
    });

    return Array.from(counts.entries())
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count);
  }, [typeTransactions]);

  const handleFilterChange = (newFilter: FilterType) => {
    setFilter(newFilter);
    setSelectedCategory(null);
  };

  const effectiveSelectedCategory =
    selectedCategory && categoryCounts.some((c) => c.name === selectedCategory)
      ? selectedCategory
      : null;

  const filteredTransactions = useMemo(() => {
    return typeTransactions.filter((tx) => {
      // Category filter
      if (effectiveSelectedCategory) {
        const itemCat = tx.categoryName || 'Non catégorisé';
        if (itemCat !== effectiveSelectedCategory) {
          return false;
        }
      }

      // Search filter
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const label = (tx.normalizedLabel || tx.rawLabel || '').toLowerCase();
        const category = (tx.categoryName || '').toLowerCase();
        return label.includes(q) || category.includes(q);
      }

      return true;
    });
  }, [typeTransactions, effectiveSelectedCategory, searchQuery]);

  // Total for currently active category
  const activeCategorySummary = useMemo(() => {
    if (!effectiveSelectedCategory) return null;
    let totalMinor = 0;
    let count = 0;

    filteredTransactions.forEach((tx) => {
      totalMinor += tx.amountMinor;
      count += 1;
    });

    const theme = getCategoryTheme(effectiveSelectedCategory === 'Non catégorisé' ? null : effectiveSelectedCategory);
    return {
      name: effectiveSelectedCategory,
      totalMinor,
      count,
      theme,
    };
  }, [effectiveSelectedCategory, filteredTransactions]);

  return (
    <View style={styles.container}>
      {/* Top Header */}
      <View
        style={[
          styles.header,
          { paddingTop: Math.max(insets.top, Spacing.three) + Spacing.two },
        ]}>
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
            <Upload size={14} color={BRAND_COLORS.pureWhite} strokeWidth={2.4} />
            <Text style={styles.importBtnText}>Importer CSV</Text>
          </TouchableOpacity>
        </View>

        {/* Search Bar */}
        <View style={styles.searchBar}>
          <Text style={styles.searchIcon}>🔍</Text>
          <TextInput
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholder="Rechercher une opération ou catégorie..."
            placeholderTextColor={BRAND_COLORS.secondaryText}
            style={styles.searchInput}
            clearButtonMode="while-editing"
          />
        </View>

        {/* Type Filter Pills */}
        <View style={styles.filterPillsRow}>
          <TouchableOpacity
            onPress={() => handleFilterChange('ALL')}
            style={[styles.filterPill, filter === 'ALL' && styles.activeFilterPill]}>
            <Text
              style={[
                styles.filterPillText,
                filter === 'ALL' && styles.activeFilterPillText,
              ]}>
              Toutes
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={() => handleFilterChange('EXPENSE')}
            style={[styles.filterPill, filter === 'EXPENSE' && styles.activeFilterPill]}>
            <Text
              style={[
                styles.filterPillText,
                filter === 'EXPENSE' && styles.activeFilterPillText,
              ]}>
              Dépenses
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={() => handleFilterChange('INCOME')}
            style={[styles.filterPill, filter === 'INCOME' && styles.activeFilterPill]}>
            <Text
              style={[
                styles.filterPillText,
                filter === 'INCOME' && styles.activeFilterPillText,
              ]}>
              Revenus
            </Text>
          </TouchableOpacity>
        </View>

        {/* Horizontal Category Carousel */}
        {categoryCounts.length > 0 && (
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.categoryChipsScroll}
            style={styles.categoryChipsContainer}>
            <TouchableOpacity
              onPress={() => setSelectedCategory(null)}
              activeOpacity={0.7}
              style={[
                styles.categoryChip,
                effectiveSelectedCategory === null && styles.activeCategoryChip,
              ]}>
              <Text
                style={[
                  styles.categoryChipText,
                  effectiveSelectedCategory === null && styles.activeCategoryChipText,
                ]}>
                Toutes ({typeTransactions.length})
              </Text>
            </TouchableOpacity>

            {categoryCounts.map((cat) => {
              const isSelected = effectiveSelectedCategory === cat.name;
              const theme = getCategoryTheme(cat.name === 'Non catégorisé' ? null : cat.name);
              const IconComp = theme.icon;

              return (
                <TouchableOpacity
                  key={cat.name}
                  onPress={() => setSelectedCategory(isSelected ? null : cat.name)}
                  activeOpacity={0.7}
                  style={[
                    styles.categoryChip,
                    isSelected && {
                      backgroundColor: theme.color,
                      borderColor: theme.color,
                    },
                  ]}>
                  <View
                    style={[
                      styles.categoryChipIconCircle,
                      { backgroundColor: isSelected ? 'rgba(255,255,255,0.25)' : theme.backgroundColor },
                    ]}>
                    <IconComp
                      size={12}
                      color={isSelected ? BRAND_COLORS.pureWhite : theme.color}
                      strokeWidth={2.4}
                    />
                  </View>
                  <Text
                    style={[
                      styles.categoryChipText,
                      isSelected && styles.activeCategoryChipText,
                    ]}>
                    {cat.name}
                  </Text>
                  <View
                    style={[
                      styles.categoryCountBadge,
                      isSelected && styles.activeCategoryCountBadge,
                    ]}>
                    <Text
                      style={[
                        styles.categoryCountText,
                        isSelected && styles.activeCategoryCountText,
                      ]}>
                      {cat.count}
                    </Text>
                  </View>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        )}

        {/* Selected Category Summary Banner */}
        {activeCategorySummary && (
          <View style={styles.categorySummaryBanner}>
            <View style={styles.categorySummaryLeft}>
              <View
                style={[
                  styles.categorySummaryIcon,
                  { backgroundColor: activeCategorySummary.theme.backgroundColor },
                ]}>
                <activeCategorySummary.theme.icon
                  size={16}
                  color={activeCategorySummary.theme.color}
                  strokeWidth={2.2}
                />
              </View>
              <View>
                <Text style={styles.categorySummaryTitle}>
                  {activeCategorySummary.name}
                </Text>
                <Text style={styles.categorySummarySubtitle}>
                  {activeCategorySummary.count} opération{activeCategorySummary.count > 1 ? 's' : ''} •{' '}
                  <Text style={styles.categorySummaryAmount}>
                    {formatCurrencyMinor(activeCategorySummary.totalMinor, 'EUR', { showSign: true })}
                  </Text>
                </Text>
              </View>
            </View>

            <TouchableOpacity
              onPress={() => setSelectedCategory(null)}
              style={styles.categorySummaryClearBtn}
              accessibilityRole="button"
              accessibilityLabel="Réinitialiser le filtre de catégorie">
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
                {searchQuery || effectiveSelectedCategory
                  ? 'Aucun résultat trouvé'
                  : 'Aucune transaction enregistrée'}
              </Text>
              <Text style={styles.emptySubtitle}>
                {searchQuery || effectiveSelectedCategory
                  ? 'Modifiez vos filtres ou votre recherche pour trouver des opérations.'
                  : 'Importez votre relevé bancaire CSV pour suivre vos dépenses et vos revenus.'}
              </Text>
              {!searchQuery && !effectiveSelectedCategory && (
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

            return (
              <View style={styles.itemRow}>
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
                  <View style={styles.itemMeta}>
                    <Text style={styles.itemDate}>
                      {formatDateDisplay(item.bookingDate, { includeYear: true })}
                    </Text>

                    {item.categoryName ? (
                      <View
                        style={[
                          styles.categoryBadge,
                          {
                            backgroundColor: theme.backgroundColor,
                            borderColor: `${theme.color}30`,
                          },
                        ]}>
                        <Text
                          style={[
                            styles.categoryBadgeText,
                            { color: theme.color },
                          ]}
                          numberOfLines={1}>
                          {item.categoryName}
                        </Text>
                      </View>
                    ) : (
                      <View style={styles.uncategorizedBadge}>
                        <Text style={styles.uncategorizedBadgeText}>
                          Non catégorisé
                        </Text>
                      </View>
                    )}
                  </View>
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
              </View>
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
    paddingHorizontal: Spacing.three,
    paddingBottom: Spacing.two + 2,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 0, 0, 0.05)',
  },
  headerTitleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.two,
  },
  headerTitleCol: {
    flex: 1,
  },
  importBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 12,
    gap: 6,
  },
  importBtnText: {
    color: BRAND_COLORS.pureWhite,
    fontSize: 12,
    fontWeight: '700',
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
    fontSize: 24,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  headerSubtitle: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
    marginTop: 2,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderRadius: 14,
    paddingHorizontal: Spacing.three,
    paddingVertical: 9,
    marginBottom: Spacing.two,
  },
  searchIcon: {
    fontSize: 14,
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
    color: BRAND_COLORS.darkCharcoal,
    padding: 0,
  },
  filterPillsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginBottom: Spacing.two,
  },
  filterPill: {
    paddingHorizontal: Spacing.three,
    paddingVertical: 6,
    borderRadius: 12,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  activeFilterPill: {
    backgroundColor: BRAND_COLORS.mutedLavender,
  },
  filterPillText: {
    fontSize: 12,
    fontWeight: '600',
    color: BRAND_COLORS.secondaryText,
  },
  activeFilterPillText: {
    color: BRAND_COLORS.pureWhite,
  },
  categoryChipsContainer: {
    marginBottom: Spacing.one,
  },
  categoryChipsScroll: {
    gap: 8,
    paddingRight: Spacing.two,
  },
  categoryChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 20,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.2)',
    gap: 6,
  },
  activeCategoryChip: {
    backgroundColor: BRAND_COLORS.deepLavender,
    borderColor: BRAND_COLORS.deepLavender,
  },
  categoryChipIconCircle: {
    width: 20,
    height: 20,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  categoryChipText: {
    fontSize: 12,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  activeCategoryChipText: {
    color: BRAND_COLORS.pureWhite,
  },
  categoryCountBadge: {
    backgroundColor: 'rgba(0, 0, 0, 0.05)',
    paddingHorizontal: 5,
    paddingVertical: 1,
    borderRadius: 8,
  },
  activeCategoryCountBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.25)',
  },
  categoryCountText: {
    fontSize: 10,
    fontWeight: '700',
    color: BRAND_COLORS.secondaryText,
  },
  activeCategoryCountText: {
    color: BRAND_COLORS.pureWhite,
  },
  categorySummaryBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: BRAND_COLORS.veryLightLavender,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginTop: Spacing.two,
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
  itemMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  itemDate: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
  },
  categoryBadge: {
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 6,
    borderWidth: 1,
    maxWidth: 130,
  },
  categoryBadgeText: {
    fontSize: 11,
    fontWeight: '600',
  },
  uncategorizedBadge: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(0, 0, 0, 0.05)',
  },
  uncategorizedBadgeText: {
    fontSize: 10,
    fontWeight: '500',
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
    color: BRAND_COLORS.darkCharcoal,
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
});
