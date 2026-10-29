/**
 * Formatting and date utility helpers for Eparly Mobile.
 * Adheres strictly to minor-unit financial rules (EUR cents).
 */

export interface FormatCurrencyOptions {
  showSign?: boolean;
  hideDecimals?: boolean;
  masked?: boolean;
}

/**
 * Formats an amount in minor units (cents) into a localized currency string.
 * Example: 12500 cents -> "125,00 €"
 * If amountMinor is null/undefined, returns "— €".
 */
export function formatCurrencyMinor(
  amountMinor: number | null | undefined,
  currency = 'EUR',
  options: FormatCurrencyOptions = {}
): string {
  if (options.masked) {
    return '•••• €';
  }

  if (amountMinor === null || amountMinor === undefined || isNaN(amountMinor)) {
    return `— ${currency === 'EUR' ? '€' : currency}`;
  }

  const isNegative = amountMinor < 0;
  const absMinor = Math.abs(amountMinor);
  const majorUnits = absMinor / 100;

  // Format with french locale spacing standard for fintech apps in EU
  const formattedNumber = new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: options.hideDecimals ? 0 : 2,
    maximumFractionDigits: options.hideDecimals ? 0 : 2,
  }).format(majorUnits);

  const symbol = currency === 'EUR' ? '€' : currency;

  if (options.showSign) {
    const sign = isNegative ? '-' : '+';
    return `${sign}${formattedNumber} ${symbol}`;
  }

  const prefix = isNegative ? '-' : '';
  return `${prefix}${formattedNumber} ${symbol}`;
}

/**
 * Formats an ISO date string (YYYY-MM-DD or full timestamp) into a human-friendly string.
 * E.g., "2026-10-24" -> "24 oct. 2026"
 */
export function formatDateDisplay(
  dateStr: string | null | undefined,
  options: { includeYear?: boolean; relative?: boolean } = {}
): string {
  if (!dateStr) return '—';

  try {
    const date = new Date(dateStr.length === 10 ? `${dateStr}T00:00:00` : dateStr);
    if (isNaN(date.getTime())) return dateStr;

    if (options.relative) {
      const today = new Date();
      const isToday =
        date.getDate() === today.getDate() &&
        date.getMonth() === today.getMonth() &&
        date.getFullYear() === today.getFullYear();

      if (isToday) return "Aujourd'hui";

      const yesterday = new Date();
      yesterday.setDate(today.getDate() - 1);
      const isYesterday =
        date.getDate() === yesterday.getDate() &&
        date.getMonth() === yesterday.getMonth() &&
        date.getFullYear() === yesterday.getFullYear();

      if (isYesterday) return 'Hier';
    }

    return new Intl.DateTimeFormat('fr-FR', {
      day: 'numeric',
      month: 'short',
      year: options.includeYear ? 'numeric' : undefined,
    }).format(date);
  } catch {
    return dateStr;
  }
}

/**
 * Returns the boundary dates for the current calendar month in ISO-8601 (YYYY-MM-DD).
 */
export function getCurrentMonthDateRange(referenceDate = new Date()): {
  startDate: string;
  endDate: string;
  monthName: string;
} {
  const year = referenceDate.getFullYear();
  const month = referenceDate.getMonth(); // 0-indexed

  const start = new Date(year, month, 1);
  const end = new Date(year, month + 1, 0); // Last day of month

  const pad = (n: number) => String(n).padStart(2, '0');

  const startDate = `${year}-${pad(month + 1)}-01`;
  const endDate = `${year}-${pad(month + 1)}-${pad(end.getDate())}`;

  const monthName = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(start);

  return { startDate, endDate, monthName };
}

/**
 * Contextual greeting based on the hour of the day.
 */
export function getTimeBasedGreeting(date = new Date()): string {
  const hour = date.getHours();
  if (hour >= 5 && hour < 12) {
    return 'Bonjour';
  }
  if (hour >= 12 && hour < 18) {
    return 'Bon après-midi';
  }
  return 'Bonsoir';
}

/**
 * Extracts the user's first name if available, otherwise returns null.
 */
export function getUserFirstName(fullName?: string | null): string | null {
  if (!fullName) return null;
  const trimmed = fullName.trim();
  if (!trimmed) return null;
  const parts = trimmed.split(/\s+/);
  return parts[0] || null;
}

/**
 * Generates 1-2 avatar initials from full name or email.
 */
export function getAvatarInitials(fullName?: string | null, email?: string | null): string {
  if (fullName && fullName.trim()) {
    const parts = fullName.trim().split(/\s+/);
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
    }
    return parts[0].slice(0, 2).toUpperCase();
  }
  if (email && email.trim()) {
    return email.slice(0, 2).toUpperCase();
  }
  return 'EP';
}
