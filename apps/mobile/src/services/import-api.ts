import { Platform } from 'react-native';
import { authenticatedFetch } from './api-client';
import { AccountItem, ApiResponse, fetchAccounts } from './dashboard-api';

export interface CsvFilePayload {
  uri: string;
  name: string;
  mimeType?: string;
  size?: number;
  fileObj?: File | Blob;
}

export interface CsvSuggestedMapping {
  dateColumn: string;
  valueDateColumn?: string | null;
  labelColumn: string;
  amountColumn?: string | null;
  debitColumn?: string | null;
  creditColumn?: string | null;
  currencyColumn?: string | null;
  sourceIdColumn?: string | null;
  categoryColumn?: string | null;
  subcategoryColumn?: string | null;
  dateFormat: string;
  amountFormat: string;
}

export interface ImportIssue {
  line: number;
  column?: string | null;
  code: string;
  message: string;
}

export interface ImportSampleRow {
  line: number;
  bookingDate: string;
  valueDate?: string | null;
  amountMinor: number;
  currency: string;
  label: string;
}

export interface ImportPreviewResponse {
  fileHash: string;
  detectedCharset?: string;
  charsetAmbiguous?: boolean;
  charsetCandidates?: string[];
  detectedDelimiter?: string;
  headers?: string[];
  suggestedMapping?: CsvSuggestedMapping;
  appliedMapping?: CsvSuggestedMapping;
  mappingComplete: boolean;
  totalRows: number;
  wouldInsert: number;
  wouldSkip: number;
  blocking: boolean;
  sample?: ImportSampleRow[];
  errors?: ImportIssue[];
  warnings?: ImportIssue[];
  issueCount?: number;
}

export interface ImportJobDetails {
  id: string;
  accountId: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  totalRows: number;
  insertedRows: number;
  skippedRows: number;
  failedRows: number;
  createdAt: string;
  completedAt?: string | null;
  errorSummary?: string | null;
}

export interface ImportResultResponse {
  importJob: ImportJobDetails;
  warnings?: ImportIssue[];
}

const B64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function base64ToUint8Array(base64: string): Uint8Array {
  if (typeof atob === 'function') {
    const bin = atob(base64);
    const u8 = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      u8[i] = bin.charCodeAt(i);
    }
    return u8;
  }
  const clean = base64.replace(/[\r\n\s=]/g, '');
  const len = clean.length;
  const byteLen = Math.floor((len * 3) / 4);
  const u8 = new Uint8Array(byteLen);
  let p = 0;
  for (let i = 0; i < len; i += 4) {
    const enc1 = B64_CHARS.indexOf(clean.charAt(i));
    const enc2 = B64_CHARS.indexOf(clean.charAt(i + 1));
    const enc3 = B64_CHARS.indexOf(clean.charAt(i + 2));
    const enc4 = B64_CHARS.indexOf(clean.charAt(i + 3));

    const chr1 = (enc1 << 2) | (enc2 >> 4);
    const chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
    const chr3 = ((enc3 & 3) << 6) | enc4;

    u8[p++] = chr1;
    if (enc3 !== -1 && p < byteLen) u8[p++] = chr2;
    if (enc4 !== -1 && p < byteLen) u8[p++] = chr3;
  }
  return u8;
}

function readUriViaXhr(uri: string): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.onload = () => {
      if (xhr.response) {
        if (xhr.response instanceof ArrayBuffer) {
          resolve(new Uint8Array(xhr.response));
          return;
        }
        if (typeof Blob !== 'undefined' && xhr.response instanceof Blob) {
          const reader = new FileReader();
          reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
          reader.onerror = reject;
          reader.readAsArrayBuffer(xhr.response);
          return;
        }
      }
      reject(new Error('Réponse vide lors de la lecture du fichier'));
    };
    xhr.onerror = (e) => reject(new Error(`Échec de lecture XHR du fichier: ${e}`));
    xhr.responseType = 'arraybuffer';
    xhr.open('GET', uri, true);
    xhr.send(null);
  });
}

/**
 * Reads local file bytes reliably across iOS, Android, and Web platforms.
 */
export async function readFileAsBytes(file: CsvFilePayload): Promise<Uint8Array> {
  // 1. Web File or Blob already present
  if (file.fileObj && typeof Blob !== 'undefined' && file.fileObj instanceof Blob) {
    const arrayBuffer = await file.fileObj.arrayBuffer();
    return new Uint8Array(arrayBuffer);
  }

  // 2. expo-file-system legacy readAsStringAsync (Base64) - most battle-tested on React Native
  try {
    const FileSystemLegacy = await import('expo-file-system/legacy');
    if (FileSystemLegacy?.readAsStringAsync && file.uri) {
      const base64 = await FileSystemLegacy.readAsStringAsync(file.uri, {
        encoding: FileSystemLegacy.EncodingType.Base64,
      });
      if (base64) {
        return base64ToUint8Array(base64);
      }
    }
  } catch {
    // Fall through
  }

  // 3. New expo-file-system File class (Expo SDK 58+)
  try {
    const FileSystem = await import('expo-file-system');
    if (FileSystem?.File && file.uri) {
      const expoFile = new FileSystem.File(file.uri);
      if (typeof expoFile.bytes === 'function') {
        const bytes = await expoFile.bytes();
        if (bytes && bytes.length > 0) {
          return bytes;
        }
      }
    }
  } catch {
    // Fall through
  }

  // 4. XMLHttpRequest for local file:// and content:// URIs in React Native
  try {
    if (file.uri) {
      return await readUriViaXhr(file.uri);
    }
  } catch {
    // Fall through
  }

  // 5. Standard fetch(file.uri)
  try {
    if (file.uri) {
      const res = await fetch(file.uri);
      const arrayBuffer = await res.arrayBuffer();
      return new Uint8Array(arrayBuffer);
    }
  } catch {
    // Fall through
  }

  throw new Error(`Impossible de lire le contenu du fichier: ${file.name}`);
}

/**
 * Appends a file to FormData in a way that is compatible with:
 * - Expo SDK 58 WinterCG Fetch (which throws "Unsupported FormDataPart implementation" on legacy {uri, name, type})
 * - Web File/Blob standard
 * - React Native environments
 */
export async function appendFileToFormData(form: FormData, file: CsvFilePayload): Promise<void> {
  const bytes = await readFileAsBytes(file);
  const fileName = file.name || 'releve.csv';
  const mimeType = file.mimeType || 'text/csv';

  // 1. On Web, standard browser FormData expects a native Blob/File instance
  if (Platform.OS === 'web' || (typeof window !== 'undefined' && typeof window.document !== 'undefined')) {
    const blob = new Blob([bytes as unknown as BlobPart], { type: mimeType });
    form.append('file', blob, fileName);
    return;
  }

  // 2. On React Native (iOS / Android), Expo's WinterCG fetch (convertFormDataAsync) requires:
  // - either an instance of standard Blob
  // - or an object with a .bytes() method returning a Uint8Array
  // Note: legacy RN { uri, name, type } throws "Unsupported FormDataPart implementation" in Expo SDK 58.
  // The object below satisfies both getFormDataPartHeaders (reads .name, .type)
  // and convertFormDataAsync (reads .bytes()), avoiding the error completely.
  const part = {
    name: fileName,
    type: mimeType,
    bytes: () => bytes,
    arrayBuffer: async () => bytes.buffer,
  };

  form.append('file', part as any);
}

/**
 * Creates a default CSV bank account if user does not have one yet.
 */
export async function createAccount(
  name = 'Compte principal',
  currency = 'EUR'
): Promise<ApiResponse<AccountItem>> {
  try {
    const res = await authenticatedFetch('/v1/accounts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name.trim(), currency: currency.trim() }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { success: false, status: res.status, error: err.message || `Erreur (${res.status})` };
    }

    const data = (await res.json()) as AccountItem;
    return { success: true, data, status: 201 };
  } catch {
    return { success: false, error: 'Connexion réseau impossible' };
  }
}

/**
 * Ensures the user has at least one account for CSV imports.
 */
export async function getOrCreateDefaultAccount(): Promise<ApiResponse<AccountItem>> {
  const accountsRes = await fetchAccounts();
  if (accountsRes.success && accountsRes.data && accountsRes.data.length > 0) {
    return { success: true, data: accountsRes.data[0], status: 200 };
  }
  return createAccount('Compte principal', 'EUR');
}

/**
 * Previews a CSV file without saving transactions to database.
 */
export async function previewCsvImport(
  accountId: string,
  file: CsvFilePayload,
  mapping?: CsvSuggestedMapping
): Promise<ApiResponse<ImportPreviewResponse>> {
  try {
    const form = new FormData();
    form.append('accountId', accountId);

    await appendFileToFormData(form, file);

    if (mapping) {
      form.append('mapping', JSON.stringify(mapping));
    }

    const res = await authenticatedFetch('/v1/imports/preview', {
      method: 'POST',
      body: form,
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return {
        success: false,
        status: res.status,
        error: err.message || `Erreur lors de l'analyse du CSV (${res.status})`,
      };
    }

    const data = (await res.json()) as ImportPreviewResponse;
    return { success: true, data, status: 200 };
  } catch (err) {
    return {
      success: false,
      error: err instanceof Error ? err.message : 'Erreur de connexion lors de la prévisualisation',
    };
  }
}

/**
 * Confirms and executes atomic import with the validated file hash and mapping.
 */
export async function confirmCsvImport(params: {
  accountId: string;
  file: CsvFilePayload;
  fileHash: string;
  mapping: CsvSuggestedMapping;
  charset?: string;
}): Promise<ApiResponse<ImportResultResponse>> {
  try {
    const form = new FormData();
    form.append('accountId', params.accountId);
    form.append('fileHash', params.fileHash);
    form.append('mapping', JSON.stringify(params.mapping));

    if (params.charset) {
      form.append('charset', params.charset);
    }

    await appendFileToFormData(form, params.file);

    const res = await authenticatedFetch('/v1/imports', {
      method: 'POST',
      body: form,
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return {
        success: false,
        status: res.status,
        error: err.message || `Erreur lors de l'import des transactions (${res.status})`,
      };
    }

    const data = (await res.json()) as ImportResultResponse;
    return { success: true, data, status: 201 };
  } catch (err) {
    return {
      success: false,
      error: err instanceof Error ? err.message : "Erreur de connexion lors de l'import",
    };
  }
}
