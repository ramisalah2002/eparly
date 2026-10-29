# Mobile integration handoff — Eparly API

This is the React Native integration entry point. Detailed contracts are linked at the end; `/v3/api-docs` is the machine-readable contract in `local` and `test` profiles. All examples use fictitious values.

## Environments and network

Configure one `API_BASE_URL` per build environment; do not hard-code it in request helpers.

| Client | Development base URL | Notes |
|---|---|---|
| Android emulator | `http://10.0.2.2:<port>` | `10.0.2.2` routes to the emulator host; `localhost` means the emulator itself. |
| iOS simulator | `http://127.0.0.1:<port>` | The simulator can reach the development host loopback. |
| Physical device | `http://<computer-LAN-IPv4>:<port>` | Both devices must be on the same trusted LAN; the API must listen on that interface and the host firewall must allow the port only on the private development network. |
| Remote test / production | `https://<environment-host>` | Use a controlled HTTPS deployment and environment-specific credentials/configuration. No production URL is supplied by this repository. |

The real smoke-test server was deliberately bound to `127.0.0.1:18080`; it was **not** reachable from a phone. To test a physical device, explicitly bind the development server to the computer's private LAN interface, limit firewall access to the private network, and stop it after testing. Do not expose it to the public internet. Plain HTTP is only for a trusted local development network; deployed environments must use HTTPS. The `prod` profile requires TLS and disables Swagger/OpenAPI. Native React Native networking does not use browser CORS; no wildcard CORS policy is configured or required. A future web client needs a separately scoped origin policy.

The `local` profile enables Docker Compose and imports `../../infra/.env`; Compose includes a persistent PostgreSQL volume. Do not use it for disposable tests or override its database without first checking the target. `test` is intended for tests. The default profile validates Hibernate schema with `ddl-auto=validate` and Flyway migrations; it is not a substitute for provisioning the profile's required database and RSA keys.

## Common wire contract

- Protected routes require `Authorization: Bearer <accessToken>`. User identity is derived only from the JWT; never send a `userId`.
- Access tokens are RS256 JWTs, 900 seconds by default. Refresh tokens are opaque, single-use, and valid for 30 days by default. Store both only in platform secure storage (for Expo, `expo-secure-store`); never log or place them in URLs or ordinary preferences.
- On `401`, attempt at most one serialized `POST /v1/auth/refresh` using the current refresh token, replace **both** tokens atomically, then retry the original request once. A successful refresh consumes the old token. Reuse, expiry, or revocation returns `401`; if the response to a refresh is lost, do not replay the old refresh token—clear the session and ask the user to sign in again. Logout revokes the refresh-token family; an issued access token remains usable until its expiry.
- Dates are ISO-8601: date-only values are `YYYY-MM-DD`; instants are UTC ISO-8601 timestamps. UUIDs are standard hyphenated UUID strings.
- Financial transaction amounts are integer minor units (`amountMinor`, normally EUR cents). Never convert through binary floating point. `BigDecimal` values such as percentages or frequency equivalents may be decimal JSON numbers.
- Page indices start at zero. Most list endpoints use `{content, page, size, totalElements, totalPages}`; default size is usually 20 and maximum 100. Follow each endpoint's documented parameters.
- Common errors use `{timestamp,status,error,message,path}`. Import errors can include stable `code` and bounded row issues. Handle `400` invalid input, `401` missing/invalid/expired credentials, `403` inactive/forbidden account, `404` missing **or foreign-owned** resource, `409` conflict/replay, `413` too-large upload, `415` unsupported file, `422` rejected CSV, and `429` rate limit (`Retry-After`). Never infer that a foreign resource exists from a `404`.
- Empty collections and null values are valid states. Do not substitute zero for unknown costs, absent observations, missing dates, or `null` observed savings.

## User journeys

### 1. Registration and sign-in

1. `POST /v1/auth/register` with `{email,password,termsAccepted,marketingConsent}`. Password length is 12–128 characters; expect `201`, `400`, `409` duplicate email, or `429`. The user must verify the email before sign-in.
2. `POST /v1/auth/verify-email` with `{token}` from the email link; expect `200` or `400` for invalid/expired/reused verification.
3. `POST /v1/auth/login` with `{email,password}`; expect `200` and `{accessToken,tokenType,expiresIn,refreshToken,refreshExpiresIn}`, `401` invalid/unavailable account, or `429`.
4. `GET /v1/me` with Bearer access token returns the current user's profile/settings; `401` for invalid/expired token and `403` for an inactive account.
5. Refresh with `POST /v1/auth/refresh` and `{refreshToken}`. Logout with `POST /v1/auth/logout` and the same shape; expect `204`. Both endpoints intentionally do not require a bearer access token.

UX: verification required; rate-limit responses should respect `Retry-After`. Never display or log tokens. Refresh replay is a session-ending event, not a retry loop.

### 2. Financial account

`POST /v1/accounts` with `{name,currency?}` creates a CSV account (`currency` defaults to EUR), `201`. `GET /v1/accounts` lists the authenticated user's accounts. Save the returned account `id` for import and account-scoped calls. `GET /v1/categories` returns available categories. Foreign/unknown account IDs return neutral `404`; duplicate names can return `409`.

### 3. CSV import

1. `POST /v1/imports/preview` as `multipart/form-data`: `accountId`, `file`, optional `mapping`, `charset`, `delimiter`. Preview does not persist transactions; it returns a proposed mapping, file hash, sample, and row issues.
2. Review/correct the mapping and display warnings before confirmation.
3. `POST /v1/imports` with the same file and `accountId`, plus the preview `fileHash`, required JSON-text `mapping`, and `charset` when ambiguous. Confirmation reparses and validates the file server-side; expect `201`. Re-importing the same CSV is deduplicated (`insertedRows=0` and skipped rows).
4. Read import status with `GET /v1/imports`, `/v1/imports/{id}`, or `/v1/accounts/{accountId}/imports`.

Do not set the multipart `Content-Type` manually: let `fetch` provide its boundary. Expo file part shape: `{uri, name, type: 'text/csv'}`. Files are limited to 5 MB. Invalid/ambiguous CSV can return `400`, `409` hash mismatch, `413`, `415`, or `422`; show actionable row errors without implying partial success. Imported positive amounts are `UNKNOWN` until classified; an empty import/month is not evidence that no banking activity occurred.

### 4. Transactions and categories

`GET /v1/transactions?accountId=<uuid>&page=0&size=20` supports inclusive `startDate`, `endDate`, and `transactionType` filters. A transaction exposes `id`, `accountId`, `bookingDate`, `valueDate`, `amountMinor`, `currency`, labels, `transactionType`, `categoryId`, and `customCategoryFlag`. `GET /v1/transactions/{id}` reads one owned transaction. Categories are available from the categories API (see OpenAPI). Imported rows may have `categoryId: null`; render them as uncategorized rather than assigning a category in the client.

Private transaction/account lookups return `404` for both unknown and foreign resources. No endpoint response should be presented as a bank balance.

### 5. Dashboard

- `GET /v1/dashboard/summary?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD&accountId=<uuid?>`
- `GET /v1/dashboard/spending-by-category` with the same dates/account filter.
- `GET /v1/dashboard/monthly-trend?months=6&accountId=<uuid?>` (`months` 1–24).

Expenses are `EXPENSE` + `FEE`; confirmed income is only positive `INCOME`. Transfers are excluded; positive `UNKNOWN` and refunds remain unclassified, not income. Show the API's incomplete-data caveat. Empty trend months are zero recorded activity only, not zero real-world activity. Invalid ranges return `400`; foreign account filters return `404`.

### 6. Recurring activity and subscriptions

1. `POST /v1/recurring/analyze` with optional `{accountIds:[...],force:false}` to request deterministic analysis.
2. Read `GET /v1/recurring/candidates?accountId=<uuid?>`; a candidate is not a confirmed subscription. User decision: `PATCH /v1/recurring/{id}/decision` with `{status:"CONFIRMED",seriesType:"SUBSCRIPTION",note?}` or `DISMISSED`.
3. Read `GET /v1/subscriptions` (optional `accountId`, `status`) and `/v1/subscriptions/summary`; create or link a declaration with `POST /v1/subscriptions` only after user confirmation.

Subscription response separates declared values from observed recurrence. `ACTIVE`, `PAUSED`, `CANCELED`, and `UNKNOWN` describe declared lifecycle state; a later transaction must not reactivate a canceled subscription. Monthly/annual equivalents are estimates, not payments during the selected month. Empty list is normal; indicative next-due dates are not guarantees.

### 7. Offer catalogue and comparison

1. `GET /v1/offers/metadata` and `GET /v1/offers/providers?sector=...` for supported sectors/providers; `GET /v1/offers?sector=STREAMING&page=0&size=20` and `/v1/offers/{id}` for public verified catalogue entries.
2. `GET /v1/comparisons/criteria?sector=STREAMING` before collecting required criteria.
3. `POST /v1/comparisons/evaluate` with `{subscriptionId,sector,requirements}`. Response groups offers into `compatibleOffers`, `incompatibleOffers`, `needsInformationOffers`, and `notEligibleOffers`, and includes catalogue coverage/freshness notes.

An empty catalogue or empty eligible result is valid; do not invent an offer or claim exhaustive market coverage. The catalogue has no public write endpoint. Offer `404` may mean unavailable or unknown. A foreign subscription also returns `404`. The compatibility score is heuristic, not a probability.

### 8. Potential savings estimate

`POST /v1/savings/estimate` with `{subscriptionId,offerId,sector,horizonMonths,requirements,userKnownCancellationFeeMinor?,userKnownOtherFeesMinor?}`. It returns an estimate status, costs and missing information. Do not turn `INSUFFICIENT_DATA`, incompatible offers, unknown fees, or absent catalogue prices into zero savings. This is potential savings, never a realized gain. `GET /v1/savings/methodologie` explains the calculation.

### 9. Savings action plan

1. Create from a fresh estimate with `POST /v1/savings/actions` using the same subscription/offer/sector/horizon/requirements and known fees. Backend recalculates; expect `201 PLANNED`, `400` if no supported positive estimate, `404` if resources are unavailable/foreign, or `409` for duplicate active opportunity.
2. List with `GET /v1/savings/actions?page=0&size=20`; detail with `/v1/savings/actions/{id}`; totals/status counts with `/v1/savings/actions/summary`.
3. Update with `PATCH /v1/savings/actions/{id}/status` and `{status,notes?}`. Supported transitions are `PLANNED → COMMITTED/CANCELED`, `COMMITTED → DECLARED/CANCELED`, `DECLARED → CANCELED`.

The immutable estimate snapshot includes its own horizon and date. Do not add different alternatives for one subscription or 12- and 24-month horizons into one unlabeled total. Canceled actions are not active opportunities. `observedSavingsMinor` remains `null` unless net savings are certified; status `DECLARED` is a user statement, not bank verification.

### 10. Banking evidence / verification

Use `/v1/savings/actions/{id}/verification`:

- `GET .../candidates?role=...&page=0&size=20`
- `POST .../evidence` with `{transactionId,role,effectiveChangeDate?}`, where `role` is `OLD_SUBSCRIPTION_PAYMENT` or `NEW_SUBSCRIPTION_PAYMENT`
- `GET ...` for verification details
- `POST .../evaluate` with optional `{effectiveChangeDate}` to recalculate the verification status
- `DELETE .../evidence/{evidenceId}` to unlink evidence without deleting the original transaction.

Verification may be `NOT_VERIFIED` or `PARTIALLY_VERIFIED`; partial raw payment differences are not net savings. Preserve contradictions and missing coverage information. Do not label `DECLARED` or `PARTIALLY_VERIFIED` as money saved. `observedSavingsMinor: null` is intentional and should be rendered as “not certified,” not €0.

### 11. Monthly report

`GET /v1/reports/monthly?month=YYYY-MM&accountId=<uuid?>`; `month` is required. The response is `{period,financialSummary,categoryBreakdown,previousMonthComparison,subscriptionsSummary,savingsActionsSummary,verificationSummary,dataQuality,generatedAt}`. Current/prior comparisons use registered EUR transactions only. Report and dashboard can return empty totals; show that there are no recorded transactions, not that no expenses occurred. Unknown negative amounts, non-EUR exclusions, CSV coverage uncertainty, unclassified activity, and null observed savings must remain visible as such.

## Troubleshooting and UX rules

| Condition | Client behavior |
|---|---|
| Expired/invalid access JWT | One serialized refresh attempt; on `401` clear credentials and sign in again. |
| Expired/replayed refresh | Do not retry; clear both tokens and sign in again. |
| Foreign account/action/subscription | Treat `404` as unavailable; do not reveal ownership/existence. |
| Bad CSV, ambiguous encoding, duplicate | Show bounded validation details; re-preview changed files; do not claim import success on `422`. |
| Empty transactions/categories/subscriptions/offers | Render an empty state and explain that no matching data is recorded/available. |
| Unknown fees or stale offer snapshot | Keep unknown distinct from zero; show estimate date and stale warning; request review/re-estimation. |
| Partially verified evidence | Show observed payments and limitations separately; do not display a certified net saving. |
| No useful report month | Show recorded-data absence and unknown CSV coverage; never infer no real spending. |

## Detailed contracts

- [Accounts and transactions](accounts-transactions-api.md)
- [CSV imports](imports-api.md)
- [Dashboard](dashboard-api.md)
- [Recurring series](recurring-api.md) and [subscriptions](subscriptions-api.md)
- [Offer catalogue](offers-catalog-api.md) and [comparison](comparison-api.md)
- [Savings estimates](savings-estimation-api.md), [action plan](savings-actions-api.md), and [verification](savings-verification-api.md)
- [Monthly report](monthly-report-api.md)
- Authentication, profile, category and all other DTO schemas: `GET /v3/api-docs`; Swagger UI is available only in the local/test configuration where enabled.
