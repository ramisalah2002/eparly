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

/**
 * Natural capitalization helper (e.g. "RAMI" -> "Rami", "jean-luc" -> "Jean-Luc")
 */
export function formatNaturalCapitalization(name?: string | null): string {
  if (!name || !name.trim()) return '';
  return name
    .trim()
    .toLowerCase()
    .replace(/(?:^|[\s-])\p{L}/gu, (char) => char.toUpperCase());
}

/**
 * Formats date into a clean, editorial uppercase contextual date (e.g. "SUN, 1 NOV")
 */
export function formatHeaderContextDate(date = new Date()): string {
  try {
    const weekday = new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(date);
    const day = date.getDate();
    const month = new Intl.DateTimeFormat('en-US', { month: 'short' }).format(date);
    return `${weekday}, ${day} ${month}`.toUpperCase();
  } catch {
    return 'TODAY';
  }
}

/**
 * Formats date into a refined editorial natural-cased contextual date (e.g. "Monday, 2 November")
 */
export function formatMonographHeaderDate(date = new Date()): string {
  try {
    const weekday = new Intl.DateTimeFormat('en-US', { weekday: 'long' }).format(date);
    const day = date.getDate();
    const month = new Intl.DateTimeFormat('en-US', { month: 'long' }).format(date);
    return `${weekday}, ${day} ${month}`;
  } catch {
    return 'Today';
  }
}

/**
 * Sanitizes initials: removes emojis/special characters and limits to 2 characters
 */
export function sanitizeInitials(initials?: string | null): string {
  if (!initials || !initials.trim()) return 'EP';
  const cleaned = initials.replace(/[^\p{L}\p{N}]/gu, '').slice(0, 2).toUpperCase();
  return cleaned || 'EP';
}

export interface FormattedMoneyParts {
  sign: string;
  prefixCurrency: string;
  integerText: string;
  decimalText: string;
  suffixCurrency: string;
  fullFormatted: string;
}

export interface ParseMoneyPartsOptions {
  showSign?: boolean;
  hideDecimals?: boolean;
}

/**
 * Splits a minor-unit financial amount into localized typographical parts:
 * prefix currency, integer with group separators, decimal with fraction, and suffix currency.
 * Eliminates floating point rounding issues for monetary precision.
 */
export function parseMoneyParts(
  amountMinor: number | null | undefined,
  currency = 'EUR',
  locale = 'en-IE',
  options: ParseMoneyPartsOptions = {}
): FormattedMoneyParts {
  if (amountMinor === null || amountMinor === undefined || isNaN(amountMinor)) {
    return {
      sign: '',
      prefixCurrency: '',
      integerText: '—',
      decimalText: '',
      suffixCurrency: ` ${currency}`,
      fullFormatted: `— ${currency}`,
    };
  }

  const isNegative = amountMinor < 0;
  const absMinor = Math.abs(amountMinor);
  const major = absMinor / 100;
  const hideDecimals = Boolean(options.hideDecimals);

  const formatter = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: hideDecimals ? 0 : 2,
    maximumFractionDigits: hideDecimals ? 0 : 2,
  });

  let prefixCurrency = '';
  let integerText = '';
  let decimalText = '';
  let suffixCurrency = '';

  // Hermes (React Native) does not implement Intl.NumberFormat.prototype.formatToParts.
  // Use formatToParts if available, otherwise safely parse the standard format() output.
  if (typeof formatter.formatToParts === 'function') {
    const parts = formatter.formatToParts(major);
    let foundDecimal = false;
    let foundInteger = false;

    for (const part of parts) {
      if (part.type === 'currency') {
        if (!foundInteger) {
          prefixCurrency += part.value;
        } else {
          suffixCurrency += part.value;
        }
      } else if (part.type === 'integer' || part.type === 'group') {
        foundInteger = true;
        integerText += part.value;
      } else if (part.type === 'decimal' || part.type === 'fraction') {
        foundDecimal = true;
        decimalText += part.value;
      } else if (part.type === 'literal') {
        if (foundDecimal || hideDecimals) {
          suffixCurrency += part.value;
        }
      }
    }
  } else {
    // Hermes engine fallback
    const formatted = formatter.format(major);
    if (hideDecimals) {
      const match = formatted.match(/^([^\d\n\r]*?)((?:\d[^\d\n\r]?)*\d+)([^\d\n\r]*)$/);
      if (match) {
        prefixCurrency = match[1];
        integerText = match[2];
        decimalText = '';
        suffixCurrency = match[3];
      } else {
        integerText = formatted;
      }
    } else {
      const match = formatted.match(/^([^\d\n\r]*?)((?:\d[^\d\n\r]?)*\d+)([.,])(\d{2})([^\d\n\r]*)$/);
      if (match) {
        prefixCurrency = match[1];
        integerText = match[2];
        decimalText = match[3] + match[4];
        suffixCurrency = match[5];
      } else {
        integerText = formatted;
      }
    }
  }

  let sign = '';
  if (isNegative) {
    sign = '-';
  } else if (options.showSign && amountMinor > 0) {
    sign = '+';
  }

  const fullFormatted = `${sign}${formatter.format(major)}`;

  return {
    sign,
    prefixCurrency,
    integerText,
    decimalText,
    suffixCurrency,
    fullFormatted,
  };
}

/**
 * Computes the percentage share of an amount relative to a total amount safely.
 * Returns a 1-decimal string (e.g. "30.5") and guards against division by zero.
 */
export function computeCategoryShare(amountMinor: number, totalMinor: number): string {
  if (!totalMinor || totalMinor <= 0) return '0.0';
  return ((amountMinor / totalMinor) * 100).toFixed(1);
}

export interface TransactionDateGroup<T> {
  id: string;
  title: string;
  dateKey: string;
  transactions: T[];
}

export interface GroupTransactionsOptions {
  referenceDate?: Date | string;
  locale?: string;
}

/**
 * Groups transactions into editorial calendar date sections ("Today", "Yesterday", or "29 October").
 * Accurately parses transaction calendar dates (YYYY-MM-DD or bookingDate) without timezone shifts,
 * groups transactions from the same day under a single heading, and eliminates fake dates.
 */
export function groupTransactionsByDate<
  T extends { date?: string; bookingDate?: string; dateLabel?: string }
>(
  transactions: T[],
  options: GroupTransactionsOptions = {}
): TransactionDateGroup<T>[] {
  if (!transactions || transactions.length === 0) {
    return [];
  }

  const locale = options.locale || 'en-IE';

  // Evaluate reference date relative to the user's current day
  const ref = options.referenceDate
    ? (typeof options.referenceDate === 'string' ? new Date(options.referenceDate) : options.referenceDate)
    : new Date();

  const refYear = ref.getFullYear();
  const refMonth = ref.getMonth();
  const refDay = ref.getDate();
  const refMidnight = new Date(refYear, refMonth, refDay).getTime();

  const groupsMap = new Map<string, TransactionDateGroup<T>>();

  for (const tx of transactions) {
    let year: number | null = null;
    let monthIndex: number | null = null; // 0-indexed
    let day: number | null = null;

    const rawDateStr = tx.date || tx.bookingDate;

    if (rawDateStr) {
      const match = rawDateStr.match(/^(\d{4})-(\d{2})-(\d{2})/);
      if (match) {
        year = parseInt(match[1], 10);
        monthIndex = parseInt(match[2], 10) - 1;
        day = parseInt(match[3], 10);
      } else {
        const parsed = new Date(rawDateStr);
        if (!isNaN(parsed.getTime())) {
          year = parsed.getFullYear();
          monthIndex = parsed.getMonth();
          day = parsed.getDate();
        }
      }
    } else if (tx.dateLabel) {
      const match = tx.dateLabel.match(/([A-Za-z]+)\s*(\d{1,2})/);
      if (match) {
        const monthNames = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
        const foundIndex = monthNames.findIndex((m) => match[1].toLowerCase().startsWith(m));
        if (foundIndex >= 0) {
          year = refYear;
          monthIndex = foundIndex;
          day = parseInt(match[2], 10);
        }
      }
    }

    if (year === null || monthIndex === null || day === null) {
      const undatedKey = 'undated';
      if (!groupsMap.has(undatedKey)) {
        groupsMap.set(undatedKey, {
          id: undatedKey,
          title: 'Recent',
          dateKey: undatedKey,
          transactions: [],
        });
      }
      groupsMap.get(undatedKey)!.transactions.push(tx);
      continue;
    }

    const txMidnight = new Date(year, monthIndex, day).getTime();
    const diffDays = Math.round((refMidnight - txMidnight) / (24 * 60 * 60 * 1000));
    const pad = (n: number) => String(n).padStart(2, '0');
    const dateKey = `${year}-${pad(monthIndex + 1)}-${pad(day)}`;

    if (!groupsMap.has(dateKey)) {
      let title = '';
      if (diffDays === 0) {
        title = 'Today';
      } else if (diffDays === 1) {
        title = 'Yesterday';
      } else if (diffDays === -1) {
        title = 'Tomorrow';
      } else {
        const dateObj = new Date(year, monthIndex, day);
        const formatOptions: Intl.DateTimeFormatOptions = {
          day: 'numeric',
          month: 'long',
          ...(year !== refYear ? { year: 'numeric' } : {}),
        };
        title = new Intl.DateTimeFormat(locale, formatOptions).format(dateObj);
      }

      groupsMap.set(dateKey, {
        id: dateKey,
        title,
        dateKey,
        transactions: [],
      });
    }

    groupsMap.get(dateKey)!.transactions.push(tx);
  }

  return Array.from(groupsMap.values());
}
