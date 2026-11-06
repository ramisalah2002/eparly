# Connexion Google (backend)

Le client demande un challenge à Eparly, obtient un **Google ID Token** contenant son nonce, puis l'échange contre la
session Eparly habituelle. Les API Eparly n'acceptent ensuite que les JWT Eparly : aucun jeton Google ne sert à les
appeler et aucun n'est stocké.

## Parcours

1. Le client appelle `POST /v1/auth/google/challenge`.
2. Eparly renvoie `challengeId` et `nonce` (valables 5 minutes, usage unique).
3. Le client transmet `nonce` **tel quel** au SDK Google. Ne le hachez pas et ne le modifiez pas.
4. Google délivre un ID Token signé contenant ce nonce.
5. Le client envoie `idToken` et `challengeId` à Eparly.
6. Eparly vérifie le jeton et le nonce, puis consomme le challenge.
7. Eparly renvoie la session (ou le résultat de la liaison).

Chaque tentative utilise un nouveau challenge, sauf la reprise après `428` décrite plus bas.

## SDK Google

| Plateforme | Transmission du nonce | Remarques |
|---|---|---|
| Android (Credential Manager) | `GetGoogleIdOption.Builder().setNonce(nonce)` ou `GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce)` | Client ID Web dans `setServerClientId` |
| iOS (GoogleSignIn) | `signInWithPresentingViewController:hint:additionalScopes:nonce:completion:` | Disponible à partir de GoogleSignIn-iOS **9.0.0**. La **10.1.0** corrige la perte du nonce après un redémarrage lié à Device Policy : préférez-la |
| Web (GIS) | `google.accounts.id.initialize({ client_id, callback, nonce })` | Initialisez avec le nonce du challenge courant |

Un flux ou un SDK qui ne transmet pas le nonce ne peut pas se connecter : il n'existe pas de mode de repli. Vérifiez
aussi le comportement des surcouches (React Native, Flutter, Expo) : elles n'exposent pas forcément ces options.
Firebase Auth n'est pas concerné ici : ce backend attend un Google ID Token direct.

Sources : [Android](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation),
[iOS, en-tête GIDSignIn](https://github.com/google/GoogleSignIn-iOS/blob/main/GoogleSignIn/Sources/Public/GoogleSignIn/GIDSignIn.h),
[iOS, versions](https://github.com/google/GoogleSignIn-iOS/releases),
[Web](https://developers.google.com/identity/gsi/web/reference/js-reference).

## Endpoints

### `POST /v1/auth/google/challenge`

Public pour `LOGIN`. `LINK` exige `Authorization: Bearer <JWT_EPARLY>` et attache le challenge à cet utilisateur.

```json
{ "purpose": "LOGIN" }
```

Réponse `200` :

```json
{ "challengeId": "7b0c7f2e-6f5c-4a51-9d55-0a1f6f3f2c11", "nonce": "<43 caractères base64url>",
  "purpose": "LOGIN", "expiresAt": "2026-11-02T20:05:00Z" }
```

`400` intention invalide, `401` `LINK` sans session valide, `429` trop de requêtes, `503` fonction non configurée.

### `POST /v1/auth/google` (public)

```http
POST /v1/auth/google
Content-Type: application/json

{ "idToken": "<GOOGLE_ID_TOKEN>", "challengeId": "<UUID>", "termsAccepted": true, "marketingConsent": false }
```

`termsAccepted` n'est nécessaire qu'à la création d'un compte. Réponse `200`, identique à `POST /v1/auth/login` :

```json
{ "accessToken": "<JWT_EPARLY>", "tokenType": "Bearer", "expiresIn": 900,
  "refreshToken": "<REFRESH_OPAQUE>", "refreshExpiresIn": 2592000 }
```

| Statut | `message` | Signification et action du client |
|---|---|---|
| 400 | `Request validation failed` | Corps invalide |
| 401 | `GOOGLE_CHALLENGE_INVALID` | Challenge inconnu, expiré, déjà consommé, d'une autre intention ou d'un autre utilisateur : recommencer depuis un nouveau challenge |
| 401 | `Invalid Google identity` | Jeton invalide, expiré, mauvaise audience, email non vérifié, nonce absent ou incorrect |
| 403 | `This account is not available` | Compte suspendu ou supprimé |
| 409 | `GOOGLE_ACCOUNT_LINK_REQUIRED` | Email déjà utilisé : se connecter par mot de passe, puis lier |
| 428 | `GOOGLE_CONSENT_REQUIRED` | Nouveau compte : afficher les conditions, puis renvoyer le même `idToken` et le même `challengeId` avec `termsAccepted: true`, tant qu'ils restent valides |
| 429 | message du limiteur | Trop de requêtes (en-tête `Retry-After`) |
| 503 | `Google sign-in is not available` ou `... temporarily unavailable` | Fonction non configurée, ou clés Google inaccessibles |

Les erreurs suivent `ApiErrorResponse`. Pour `409`, `428` et `GOOGLE_CHALLENGE_INVALID`, le champ `message` est un code stable.
Après `409`, la liaison exige un nouveau challenge `LINK`.

### `POST /v1/auth/google/link` (`Authorization: Bearer <JWT_EPARLY>`)

```json
{ "idToken": "<GOOGLE_ID_TOKEN>", "challengeId": "<UUID_CHALLENGE_LINK>", "currentPassword": "<MOT_DE_PASSE_ACTUEL>" }
```

`currentPassword` est obligatoire si le compte possède un mot de passe : un JWT issu d'un refresh ne prouve pas un
contrôle récent. Échec : `401`, avec le même limiteur que le login. Réponse `200` : `{ "provider": "GOOGLE", "email": "alex@gmail.com", "alreadyLinked": false }`. Relancer la même
liaison sur le même compte, avec un nouveau challenge et un nouveau jeton, renvoie `alreadyLinked: true`. `409` si cette identité Google est liée à un autre compte,
ou si ce compte est déjà lié à une autre identité Google. `401`, `403` et `503` comme ci-dessus.

## Règles de compte

- **Identifiant** : le `sub` Google, jamais l'email. Un changement d'email côté Google ne change pas le compte.
- **Nouveau compte** : créé seulement avec `termsAccepted: true` : statut `ACTIVE`, email considéré vérifié, sans mot
  de passe local (`password_hash` NULL), préférences par défaut, consentements versionnés comme à l'inscription.
- **Sans consentement** : rien n'est stocké. Le flux est sans état, aucun compte « en attente » n'existe.
- **Email déjà présent** : jamais de fusion automatique, y compris avec un compte non vérifié.
- **Sans mot de passe** : la connexion par mot de passe échoue comme des identifiants invalides.
  `forgot-password` reste possible et permet d'ajouter un mot de passe après contrôle de la boîte mail.
- **Privilèges** : `CATALOG_ADMIN` ne vient que de `catalog_admin_permissions` ; ni le jeton ni la requête n'en donnent.

## Vérification du jeton

Nimbus (déjà présent via Spring Security) vérifie la signature **RS256 uniquement** avec les clés de
`https://www.googleapis.com/oauth2/v3/certs`, mises en cache et rafraîchies lors d'une rotation, avec délais réseau
bornés. Sont exigés : `iss` = `accounts.google.com` ou `https://accounts.google.com`, `aud` parmi les Client IDs
configurés, `azp` configuré s'il est présent, `exp` et `iat` valides, `sub` présent, `email` présent,
`email_verified` vrai. Le claim `nonce` signé est ensuite obligatoire et doit correspondre au challenge.

## Anti-rejeu et concurrence

- **Nonce** : 32 octets de `SecureRandom`, encodés en base64url. Seule son empreinte SHA-256 est stockée.
- **Consommation** : `UPDATE ... WHERE consumed_at IS NULL AND expires_at > now`, dans la transaction qui crée ou lie le
  compte. Deux requêtes simultanées se sérialisent sur la ligne : une seule met à jour, l'autre reçoit `401`.
- **Rejet sans consommation** : un jeton invalide ou un mauvais mot de passe ne consomme pas le challenge, donc un tiers
  qui connaît `challengeId` ne peut pas le griller. `409` et `428` annulent aussi la consommation.
- **Après succès** : si l'émission de session échoue après la transaction, le challenge reste consommé ; le client recommence.
- **Nettoyage** : les challenges expirés sont supprimés par le nettoyage d'authentification quotidien (`AuthCleanupService`).
- **Débit** : `POST /challenge` utilise la même limite par IP que le refresh (60 par minute par défaut), compteur séparé.

## Session

Après vérification, le service appelle `RefreshTokenService.createSession`, comme le login classique : JWT RS256 de
15 minutes, refresh opaque avec rotation, détection de réutilisation, `POST /v1/auth/refresh`, `POST /v1/auth/logout`.

## Schéma (V26, V27)

`users.password_hash` devient nullable. Table `user_oauth_identities` : `id`, `user_id`, `provider`,
`provider_subject`, `email_at_link_time`, `created_at`, `updated_at`, avec unicité sur `(provider, provider_subject)`
et `(user_id, provider)`.

V27 ajoute `google_auth_challenges` : `id`, `nonce_hash` (unique), `purpose` (`LOGIN` ou `LINK`), `user_id` (requis pour
`LINK`, interdit pour `LOGIN`), `created_at`, `expires_at`, `consumed_at`.

## Configuration

Désactivée par défaut. Sans Client ID, l'endpoint répond 503, sans validation permissive.

```properties
GOOGLE_AUTH_ENABLED=true
GOOGLE_AUTH_ALLOWED_CLIENT_IDS=<web>.apps.googleusercontent.com,<android>.apps.googleusercontent.com,<ios>.apps.googleusercontent.com
```

Durée d'un challenge : `eparly.auth.google.challenge-ttl` (`PT5M` par défaut).

Dans Google Cloud Console : écran de consentement OAuth, puis un Client ID **Web**, **Android** (package + SHA-1) et
**iOS** (bundle ID). Sur Android, l'audience du jeton est généralement le Client ID Web et `azp` le Client ID Android :
déclarez-les tous. Aucun Client Secret n'est nécessaire à ce backend. Vérifiez l'audience réelle sur un premier jeton.

## À faire plus tard côté mobile

1. Suivre le parcours ci-dessus, en demandant le Client ID Web comme audience.
2. Stocker la session comme pour le login classique.
3. Gérer `428` (conditions) et `409` (connexion par mot de passe, challenge `LINK`, puis `POST /v1/auth/google/link`).
4. Sur `GOOGLE_CHALLENGE_INVALID`, ou si le jeton a expiré, recommencer depuis un nouveau challenge.

## Tests

`./mvnw test -Dtest=GoogleAuthIntegrationTest`. Les jetons sont signés par une clé de test et vérifiés par le vrai
code via un serveur JWKS local, sur PostgreSQL Testcontainers. Aucun appel n'est fait à Google.

## Avant production

Vérifier l'audience et la présence du nonce
sur de vrais jetons, utiliser de vrais Client IDs par environnement, passer l'écran de consentement en production et
surveiller les 401 et 429 de ces routes.

## Limites

- Aucun véritable jeton Google n'a été testé tant que le client et les Client IDs n'existent pas.
- Le challenge `LOGIN` n'est pas lié à un appareil : un attaquant qui intercepte à la fois `idToken` et `challengeId`
  avant leur première utilisation peut l'utiliser à la place du client. HTTPS reste indispensable en production.
- Le contrat a changé : le champ `nonce` des requêtes disparaît et `challengeId` est obligatoire (`400` sinon).
- Un compte classique jamais vérifié occupant un email bloque la connexion Google sur `409` jusqu'à sa résolution.
- Il n'existe pas encore d'endpoint pour délier un compte Google.
