# Eparly Mobile Application

Cross-platform personal finance mobile client built with React Native, Expo, and Expo Router.

---

## Getting Started

### Prerequisites
- Node.js 20+ (Node 24 recommended)
- pnpm 12+
- Backend running on `http://localhost:8080` (or `http://<LAN_IP>:8080` for physical devices)
- Mailpit running on `http://localhost:8025`

### Installation & Run
```bash
# Install dependencies from monorepo root
pnpm install

# Start Expo dev server
pnpm --filter mobile dev
```

---

## Authentication & Security Architecture

### 1. Secure Token Storage
- **Native (iOS / Android)**: Tokens are stored exclusively in **Expo SecureStore** (`eparly_access_token`, `eparly_refresh_token`) using `AFTER_FIRST_UNLOCK` keychain access.
- **No Insecure Fallback**: On native platforms, storage failures throw an explicit error rather than falling back to unencrypted `AsyncStorage`.
- **Legacy Cleanup**: On initialization, any insecure tokens written to `AsyncStorage` by older builds are purged automatically via `cleanLegacyInsecureTokens()`.
- **Non-sensitive Data**: User profiles (`eparly_user_profile`) and onboarding state are kept strictly segregated in `AsyncStorage`.

### 2. Session Lifecycle & State Machine
The authentication state in `auth-context.tsx` tracks three states:
1. **Loading / Restoring**: When the app starts, session tokens are restored and verified with `/v1/me`. Protected routes display a loading spinner and never expose authenticated screens prematurely.
2. **Authenticated**: Active valid access token present. Full access to `(tabs)`.
3. **Unauthenticated**: No active token or expired/revoked session. Automatically redirects to `/(auth)/sign-in`.

### 3. Route Protection Guard
- Protected screens reside in `apps/mobile/src/app/(tabs)/_layout.tsx`.
- Implements declarative route protection via `<Redirect href="/(auth)/sign-in" />`.
- Prevents back-navigation leaks and redirect loops.

### 4. Single-Flight Token Refresh
- Located in `apps/mobile/src/services/api-client.ts`.
- **Concurrent Request Deduplication**: If multiple simultaneous API requests receive HTTP 401, only **one single refresh request** is dispatched to the backend `/v1/auth/refresh`.
- **In-flight Promise Sharing**: All waiting requests share the single in-flight promise and retry once using the newly rotated access token.
- **Token Rotation Safety**: Respects backend single-use rotating refresh tokens, preventing token family reuse detection and accidental account lockouts.
- **Infinite Loop Prevention**: Retried requests are marked with `_isRetry: true` and will never trigger another refresh attempt.
- **Offline Resilience**: Transient network failures do not prematurely wipe the local refresh token.

### 5. Registration Contract (`fullName`)
- Form collects: `Full Name` (required, 1–100 chars), `Email`, and `Password` (min 8 chars).
- Sent payload:
  ```json
  {
    "email": "user@example.com",
    "password": "Password123!",
    "termsAccepted": true,
    "marketingConsent": false,
    "fullName": "Jane Doe"
  }
  ```
- Persisted to backend `users.full_name` and returned in `/v1/me`.

### 6. Email Verification & Deep Links
- **Primary Flow**: 6-digit numeric OTP entered into `(auth)/otp-verification.tsx`.
- **Deep Link**: Supported via `mobile://(auth)/otp-verification?code=123456&email=user@example.com`.
- **Auto-Verification**: When opened via deep link, the 6 digits are pre-filled and verified automatically.
- **Clean Handoff**: After verification, user proceeds cleanly to Sign In without plaintext passwords lingering in navigation history or state.

---

## Verification & Testing

```bash
# TypeScript validation
pnpm --filter mobile typecheck

# ESLint validation
pnpm --filter mobile lint

# Unit & regression tests (Single-flight refresh & Secure storage)
pnpm --filter mobile test
```
