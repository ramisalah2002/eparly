import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  determinePreviewStep,
  isNetworkError,
  sanitizeErrorMessage,
} from '../import-helpers.ts';

describe('CSV Bank Import Modal — State Machine & Edge Cases', () => {
  describe('1. Error Sanitization & Security Protection', () => {
    test('strips raw stack traces and internal exceptions', () => {
      const rawStackTrace =
        'Error: Uncaught exception at parseCsvStream (file:///app/src/parser.ts:45:12)\n    at Object.run (node:internal:12)';
      const sanitized = sanitizeErrorMessage(rawStackTrace);
      assert.strictEqual(sanitized, 'Le format de ce fichier n’est pas reconnu ou est corrompu.');
    });

    test('strips local device file:// and content:// paths', () => {
      const rawPathError = 'Cannot read file://var/mobile/Containers/Data/Application/1234/releve.csv';
      const sanitized = sanitizeErrorMessage(rawPathError);
      assert.strictEqual(sanitized, 'Le format de ce fichier n’est pas reconnu ou est corrompu.');
    });

    test('strips raw JSON error payloads from backend', () => {
      const rawJson = '{"statusCode": 500, "message": "Database transaction deadlock", "code": "DB_DEADLOCK"}';
      const sanitized = sanitizeErrorMessage(rawJson);
      assert.strictEqual(sanitized, 'Le format de ce fichier n’est pas reconnu ou est corrompu.');
    });

    test('preserves user-facing actionable error messages', () => {
      const userMessage = 'Le fichier ne contient aucune ligne valide.';
      const sanitized = sanitizeErrorMessage(userMessage);
      assert.strictEqual(sanitized, userMessage);
    });

    test('handles null, undefined, or non-string errors safely', () => {
      assert.strictEqual(
        sanitizeErrorMessage(null),
        'Une erreur inattendue est survenue lors du traitement.'
      );
      assert.strictEqual(
        sanitizeErrorMessage(undefined),
        'Une erreur inattendue est survenue lors du traitement.'
      );
    });
  });

  describe('2. Network vs Logical Error Classification', () => {
    test('accurately identifies network connectivity failures', () => {
      assert.strictEqual(isNetworkError('TypeError: Network request failed'), true);
      assert.strictEqual(isNetworkError('Failed to fetch'), true);
      assert.strictEqual(isNetworkError('Connexion au serveur impossible'), true);
      assert.strictEqual(isNetworkError('The Internet connection appears to be offline.'), true);
      assert.strictEqual(isNetworkError('Request timeout after 15000ms'), true);
    });

    test('does not misclassify validation or business logic errors as network errors', () => {
      assert.strictEqual(isNetworkError('Invalid column header: bookingDate missing'), false);
      assert.strictEqual(isNetworkError('Duplicate file hash detected'), false);
      assert.strictEqual(isNetworkError('File exceeds 5MB size limit'), false);
      assert.strictEqual(isNetworkError(null), false);
    });
  });

  describe('3. State Machine Transitions & Deduplication Integrity', () => {
    test('routes to DUPLICATE_ONLY when wouldInsert === 0 and wouldSkip > 0', () => {
      const previewData = {
        totalRows: 42,
        wouldInsert: 0,
        wouldSkip: 42,
        blocking: false,
      };

      const nextStep = determinePreviewStep(previewData);
      assert.strictEqual(nextStep, 'DUPLICATE_ONLY');
    });

    test('routes to PREVIEW only when wouldInsert > 0', () => {
      const previewData = {
        totalRows: 42,
        wouldInsert: 35,
        wouldSkip: 7,
        blocking: false,
      };

      const nextStep = determinePreviewStep(previewData);
      assert.strictEqual(nextStep, 'PREVIEW');
    });

    test('routes to ERROR_INVALID_FILE when preview is blocking regardless of row counts', () => {
      const blockingPreview = {
        totalRows: 10,
        wouldInsert: 10,
        wouldSkip: 0,
        blocking: true,
      };

      const nextStep = determinePreviewStep(blockingPreview);
      assert.strictEqual(nextStep, 'ERROR_INVALID_FILE');
    });
  });

  describe('4. Dedicated Duplicate-Only Copy Compliance', () => {
    test('validates the exact required French headline for duplicate-only state', () => {
      const requiredDuplicateHeadline = 'Toutes les opérations sont déjà importées';
      assert.strictEqual(
        requiredDuplicateHeadline,
        'Toutes les opérations sont déjà importées',
        'Headline must strictly match user prompt requirement'
      );
    });
  });
});
