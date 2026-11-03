/**
 * Sanitizes raw error strings to prevent exposing internal stack traces,
 * file paths, or backend exceptions in the user-facing interface.
 */
export function sanitizeErrorMessage(rawMessage?: string | null): string {
  if (!rawMessage || typeof rawMessage !== 'string') {
    return 'Une erreur inattendue est survenue lors du traitement.';
  }

  const clean = rawMessage.trim();

  // Strip stack traces, native file paths, or JSON dumps
  if (
    clean.includes('at ') ||
    clean.includes('Error:') ||
    clean.includes('file://') ||
    clean.includes('content://') ||
    clean.includes('{')
  ) {
    return 'Le format de ce fichier n’est pas reconnu ou est corrompu.';
  }

  return clean;
}

/**
 * Identifies network or connectivity disruptions.
 */
export function isNetworkError(error?: string | null): boolean {
  if (!error) return false;
  const lower = error.toLowerCase();
  return (
    lower.includes('network') ||
    lower.includes('connexion') ||
    lower.includes('fetch') ||
    lower.includes('offline') ||
    lower.includes('timeout') ||
    lower.includes('failed to fetch') ||
    lower.includes('internet')
  );
}

/**
 * Determines next state machine step given preview data and blocking status.
 */
export function determinePreviewStep(preview: {
  blocking: boolean;
  wouldInsert: number;
  wouldSkip?: number;
}): 'ERROR_INVALID_FILE' | 'DUPLICATE_ONLY' | 'PREVIEW' {
  if (preview.blocking) {
    return 'ERROR_INVALID_FILE';
  }
  if (preview.wouldInsert === 0) {
    return 'DUPLICATE_ONLY';
  }
  return 'PREVIEW';
}

