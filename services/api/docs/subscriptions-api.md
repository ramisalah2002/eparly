# Subscriptions API

Base path: `/v1/subscriptions`. All endpoints require a valid JWT.

## GET /v1/subscriptions
Returns subscriptions (confirmed or manually declared) for the connected user.

Query: `accountId` (optional UUID), `status` (ACTIVE|PAUSED|CANCELED|UNKNOWN).

## GET /v1/subscriptions/{id}
Returns one subscription with declared and observed information.

## POST /v1/subscriptions
Creates a subscription manually or transforms a detected recurring series.

Body:
```json
{
  "accountId": "uuid",
  "recurringSeriesId": "uuid",
  "name": "Netflix",
  "provider": "Netflix",
  "declaredAmountMinor": 1599,
  "currency": "EUR",
  "declaredFrequency": "MONTHLY",
  "nextDueDate": "2025-05-05",
  "declaredStatus": "ACTIVE",
  "notes": "optional"
}
```

## PATCH /v1/subscriptions/{id}
Updates declared information only. Observed banking data is never overwritten.

## PATCH /v1/subscriptions/{id}/status
Declares a lifecycle change.

Body:
```json
{ "status": "CANCELED", "note": "optional" }
```

Allowed states: ACTIVE, PAUSED, CANCELED, UNKNOWN.

## GET /v1/subscriptions/summary
Returns cost estimates grouped by currency.

## Observed vs declared
- Observed fields come from the linked recurring series (banking detection).
- Declared fields come from the user.
- Declared values are never overwritten by CSV re-import or recurring analysis.
- A declared CANCELED status is not proof of real cancellation.
- If a payment is observed after a declared cancellation, `contradictoryObservation` is true and the declared status is preserved.

## Financial calculations
- Amounts are in minor units (cents).
- Monthly equivalent = declared or observed amount * frequency factor.
- Annual equivalent = monthly equivalent * 12.
- Currencies are never mixed; summary is grouped by currency.
- Estimated costs are not actual payments.

## Errors
- 400 invalid request
- 401 missing or invalid JWT
- 404 unknown or foreign subscription/account/series
- 409 recurring series already linked to a subscription
