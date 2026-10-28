# Recurring payments API

Base path: `/v1/recurring`. All endpoints require a valid JWT.

## GET /v1/recurring/candidates
Returns series with status CANDIDATE for the connected user.

Query: `accountId` (optional UUID).

## GET /v1/recurring
Returns all recurring series for the connected user.

Query: `accountId`, `status` (CANDIDATE|CONFIRMED|DISMISSED), `type` (SUBSCRIPTION|RECURRING_BILL|OTHER_RECURRING|UNCLASSIFIED).

## GET /v1/recurring/{id}
Returns detail, occurrences and detection reasons.

## PATCH /v1/recurring/{id}/decision
Body:
```json
{ "status": "CONFIRMED", "seriesType": "SUBSCRIPTION", "note": "optional" }
```
Status can be CONFIRMED or DISMISSED.

## POST /v1/recurring/analyze
Body (optional):
```json
{ "accountIds": ["uuid"], "force": false }
```
Triggers a deterministic analysis. Returns counts.

## DTO example
```json
{
  "id": "uuid",
  "accountId": "uuid",
  "accountName": "Compte courant",
  "label": "NETFLIX",
  "currency": "EUR",
  "frequency": "MONTHLY",
  "status": "CANDIDATE",
  "seriesType": "SUBSCRIPTION",
  "usualAmountMinor": 1599,
  "minAmountMinor": 1599,
  "maxAmountMinor": 1599,
  "occurrenceCount": 4,
  "firstOccurrenceDate": "2025-01-05",
  "lastOccurrenceDate": "2025-04-05",
  "nextEstimatedDate": "2025-05-05",
  "monthlyEquivalentMinor": 1599.0000,
  "confidenceScore": 85,
  "confidenceLevel": "HIGH"
}
```

Notes:
- Amounts are in minor units (cents).
- Next estimated date is indicative, not guaranteed.
- Missing payment does not prove cancellation, especially with incomplete CSV imports.
- Confidence score is a heuristic estimate, not a calibrated probability.
