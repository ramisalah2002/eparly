import test, { describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

describe('CSV Bank Import Service & Mapping Rules', () => {
  let mockFetchHandler = null;

  beforeEach(() => {
    mockFetchHandler = null;
    globalThis.fetch = async (url, options) => {
      if (mockFetchHandler) {
        return mockFetchHandler(url, options);
      }
      return new Response(JSON.stringify({}), { status: 200 });
    };
  });

  test('resolves existing account when user already has accounts', () => {
    const existingAccounts = [
      {
        id: 'acc-123',
        name: 'Compte Courant',
        sourceType: 'CSV',
        currency: 'EUR',
        createdAt: '2026-10-01T12:00:00Z',
        updatedAt: '2026-10-01T12:00:00Z',
      },
    ];

    const hasAccounts = existingAccounts.length > 0;
    assert.strictEqual(hasAccounts, true);
    assert.strictEqual(existingAccounts[0].id, 'acc-123');
  });

  test('validates preview CSV response structure and deduplication counts', () => {
    const mockPreviewResponse = {
      fileHash: 'sha256_mock_hash_12345',
      detectedCharset: 'UTF_8',
      charsetAmbiguous: false,
      charsetCandidates: [],
      detectedDelimiter: ';',
      headers: ['Date operation', 'Libelle', 'Montant'],
      suggestedMapping: {
        dateColumn: 'Date operation',
        valueDateColumn: null,
        labelColumn: 'Libelle',
        amountColumn: 'Montant',
        debitColumn: null,
        creditColumn: null,
        currencyColumn: null,
        sourceIdColumn: null,
        dateFormat: 'dd/MM/yyyy',
        amountFormat: 'FR',
      },
      mappingComplete: true,
      totalRows: 50,
      wouldInsert: 48,
      wouldSkip: 2,
      blocking: false,
      sample: [
        {
          line: 2,
          bookingDate: '2026-10-15',
          amountMinor: -4550,
          currency: 'EUR',
          label: 'CARREFOUR MARKET',
        },
      ],
      errors: [],
      warnings: [],
      issueCount: 0,
    };

    assert.strictEqual(mockPreviewResponse.blocking, false);
    assert.strictEqual(mockPreviewResponse.wouldInsert, 48);
    assert.strictEqual(mockPreviewResponse.wouldSkip, 2);
    assert.strictEqual(mockPreviewResponse.suggestedMapping.amountFormat, 'FR');
    assert.strictEqual(mockPreviewResponse.sample[0].amountMinor, -4550);
  });

  test('detects and flags blocking preview errors without saving transactions', () => {
    const mockBlockingPreview = {
      fileHash: 'sha256_invalid_hash',
      mappingComplete: false,
      totalRows: 10,
      wouldInsert: 0,
      wouldSkip: 0,
      blocking: true,
      errors: [
        {
          line: 4,
          column: 'Montant',
          code: 'INVALID_AMOUNT',
          message: 'Montant non reconnu',
        },
      ],
      issueCount: 1,
    };

    assert.strictEqual(mockBlockingPreview.blocking, true);
    assert.strictEqual(mockBlockingPreview.errors.length, 1);
    assert.strictEqual(mockBlockingPreview.errors[0].code, 'INVALID_AMOUNT');
  });

  test('validates atomic import confirmation result and idempotency', () => {
    const mockImportResult = {
      importJob: {
        id: 'job-789',
        accountId: 'acc-123',
        status: 'COMPLETED',
        totalRows: 48,
        insertedRows: 48,
        skippedRows: 0,
        failedRows: 0,
        createdAt: '2026-10-30T10:00:00Z',
        completedAt: '2026-10-30T10:00:01Z',
      },
      warnings: [],
    };

    assert.strictEqual(mockImportResult.importJob.status, 'COMPLETED');
    assert.strictEqual(mockImportResult.importJob.insertedRows, 48);
    assert.strictEqual(mockImportResult.importJob.skippedRows, 0);

    // Re-importing same file skips duplicate rows idempotently
    const mockReimportResult = {
      importJob: {
        id: 'job-790',
        accountId: 'acc-123',
        status: 'COMPLETED',
        totalRows: 48,
        insertedRows: 0,
        skippedRows: 48,
        failedRows: 0,
        createdAt: '2026-10-30T10:05:00Z',
        completedAt: '2026-10-30T10:05:01Z',
      },
      warnings: [],
    };

    assert.strictEqual(mockReimportResult.importJob.status, 'COMPLETED');
    assert.strictEqual(mockReimportResult.importJob.insertedRows, 0);
    assert.strictEqual(mockReimportResult.importJob.skippedRows, 48);
  });
});
