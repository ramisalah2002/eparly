# Eparly — Assistant Financier Intelligent

Assistant financier intelligent qui analyse les transactions bancaires, détecte les abonnements, propose des alternatives moins chères et calcule les économies potentielles.

## Architecture du dépôt

Ce dépôt est un monorepo contenant :

- `apps/mobile/` : Application mobile React Native (Expo, TypeScript)
- `apps/admin/` : Console d'administration
- `services/api/` : Backend Spring Boot 4.1.2 (Java 21, Maven, Spring Data JPA, Flyway)
- `infra/` : Infrastructure locale (Docker Compose, PostgreSQL 17)

---

## Guide de développement Backend

### 1. Démarrer la base de données PostgreSQL

Depuis la racine du projet :
```bash
cd infra
cp .env.example .env   # (si premier lancement)
docker compose up -d
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

# Via Maven Wrapper (avec profil local actif par défaut) :
./mvnw spring-boot:run
# ou sous Windows PowerShell :
.\mvnw.cmd spring-boot:run

# Ou via le JAR exécutable packagé :
java -Dspring.profiles.active=local -jar target/api-0.0.1-SNAPSHOT.jar
```

Le profil `local` intègre automatiquement :
- L'import sécurisé des variables locales depuis `../../infra/.env` (non commité).
- La connexion au conteneur PostgreSQL `eparly_db` avec pool HikariCP (`EparlyHikariPool`).
- L'exécution automatique des migrations Flyway au démarrage.

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
- Hibernate est configuré en `ddl-auto=none` : **seul Flyway** gère les modifications de schéma.

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
