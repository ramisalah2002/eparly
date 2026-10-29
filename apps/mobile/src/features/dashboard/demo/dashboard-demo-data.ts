/**
 * EPARLY — Dashboard Static Demo Fixture
 * 
 * Provides consistent, highly realistic static financial demo data for the Home Dashboard UI.
 * This file serves as the single source of truth for the static prototype,
 * allowing effortless migration to live backend APIs in future milestones.
 */

export interface DemoUserProfile {
  name: string;
  avatarUrl?: string;
  initials: string;
}

export interface DemoBalanceOverview {
  totalBalanceMinor: number; // in cents (e.g. 845050 -> €8,450.50)
  currency: string;
  accountsCount: number;
  accountsLabel: string;
}

export interface DemoCashFlow {
  incomeMinor: number; // in cents (345000 -> €3,450.00)
  expensesMinor: number; // in cents (128000 -> €1,280.00)
  periodLabel: string;
}

export interface DemoMonthlySpending {
  monthKey: string;
  monthShort: string;
  monthFull: string;
  amountMinor: number;
  isSelected?: boolean;
}

export interface DemoCategoryItem {
  id: string;
  name: string;
  amountMinor: number;
  percentage: number;
  color: string;
}

export interface DemoTransaction {
  id: string;
  merchant: string;
  category: string;
  dateLabel: string;
  amountMinor: number; // signed cents (+ for income, - for expense)
  currency: string;
  iconName: 'shopping' | 'music' | 'briefcase' | 'play' | 'generic';
  iconBg: string;
}

export interface DashboardDemoData {
  user: DemoUserProfile;
  balance: DemoBalanceOverview;
  cashFlow: DemoCashFlow;
  spendingOverview: {
    periodTitle: string;
    months: DemoMonthlySpending[];
  };
  categoryBreakdown: {
    title: string;
    subtitle: string;
    totalExpensesMinor: number;
    categories: DemoCategoryItem[];
  };
  recentTransactions: {
    title: string;
    transactions: DemoTransaction[];
  };
}

export const DASHBOARD_DEMO_DATA: DashboardDemoData = {
  user: {
    name: 'Alex',
    initials: 'AL',
  },
  balance: {
    totalBalanceMinor: 845050, // €8,450.50
    currency: 'EUR',
    accountsCount: 2,
    accountsLabel: 'Across 2 accounts',
  },
  cashFlow: {
    incomeMinor: 345000, // €3,450.00
    expensesMinor: 128000, // €1,280.00
    periodLabel: 'October',
  },
  spendingOverview: {
    periodTitle: 'Last 6 months',
    months: [
      {
        monthKey: '2026-05',
        monthShort: 'May',
        monthFull: 'May 2026',
        amountMinor: 94000, // €940
      },
      {
        monthKey: '2026-06',
        monthShort: 'Jun',
        monthFull: 'June 2026',
        amountMinor: 115000, // €1,150
      },
      {
        monthKey: '2026-07',
        monthShort: 'Jul',
        monthFull: 'July 2026',
        amountMinor: 78000, // €780
      },
      {
        monthKey: '2026-08',
        monthShort: 'Aug',
        monthFull: 'August 2026',
        amountMinor: 142000, // €1,420
      },
      {
        monthKey: '2026-09',
        monthShort: 'Sep',
        monthFull: 'September 2026',
        amountMinor: 106000, // €1,060
      },
      {
        monthKey: '2026-10',
        monthShort: 'Oct',
        monthFull: 'October 2026',
        amountMinor: 128000, // €1,280 (Selected current demo month)
        isSelected: true,
      },
    ],
  },
  categoryBreakdown: {
    title: 'Where your money goes',
    subtitle: 'October spending',
    totalExpensesMinor: 128000, // €1,280.00
    categories: [
      {
        id: 'cat-shopping',
        name: 'Shopping',
        amountMinor: 39000, // €390.00
        percentage: 30.5,
        color: '#8067A1', // Deep Lavender
      },
      {
        id: 'cat-food',
        name: 'Food & Drinks',
        amountMinor: 31000, // €310.00
        percentage: 24.2,
        color: '#A89BBE', // Muted Lavender
      },
      {
        id: 'cat-bills',
        name: 'Bills',
        amountMinor: 28000, // €280.00
        percentage: 21.9,
        color: '#D8C3A8', // Warm Sand Beige
      },
      {
        id: 'cat-transport',
        name: 'Transport',
        amountMinor: 18000, // €180.00
        percentage: 14.1,
        color: '#59B99B', // Muted Mint Green
      },
      {
        id: 'cat-other',
        name: 'Other',
        amountMinor: 12000, // €120.00
        percentage: 9.4,
        color: '#E89A91', // Soft Coral
      },
    ],
  },
  recentTransactions: {
    title: 'Recent transactions',
    transactions: [
      {
        id: 'tx-carrefour',
        merchant: 'Carrefour',
        category: 'Groceries',
        dateLabel: 'Oct 29',
        amountMinor: -5280, // -€52.80
        currency: 'EUR',
        iconName: 'shopping',
        iconBg: '#F0ECF5',
      },
      {
        id: 'tx-spotify',
        merchant: 'Spotify',
        category: 'Subscription',
        dateLabel: 'Oct 28',
        amountMinor: -1099, // -€10.99
        currency: 'EUR',
        iconName: 'music',
        iconBg: '#F0ECF5',
      },
      {
        id: 'tx-salary',
        merchant: 'Salary',
        category: 'Income',
        dateLabel: 'Oct 25',
        amountMinor: 285000, // +€2,850.00
        currency: 'EUR',
        iconName: 'briefcase',
        iconBg: '#E4F6F0',
      },
      {
        id: 'tx-netflix',
        merchant: 'Netflix',
        category: 'Subscription',
        dateLabel: 'Oct 23',
        amountMinor: -1349, // -€13.49
        currency: 'EUR',
        iconName: 'play',
        iconBg: '#F0ECF5',
      },
    ],
  },
};
