/**
 * Centralized, bilingual localization dictionary for the Eparly Activity / Transactions screen.
 * Resolves the previous mixed-language issue by providing clean, context-appropriate strings
 * based on the active locale ('fr-FR' vs 'en-IE' / 'en-US').
 */

export interface ActivityCopyStrings {
  title: string;
  operationsRecorded: string;
  operationsOf: string;
  importCsv: string;
  searchPlaceholder: string;
  filterButtonLabel: string;
  allFilter: string;
  clearSearch: string;
  resetFilters: string;
  showAllCategories: string;
  period: string;
  categories: string;
  applyFilters: string;
  noMatchingTitle: string;
  noMatchingSubtitle: string;
  noActivityTitle: string;
  noActivitySubtitle: string;
  importCsvAction: string;
  transactionDetailTitle: string;
  dateLabel: string;
  typeLabel: string;
  statusLabel: string;
  statusCleared: string;
  accountLabel: string;
  mainAccount: string;
  rawLabel: string;
  subscriptionBadge: string;
  subscriptionType: string;
  incomeType: string;
  expenseType: string;
  transferType: string;
  feeType: string;
  refundType: string;
  discoverAlternative: string;
  copyRawLabel: string;
  copiedRawLabel: string;
  close: string;
  durationAll: string;
  duration30Days: string;
  duration3Months: string;
  durationThisYear: string;
  shortAll: string;
  short30Days: string;
  short3Months: string;
  shortThisYear: string;
}

const STRINGS: Record<'fr' | 'en', ActivityCopyStrings> = {
  fr: {
    title: 'Activité',
    operationsRecorded: 'opérations',
    operationsOf: 'sur',
    importCsv: 'Importer CSV',
    searchPlaceholder: 'Rechercher commerçants, notes, montants...',
    filterButtonLabel: 'Filtrer les opérations',
    allFilter: 'Tout',
    clearSearch: 'Effacer la recherche',
    resetFilters: 'Réinitialiser',
    showAllCategories: 'Toutes les catégories',
    period: 'PÉRIODE',
    categories: 'CATÉGORIES',
    applyFilters: 'Afficher les opérations',
    noMatchingTitle: 'Aucune transaction trouvée',
    noMatchingSubtitle: 'Modifiez vos filtres ou votre recherche pour trouver des opérations.',
    noActivityTitle: 'Aucune transaction enregistrée',
    noActivitySubtitle: 'Importez votre relevé bancaire CSV pour suivre vos dépenses et vos revenus.',
    importCsvAction: 'Importer un fichier CSV',
    transactionDetailTitle: "Détail de l'opération",
    dateLabel: "Date d'opération",
    typeLabel: "Type d'opération",
    statusLabel: 'Statut',
    statusCleared: 'Validé',
    accountLabel: 'Compte',
    mainAccount: 'Compte courant',
    rawLabel: 'Libellé bancaire brut',
    subscriptionBadge: 'Abonnement',
    subscriptionType: 'Abonnement récurrent',
    incomeType: 'Revenu crédité',
    expenseType: 'Dépense',
    transferType: 'Virement',
    feeType: 'Frais bancaires',
    refundType: 'Remboursement',
    discoverAlternative: 'Découvrir une alternative moins chère',
    copyRawLabel: 'Copier',
    copiedRawLabel: 'Copié',
    close: 'Fermer',
    durationAll: 'Toutes les dates',
    duration30Days: '30 derniers jours',
    duration3Months: '3 derniers mois',
    durationThisYear: 'Cette année',
    shortAll: 'Toutes',
    short30Days: '30 jours',
    short3Months: '3 mois',
    shortThisYear: 'Cette année',
  },
  en: {
    title: 'Activity',
    operationsRecorded: 'operations',
    operationsOf: 'of',
    importCsv: 'Import CSV',
    searchPlaceholder: 'Search merchants, notes, amounts...',
    filterButtonLabel: 'Filter transactions',
    allFilter: 'All',
    clearSearch: 'Clear search',
    resetFilters: 'Reset',
    showAllCategories: 'All categories',
    period: 'PERIOD',
    categories: 'CATEGORIES',
    applyFilters: 'Show operations',
    noMatchingTitle: 'No transactions found',
    noMatchingSubtitle: 'Try adjusting your search terms or filters to find operations.',
    noActivityTitle: 'No transactions recorded',
    noActivitySubtitle: 'Import your bank statement CSV to track your spending and income.',
    importCsvAction: 'Import a CSV file',
    transactionDetailTitle: 'Transaction Details',
    dateLabel: 'Booking date',
    typeLabel: 'Transaction type',
    statusLabel: 'Status',
    statusCleared: 'Cleared',
    accountLabel: 'Account',
    mainAccount: 'Primary account',
    rawLabel: 'Raw bank description',
    subscriptionBadge: 'Subscription',
    subscriptionType: 'Recurring subscription',
    incomeType: 'Credit / Income',
    expenseType: 'Debit / Expense',
    transferType: 'Transfer',
    feeType: 'Bank fee',
    refundType: 'Refund',
    discoverAlternative: 'Discover cheaper alternatives',
    copyRawLabel: 'Copy',
    copiedRawLabel: 'Copied',
    close: 'Close',
    durationAll: 'All dates',
    duration30Days: 'Last 30 days',
    duration3Months: 'Last 3 months',
    durationThisYear: 'This year',
    shortAll: 'All',
    short30Days: '30 days',
    short3Months: '3 months',
    shortThisYear: 'This year',
  },
};

/**
 * Helper to fetch localized Activity strings matching the specified locale.
 * Defaults to 'en' when using 'en-IE' or general international configurations.
 */
export function getActivityCopy(locale = 'en-IE'): ActivityCopyStrings {
  const isFrench = locale.toLowerCase().startsWith('fr');
  return isFrench ? STRINGS.fr : STRINGS.en;
}

