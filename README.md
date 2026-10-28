# Eparly — Assistant Financier Intelligent

Assistant financier intelligent qui analyse les transactions bancaires, détecte les abonnements, propose des alternatives moins chères et calcule les économies potentielles.

## Architecture du dépôt

Ce dépôt est un monorepo contenant :

- `apps/mobile/` : Application mobile React Native (Expo, TypeScript)
- `apps/admin/` : Console d'administration Next.js (TypeScript, Tailwind CSS)
- `services/api/` : Backend Spring Boot 4.1.2 (Java 21, Maven, Spring Data JPA, Flyway)
- `infra/` : Infrastructure locale (Docker Compose, PostgreSQL 17)

---

## Intégration Continue (CI)

Le projet utilise GitHub Actions (`.github/workflows/ci.yml`) pour valider automatiquement chaque push et pull request sur 3 jobs indépendants :

| Job | Périmètre | Vérifications exécutées |
| :--- | :--- | :--- |
| **Mobile CI** | `apps/mobile` | Validation TypeScript (`tsc --noEmit`), ESLint (`expo lint`), et validation du bundle JS (`expo export`) |
| **Admin CI** | `apps/admin` | ESLint (`eslint`), validation TypeScript (`tsc --noEmit`), et compilation de production Next.js (`next build`) |
| **Backend CI** | `services/api` | Compilation Java 21, migrations Flyway, tests unitaires et d'intégration avec Testcontainers (PostgreSQL 17) |

### Commandes locales avant de pousser

Pour vérifier votre code en local avant d'ouvrir une Pull Request :

#### Monorepo Frontend (Mobile & Admin)
```bash
# Vérification du typage TypeScript sur tous les packages frontend
pnpm typecheck

# Vérification du linting ESLint sur tous les packages frontend
pnpm lint

# Tester la compilation Next.js en production
pnpm --filter admin build
```

#### Application Mobile
```bash
pnpm --filter mobile lint
pnpm --filter mobile typecheck
```

#### Console Admin
```bash
pnpm --filter admin lint
pnpm --filter admin typecheck
pnpm --filter admin build
```

#### Backend API (Java 21 / Maven)
```bash
cd services/api
./mvnw -B -ntp verify
# Sous Windows PowerShell :
.\mvnw.cmd -B -ntp verify
```

---

## Guide de développement Backend

### 1. Démarrer la base de données PostgreSQL

Depuis la racine du projet :
```bash
cd infra
cp .env.example .env   # (si premier lancement)
docker compose --profile local up -d
```

Vérifier la santé du conteneur :
```bash
docker compose ps
# Doit afficher : eparly-postgres ... Up ... (healthy)
```

Vérifier la connexion SQL manuellement :
```bash
docker compose exec postgres psql -U eparly_user -d eparly_db -c "SELECT version();"
```

### 2. Démarrer le Backend Spring Boot avec le profil local

Depuis `services/api` :
```bash
cd services/api

# Sous Windows PowerShell, activer explicitement le profil local :
.\mvnw.cmd '-Dspring-boot.run.profiles=local' spring-boot:run

# Ou via le JAR exécutable packagé :
java -Dspring.profiles.active=local -jar target/api-0.0.1-SNAPSHOT.jar
```

Le profil `local` doit être activé explicitement (`.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`) et intègre :
- L'import sécurisé des variables locales depuis `../../infra/.env` (non commité).
- La connexion au conteneur PostgreSQL `eparly_db` avec pool HikariCP (`EparlyHikariPool`).
- L'exécution automatique des migrations Flyway au démarrage.
- Mailpit pour le SMTP local (`127.0.0.1:1025`, interface `http://127.0.0.1:8025`).
- Les clés JWT de développement, placées sous `infra/keys/` et exclues de Git.
- Les endpoints OpenAPI locaux sont disponibles sous `/v3/api-docs` et Swagger UI sous `/swagger-ui.html`.

Générer une paire RSA avant le démarrage (format PEM PKCS#8/X.509), depuis la racine du dépôt :
```bash
node -e "const fs=require('node:fs');const c=require('node:crypto');fs.mkdirSync('infra/keys',{recursive:true});const k=c.generateKeyPairSync('rsa',{modulusLength:3072,publicKeyEncoding:{type:'spki',format:'pem'},privateKeyEncoding:{type:'pkcs8',format:'pem'}});fs.writeFileSync('infra/keys/jwt-private.pem',k.privateKey,{mode:0o600});fs.writeFileSync('infra/keys/jwt-public.pem',k.publicKey)"
```
Ces fichiers sont exclus de Git. Ne jamais réutiliser les clés locales en production. La production exige le profil `prod`, une paire RSA privée/publique et un keystore TLS fournis par configuration sécurisée.

### Sessions mobiles : refresh et logout

`POST /v1/auth/login` retourne `accessToken` (JWT, 15 minutes par défaut), `expiresIn` en secondes, `refreshToken` opaque et `refreshExpiresIn` (30 jours par défaut). Le client React Native doit conserver le refresh token exclusivement dans **Expo SecureStore**, jamais dans AsyncStorage, une URL ou les logs.

Quand l'access token expire, appeler `POST /v1/auth/refresh` avec `{"refreshToken":"<refresh token courant>"}`. Une rotation invalide l'ancien jeton et retourne un nouveau couple. La durée du refresh token n'est pas prolongée lors d'une rotation : elle est calculée depuis la création initiale de la famille de session. Les erreurs de jeton retournent `401`; le client doit alors supprimer les jetons locaux et demander une nouvelle connexion.

La réutilisation d'un refresh token consommé révoque toute sa famille. Cela protège contre le rejeu, mais une réponse de rotation perdue implique une reconnexion : ne pas réessayer l'ancien jeton. `POST /v1/auth/logout` accepte `{"refreshToken":"<refresh token courant>"}` et retourne toujours `204`, sans exiger d'access token. Le logout révoque toute la famille et est idempotent ; un JWT d'accès déjà émis reste utilisable jusqu'à son expiration.

Exemple :
```http
POST /v1/auth/refresh
Content-Type: application/json

{"refreshToken":"<refresh token courant>"}
```

### 3. Exécuter les tests automatisés

Depuis `services/api` :
```bash
./mvnw test
# ou sous Windows PowerShell :
.\mvnw.cmd test
```

> [!NOTE]
> Les tests d'intégration (`PostgresIntegrationTest`) et de contexte (`ApiApplicationTests`) utilisent **Testcontainers** pour instancier un conteneur PostgreSQL 17 éphémère et isolé, garantissant qu'aucun test ne dépend ni ne pollue la base de développement locale.

### 4. Créer de futures migrations Flyway

Toutes les migrations SQL doivent être placées dans :
`services/api/src/main/resources/db/migration/`

Convention de nommage stricte :
- Format : `V<Version>__<Description_avec_underscores>.sql`
- Exemple : `V2__create_users_table.sql`, `V3__create_transactions_and_subscriptions.sql`

Règles de migration :
- Une migration appliquée ne doit **jamais** être modifiée rétrospectivement.
- Toute modification de structure ou de données ultérieure doit faire l'objet d'un nouveau numéro de version séquentiel (`V2`, `V3`, etc.).
- Hibernate est configuré en `ddl-auto=validate` : **seul Flyway** gère les modifications de schéma ; V1/V2 restent inchangées et V3 ajoute les jetons de vérification email.

### 5. Vérifier la connexion à la base et la santé de l'API

Interroger l'endpoint Actuator :
```bash
curl http://localhost:8080/actuator/health
```

Réponse attendue confirmant l'état de la base PostgreSQL :
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

Consulter l'historique des migrations Flyway en base :
```bash
cd infra
docker compose exec postgres psql -U eparly_user -d eparly_db -c "SELECT installed_rank, version, description, success FROM flyway_schema_history;"
```

---

## Arrêt des services d'infrastructure

```bash
cd infra
docker compose stop
# ou
docker compose down
```

> [!CAUTION]
> Ne jamais utiliser `docker compose down -v` sans autorisation préalable sous peine de détruire le volume persistant de données PostgreSQL (`eparly_postgres_data`).
