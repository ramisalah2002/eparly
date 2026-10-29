import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

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
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  const loadTransactions = useCallback(async (refresh = false) => {
    if (refresh) setIsRefreshing(true);
    else setIsLoading(true);

    const res = await fetchRecentTransactions(50);
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

  const filteredTransactions = transactions.filter((tx) => {
    // Type filter
    if (filter === 'EXPENSE' && tx.transactionType !== 'EXPENSE' && tx.transactionType !== 'FEE') {
      return false;
    }
    if (filter === 'INCOME' && tx.transactionType !== 'INCOME') {
      return false;
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

  return (
    <View style={styles.container}>
      {/* Top Header */}
      <View
        style={[
          styles.header,
          { paddingTop: Math.max(insets.top, Spacing.three) + Spacing.two },
        ]}>
        <View style={styles.headerTitleRow}>
          <Text style={styles.headerTitle}>Transactions</Text>
          <Text style={styles.headerSubtitle}>
            {transactions.length} opération{transactions.length > 1 ? 's' : ''} enregistrée{transactions.length > 1 ? 's' : ''}
          </Text>
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

        {/* Filter Pills */}
        <View style={styles.filterPillsRow}>
          <TouchableOpacity
            onPress={() => setFilter('ALL')}
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
            onPress={() => setFilter('EXPENSE')}
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
            onPress={() => setFilter('INCOME')}
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
                {searchQuery ? 'Aucun résultat trouvé' : 'Aucune transaction enregistrée'}
              </Text>
              <Text style={styles.emptySubtitle}>
                {searchQuery
                  ? 'Modifiez votre recherche pour trouver des opérations.'
                  : 'Importez vos relevés bancaires CSV pour afficher vos opérations détaillées.'}
              </Text>
            </View>
          }
          renderItem={({ item }) => {
            const isPositive = item.amountMinor > 0;
            const displayLabel = item.normalizedLabel || item.rawLabel || 'Transaction';

            return (
              <View style={styles.itemRow}>
                <View
                  style={[
                    styles.itemIconBadge,
                    {
                      backgroundColor: isPositive
                        ? BRAND_COLORS.softMintBackground
                        : BRAND_COLORS.veryLightLavender,
                    },
                  ]}>
                  <Text
                    style={[
                      styles.itemIconSymbol,
                      { color: isPositive ? '#2E7D32' : BRAND_COLORS.darkCharcoal },
                    ]}>
                    {isPositive ? '↓' : '↑'}
                  </Text>
                </View>

                <View style={styles.itemDetails}>
                  <Text style={styles.itemLabel} numberOfLines={1}>
                    {displayLabel}
                  </Text>
                  <View style={styles.itemMeta}>
                    <Text style={styles.itemDate}>
                      {formatDateDisplay(item.bookingDate, { includeYear: true })}
                    </Text>
                    {item.categoryName && (
                      <View style={styles.categoryBadge}>
                        <Text style={styles.categoryBadgeText} numberOfLines={1}>
                          {item.categoryName}
                        </Text>
                      </View>
                    )}
                  </View>
                </View>

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
    paddingBottom: Spacing.three,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0, 0, 0, 0.05)',
  },
  headerTitleRow: {
    marginBottom: Spacing.two,
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
    marginBottom: Spacing.two + 2,
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
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: Spacing.three,
  },
  itemIconSymbol: {
    fontSize: 15,
    fontWeight: '800',
  },
  itemDetails: {
    flex: 1,
    paddingRight: Spacing.two,
  },
  itemLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    marginBottom: 3,
  },
  itemMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  itemDate: {
    fontSize: 12,
    color: BRAND_COLORS.secondaryText,
  },
  categoryBadge: {
    backgroundColor: BRAND_COLORS.warmOffWhite,
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 6,
    maxWidth: 120,
  },
  categoryBadgeText: {
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
    color: '#2E7D32',
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
