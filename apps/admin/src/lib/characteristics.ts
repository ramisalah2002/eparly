// Lecture défensive du champ `characteristics` (objet JSON sérialisé, extensible par secteur).

export interface CharacteristicEntry {
  key: string;
  value: string;
  warning: string | null;
}

export interface CharacteristicsView {
  entries: CharacteristicEntry[];
  parseError: boolean;
}

/** Valeur sentinelle utilisée par certains imports pour signifier « illimité » (ex. Free Max). */
const SENTINEL_THRESHOLD = 9999;
const SENTINEL_WARNING =
  "Donnée incohérente : valeur sentinelle probable (pseudo-illimité). Ne pas afficher telle quelle.";

function warningFor(key: string, value: unknown): string | null {
  if (typeof value === "number" && value >= SENTINEL_THRESHOLD && /data|gb/i.test(key)) {
    return SENTINEL_WARNING;
  }
  if (typeof value === "boolean" && /5g|network/i.test(key)) {
    return "Caractéristique réseau à revalider sur preuve.";
  }
  return null;
}

function display(value: unknown): string {
  if (value === null || value === undefined) return "Donnée inconnue";
  if (typeof value === "boolean") return value ? "Oui" : "Non";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

export function parseCharacteristics(raw: string | null): CharacteristicsView {
  if (!raw || !raw.trim()) return { entries: [], parseError: false };
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return { entries: [], parseError: true };
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return { entries: [], parseError: true };
  const entries = Object.entries(parsed as Record<string, unknown>).map(([key, value]) => {
    const warning = warningFor(key, value);
    return { key, value: warning === SENTINEL_WARNING ? "Donnée incohérente" : display(value), warning };
  });
  return { entries, parseError: false };
}
