import { authenticatedFetch } from './api-client';
import { getCurrentMonthDateRange } from '@/utils/formatters';

export interface DashboardSummary {
  totalExpensesMinor: number;
  confirmedIncomeMinor: number;
  unclassifiedExpensesMinor: number;
  unclassifiedPositiveMinor: number;
  transactionsCount: number;
  uncategorizedTransactionsCount: number;
  firstTransactionDate: string | null;
  lastTransactionDate: string | null;
  currency: string;
  basedOnIncompleteData: boolean;
}

export interface CategorySpending {
  categoryId: string | null;
  categoryName: string;
  amountMinor: number;
  transactionCount: number;
  percentage: number;
}

export interface MonthlyTrend {
  month: string;
  expensesMinor: number;
  confirmedIncomeMinor: number;
  unclassifiedPositiveMinor: number;
  transactionCount: number;
}

export interface AccountItem {
  id: string;
  name: string;
  sourceType: string;
  currency: string;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionItem {
  id: string;
  accountId: string;
  bookingDate: string;
  valueDate?: string | null;
  amountMinor: number;
  currency: string;
  rawLabel: string;
  normalizedLabel?: string | null;
  transactionType: 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'REFUND' | 'FEE' | 'UNKNOWN';
  categoryId?: string | null;
  categoryName?: string | null;
  customCategoryFlag: boolean;
}

export interface CategoryItem {
  id: string;
  name: string;
  categoryType: 'EXPENSE' | 'INCOME' | 'BOTH';
  system: boolean;
  parentId?: string | null;
}

export interface DashboardData {
  summary: DashboardSummary | null;
  spendingByCategory: CategorySpending[];
  monthlyTrend: MonthlyTrend[];
  accounts: AccountItem[];
  recentTransactions: TransactionItem[];
  categories: CategoryItem[];
  hasAnyData: boolean;
  period: {
    startDate: string;
    endDate: string;
    monthName: string;
  };
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  status?: number;
}

/**
 * Fetches dashboard summary for a specific date range and optional account ID.
 */
export async function fetchDashboardSummary(
  startDate: string,
  endDate: string,
  accountId?: string
): Promise<ApiResponse<DashboardSummary>> {
  try {
    const params = new URLSearchParams({ startDate, endDate });
    if (accountId) params.set('accountId', accountId);

    const res = await authenticatedFetch(`/v1/dashboard/summary?${params.toString()}`);
    if (!res.ok) {
      return { success: false, status: res.status, error: `Erreur serveur (${res.status})` };
    }
    const data = (await res.json()) as DashboardSummary;
    return { success: true, data, status: 200 };
  } catch {
    return { success: false, error: 'Connexion réseau impossible' };
  }
}

/**
 * Fetches spending categorized by groups for a date range.
 */
export async function fetchSpendingByCategory(
  startDate: string,
  endDate: string,
  accountId?: string
): Promise<ApiResponse<CategorySpending[]>> {
  try {
    const params = new URLSearchParams({ startDate, endDate });
    if (accountId) params.set('accountId', accountId);

    const res = await authenticatedFetch(`/v1/dashboard/spending-by-category?${params.toString()}`);
    if (!res.ok) {
      return { success: false, status: res.status, error: `Erreur (${res.status})` };
    }
    const data = (await res.json()) as CategorySpending[];
    return { success: true, data: Array.isArray(data) ? data : [], status: 200 };
  } catch {
    return { success: false, error: 'Connexion réseau impossible' };
  }
}

/**
 * Fetches monthly historical trend (default 6 months).
 */
export async function fetchMonthlyTrend(
  months = 6,
  accountId?: string
): Promise<ApiResponse<MonthlyTrend[]>> {
  try {
    const params = new URLSearchParams({ months: String(months) });
    if (accountId) params.set('accountId', accountId);

    const res = await authenticatedFetch(`/v1/dashboard/monthly-trend?${params.toString()}`);
    if (!res.ok) {
      return { success: false, status: res.status, error: `Erreur (${res.status})` };
    }
    const data = (await res.json()) as MonthlyTrend[];
    return { success: true, data: Array.isArray(data) ? data : [], status: 200 };
  } catch {
    return { success: false, error: 'Connexion réseau impossible' };
  }
}

/**
 * Fetches user accounts.
 */
export async function fetchAccounts(): Promise<ApiResponse<AccountItem[]>> {
  try {
    const res = await authenticatedFetch('/v1/accounts');
    if (!res.ok) {
      return { success: false, status: res.status, error: `Erreur (${res.status})` };
    }
    const data = (await res.json()) as AccountItem[];
    return { success: true, data: Array.isArray(data) ? data : [], status: 200 };
  } catch {
    return { success: false, error: 'Connexion réseau impossible' };
  }
}

/**
 * Fetches recent transactions (paged, size defaults to 5).
 */
export async function fetchRecentTransactions(
  size = 5,
  accountId?: string
): Promise<ApiResponse<TransactionItem[]>> {
  try {
    const params = new URLSearchParams({ page: '0', size: String(size) });
    if (accountId) params.set('accountId', accountId);

    const res = await authenticatedFetch(`/v1/transactions?${params.toString()}`);
    if (!res.ok) {
      return { success: false, status: res.status, error: `Erreur (${res.status})` };
    }
    const body = (await res.json()) as { content?: TransactionItem[] };
    return { success: true, data: Array.isArray(body.content) ? body.content : [], status: 200 };
  } catch {
    return { success: false, error: 'Connexion réseau impossible' };
  }
}

/**
 * Fetches system and user categories.
 */
export async function fetchCategories(): Promise<ApiResponse<CategoryItem[]>> {
  try {
    const res = await authenticatedFetch('/v1/categories');
    if (!res.ok) {
      return { success: false, status: res.status, error: `Erreur (${res.status})` };
    }
    const data = (await res.json()) as CategoryItem[];
    return { success: true, data: Array.isArray(data) ? data : [], status: 200 };
  } catch {
    return { success: false, error: 'Connexion réseau impossible' };
  }
}

/**
 * Aggregates all dashboard resources in parallel.
 * Tolerant to individual endpoint failures so an empty state or partial response
 * is safely resolved without throwing or displaying unverified information.
 */
export async function fetchFullDashboardData(
  customRange?: { startDate: string; endDate: string }
): Promise<ApiResponse<DashboardData>> {
  const currentMonth = getCurrentMonthDateRange();
  const startDate = customRange?.startDate ?? currentMonth.startDate;
  const endDate = customRange?.endDate ?? currentMonth.endDate;

  try {
    const [summaryRes, spendingRes, trendRes, accountsRes, transactionsRes, categoriesRes] =
      await Promise.allSettled([
        fetchDashboardSummary(startDate, endDate),
        fetchSpendingByCategory(startDate, endDate),
        fetchMonthlyTrend(6),
        fetchAccounts(),
        fetchRecentTransactions(5),
        fetchCategories(),
      ]);

    const summary = summaryRes.status === 'fulfilled' && summaryRes.value.success ? summaryRes.value.data ?? null : null;
    const spendingByCategory = spendingRes.status === 'fulfilled' && spendingRes.value.success ? spendingRes.value.data ?? [] : [];
    const monthlyTrend = trendRes.status === 'fulfilled' && trendRes.value.success ? trendRes.value.data ?? [] : [];
    const accounts = accountsRes.status === 'fulfilled' && accountsRes.value.success ? accountsRes.value.data ?? [] : [];
    const rawTransactions = transactionsRes.status === 'fulfilled' && transactionsRes.value.success ? transactionsRes.value.data ?? [] : [];
    const categories = categoriesRes.status === 'fulfilled' && categoriesRes.value.success ? categoriesRes.value.data ?? [] : [];

    // Map category names to transactions if available
    const categoryMap = new Map<string, string>();
    categories.forEach((cat) => {
      if (cat.id) categoryMap.set(cat.id, cat.name);
    });

    const recentTransactions = rawTransactions.map((tx) => ({
      ...tx,
      categoryName: tx.categoryId ? categoryMap.get(tx.categoryId) ?? null : null,
    }));

    // If transactions count is 0 and accounts count is 0, user has no data yet
    const hasAnyData =
      (accounts.length > 0) ||
      (summary !== null && summary.transactionsCount > 0) ||
      recentTransactions.length > 0;

    return {
      success: true,
      data: {
        summary,
        spendingByCategory,
        monthlyTrend,
        accounts,
        recentTransactions,
        categories,
        hasAnyData,
        period: {
          startDate,
          endDate,
          monthName: currentMonth.monthName,
        },
      },
    };
  } catch (err) {
    return {
      success: false,
      error: err instanceof Error ? err.message : 'Erreur lors du chargement du tableau de bord',
    };
  }
}
