import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { Inbox, X } from 'lucide-react-native';

import { AnimatedPressable } from '@/components/motion/animated-pressable';
import { MoneyAmount } from '@/components/dashboard/money-amount';
import {
  ActivityDetailSheet,
  ActivityFilterSheet,
  ActivityHeader,
  ActivityJournalRow,
  ActivitySearchBar,
  DurationFilter,
  ImportCsvModal,
} from '@/components/transactions';
import { BRAND_COLORS } from '@/constants/colors';
import { MOTION } from '@/constants/motion';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { FONT_FAMILY } from '@/constants/typography';
import { getActivityCopy } from '@/constants/activity-copy';
import { fetchRecentTransactions, TransactionItem } from '@/services/dashboard-api';
import { groupTransactionsByDate } from '@/utils/formatters';
import { triggerHaptic } from '@/utils/haptics';

export const DURATION_OPTIONS: { id: DurationFilter; label: string; shortLabel: string }[] = [
  { id: 'ALL', label: 'All dates', shortLabel: 'All' },
  { id: '30_DAYS', label: 'Last 30 days', shortLabel: '30 days' },
  { id: '3_MONTHS', label: 'Last 3 months', shortLabel: '3 months' },
  { id: 'THIS_YEAR', label: 'This year', shortLabel: 'This year' },
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
  const isReducedMotion = useReducedMotion();
  const locale = 'en-IE';
  const copy = getActivityCopy(locale);

  const scalePress = isReducedMotion ? 1.0 : MOTION.scale.buttonPress;
  const scaleAction = isReducedMotion ? 1.0 : MOTION.scale.actionPress;

  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isImportModalVisible, setIsImportModalVisible] = useState<boolean>(false);
  const [isFilterSheetVisible, setIsFilterSheetVisible] = useState<boolean>(false);
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

  // Filter transactions by selected duration
  const durationFilteredTransactions = useMemo(() => {
    return transactions.filter((tx) => isDateInDuration(tx.bookingDate, selectedDuration));
  }, [transactions, selectedDuration]);

  // Compute categories and counts based on duration
  const categoryCounts = useMemo(() => {
    const counts = new Map<string, number>();

    durationFilteredTransactions.forEach((tx) => {
      const cat = tx.categoryName || copy.showAllCategories;
      counts.set(cat, (counts.get(cat) || 0) + 1);
    });

    return Array.from(counts.entries())
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => {
        const isSubA = a.name.toLowerCase().includes('abonnement') || a.name.toLowerCase().includes('subscription');
        const isSubB = b.name.toLowerCase().includes('abonnement') || b.name.toLowerCase().includes('subscription');
        if (isSubA && !isSubB) return -1;
        if (!isSubA && isSubB) return 1;
        return b.count - a.count;
      });
  }, [durationFilteredTransactions, copy.showAllCategories]);

  const effectiveSelectedCategory =
    selectedCategory && categoryCounts.some((c) => c.name === selectedCategory)
      ? selectedCategory
      : null;

  const isFilterActive = effectiveSelectedCategory !== null || selectedDuration !== 'ALL';

  // Apply category and full-text search filter
  const filteredTransactions = useMemo(() => {
    return durationFilteredTransactions.filter((tx) => {
      // Category filter
      if (effectiveSelectedCategory) {
        const itemCat = tx.categoryName || copy.showAllCategories;
        if (itemCat !== effectiveSelectedCategory) {
          return false;
        }
      }

      // Search filter (merchant, raw label, category, formatted amounts)
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
  }, [durationFilteredTransactions, effectiveSelectedCategory, searchQuery, copy.showAllCategories]);

  // Group filtered transactions into chronological calendar sections
  const groupedTransactions = useMemo(() => {
    return groupTransactionsByDate(filteredTransactions, { locale });
  }, [filteredTransactions, locale]);

  // Summary banner data for active filters
  const activeFilterSummary = useMemo(() => {
    if (!isFilterActive) return null;

    let totalMinor = 0;
    filteredTransactions.forEach((tx) => {
      totalMinor += tx.amountMinor;
    });

    const durationObj = DURATION_OPTIONS.find((d) => d.id === selectedDuration);
    const durationLabel = durationObj && selectedDuration !== 'ALL' ? durationObj.shortLabel : null;

    return {
      title: effectiveSelectedCategory || (durationObj ? durationObj.label : copy.period),
      subtitle: durationLabel
        ? `${durationLabel} · ${filteredTransactions.length} ${copy.operationsRecorded}`
        : `${filteredTransactions.length} ${copy.operationsRecorded}`,
      totalMinor,
    };
  }, [isFilterActive, effectiveSelectedCategory, selectedDuration, filteredTransactions, copy]);

  const handleResetFilters = () => {
    triggerHaptic.light();
    setSelectedCategory(null);
    setSelectedDuration('ALL');
  };

  return (
    <View style={styles.container}>
      {/* 1. Monograph Editorial Header */}
      <View
        style={[
          styles.headerWrapper,
          { paddingTop: Math.max(insets.top, Spacing.three) + 4 },
        ]}>
        <ActivityHeader
          totalCount={transactions.length}
          filteredCount={filteredTransactions.length}
          locale={locale}
          onImportPress={() => setIsImportModalVisible(true)}
        />

        {/* 2. Integrated Search Bar & Quick Filter Chips */}
        <ActivitySearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          isFilterActive={isFilterActive}
          onOpenFilterSheet={() => setIsFilterSheetVisible(true)}
          categories={categoryCounts}
          selectedCategory={effectiveSelectedCategory}
          onSelectCategory={setSelectedCategory}
          locale={locale}
        />

        {/* 3. Active Filter Summary Banner */}
        {activeFilterSummary && (
          <View style={styles.filterSummaryBanner}>
            <View style={styles.filterSummaryLeft}>
              <View style={styles.filterSummaryDot} />
              <View>
                <Text style={styles.filterSummaryTitle}>{activeFilterSummary.title}</Text>
                <Text style={styles.filterSummarySubtitle}>
                  {activeFilterSummary.subtitle} ·{' '}
                  <Text style={styles.filterSummaryAmount}>
                    {(activeFilterSummary.totalMinor / 100).toFixed(2)} €
                  </Text>
                </Text>
              </View>
            </View>

            <AnimatedPressable
              onPress={handleResetFilters}
              scaleTarget={scalePress}
              haptic="light"
              style={styles.filterSummaryClearBtn}
              accessibilityRole="button"
              accessibilityLabel={copy.resetFilters}>
              <X size={13} color={BRAND_COLORS.secondaryText} strokeWidth={2.4} />
            </AnimatedPressable>
          </View>
        )}
      </View>

      {/* 4. Scrollable Transaction Journal */}
      {isLoading ? (
        <View style={styles.centerLoading}>
          <ActivityIndicator size="large" color={BRAND_COLORS.deepLavender} />
        </View>
      ) : (
        <ScrollView
          contentContainerStyle={[
            styles.scrollContent,
            { paddingBottom: insets.bottom + BottomTabInset + Spacing.four },
          ]}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={() => loadTransactions(true)}
              tintColor={BRAND_COLORS.deepLavender}
              colors={[BRAND_COLORS.deepLavender]}
              progressBackgroundColor={BRAND_COLORS.pureWhite}
            />
          }>
          {filteredTransactions.length === 0 ? (
            <View style={styles.emptyContainer}>
              <View style={styles.emptyIconCircle}>
                <Inbox size={26} color={BRAND_COLORS.deepLavender} strokeWidth={1.8} />
              </View>
              <Text style={styles.emptyTitle}>
                {searchQuery || isFilterActive ? copy.noMatchingTitle : copy.noActivityTitle}
              </Text>
              <Text style={styles.emptySubtitle}>
                {searchQuery || isFilterActive ? copy.noMatchingSubtitle : copy.noActivitySubtitle}
              </Text>

              {searchQuery || isFilterActive ? (
                <AnimatedPressable
                  onPress={handleResetFilters}
                  scaleTarget={scaleAction}
                  haptic="light"
                  style={styles.emptyActionBtn}
                  accessibilityRole="button"
                  accessibilityLabel={copy.resetFilters}>
                  <Text style={styles.emptyActionBtnText}>{copy.resetFilters}</Text>
                </AnimatedPressable>
              ) : (
                <AnimatedPressable
                  onPress={() => setIsImportModalVisible(true)}
                  scaleTarget={scaleAction}
                  haptic="light"
                  style={styles.emptyActionBtn}
                  accessibilityRole="button"
                  accessibilityLabel={copy.importCsvAction}>
                  <Text style={styles.emptyActionBtnText}>{copy.importCsvAction}</Text>
                </AnimatedPressable>
              )}
            </View>
          ) : (
            <View style={styles.journalContainer}>
              {groupedTransactions.map((group) => {
                const dayNetMinor = group.transactions.reduce((sum, tx) => sum + tx.amountMinor, 0);
                const isNetPositive = dayNetMinor > 0;

                return (
                  <View key={group.id} style={styles.groupSection}>
                    {/* Date Header: Clean Space Grotesk date on left, day net sum on right */}
                    <View style={styles.dateHeaderRow}>
                      <Text style={styles.dateHeaderText}>{group.title}</Text>
                      <MoneyAmount
                        amountMinor={dayNetMinor}
                        currency="EUR"
                        locale={locale}
                        size="compact"
                        showSign={dayNetMinor !== 0}
                        color={isNetPositive ? '#1E7558' : BRAND_COLORS.secondaryText}
                      />
                    </View>

                    {/* Date Group Card: Rounded 20px parent card with hairline dividers between rows */}
                    <View style={styles.groupCard}>
                      {group.transactions.map((tx, idx) => (
                        <ActivityJournalRow
                          key={tx.id}
                          transaction={tx}
                          locale={locale}
                          isLastInGroup={idx === group.transactions.length - 1}
                          onPress={(item) => setSelectedTransaction(item)}
                        />
                      ))}
                    </View>
                  </View>
                );
              })}
            </View>
          )}
        </ScrollView>
      )}

      {/* 5. Filter Bottom Sheet */}
      <ActivityFilterSheet
        visible={isFilterSheetVisible}
        onClose={() => setIsFilterSheetVisible(false)}
        selectedDuration={selectedDuration}
        onSelectDuration={setSelectedDuration}
        categories={categoryCounts}
        selectedCategory={effectiveSelectedCategory}
        onSelectCategory={setSelectedCategory}
        filteredCount={filteredTransactions.length}
        totalCount={transactions.length}
        locale={locale}
      />

      {/* 6. Transaction Detail Sheet */}
      <ActivityDetailSheet
        transaction={selectedTransaction}
        onClose={() => setSelectedTransaction(null)}
        locale={locale}
      />

      {/* 7. CSV Bank Import Modal */}
      <ImportCsvModal
        visible={isImportModalVisible}
        onClose={() => setIsImportModalVisible(false)}
        onImportSuccess={() => loadTransactions(true)}
        locale={locale}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  headerWrapper: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(36, 37, 53, 0.05)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.02,
    shadowRadius: 6,
    elevation: 2,
    zIndex: 10,
  },
  filterSummaryBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(128, 103, 161, 0.08)',
    borderRadius: 12,
    marginHorizontal: 20,
    marginBottom: 10,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  filterSummaryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  filterSummaryDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: BRAND_COLORS.deepLavender,
  },
  filterSummaryTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 12.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  filterSummarySubtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11,
    color: BRAND_COLORS.secondaryText,
    marginTop: 1,
  },
  filterSummaryAmount: {
    fontFamily: FONT_FAMILY.bold,
    color: BRAND_COLORS.deepLavender,
  },
  filterSummaryClearBtn: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: 'rgba(0, 0, 0, 0.05)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  centerLoading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingTop: 14,
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    width: '100%',
  },
  journalContainer: {
    gap: 14,
  },
  groupSection: {
    marginBottom: 4,
  },
  dateHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 4,
    marginBottom: 6,
  },
  dateHeaderText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.1,
  },
  groupCard: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    overflow: 'hidden',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.03,
    shadowRadius: 8,
    elevation: 2,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 56,
    paddingHorizontal: 24,
  },
  emptyIconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: 'rgba(128, 103, 161, 0.10)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  emptyTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 16,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    marginBottom: 6,
    letterSpacing: -0.2,
  },
  emptySubtitle: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    lineHeight: 18,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    marginBottom: 20,
    maxWidth: 280,
  },
  emptyActionBtn: {
    backgroundColor: BRAND_COLORS.deepLavender,
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 14,
    shadowColor: BRAND_COLORS.deepLavender,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 2,
  },
  emptyActionBtnText: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 13,
    fontWeight: '700',
    color: BRAND_COLORS.pureWhite,
  },
});
