# Infrastructure Eparly — PostgreSQL & Docker Compose

Ce dossier contient la configuration de l'infrastructure locale pour l'application Eparly.

## Services inclus

- **PostgreSQL 17** (`postgres:17-alpine`)
  - Base de données : `eparly_db`
  - Utilisateur applicatif dédié : `eparly_user`
  - Port exposé : `127.0.0.1:5432` (restreint à la boucle locale pour des raisons de sécurité)
  - Persistance : Volume Docker nommé `eparly_postgres_data`
  - Healthcheck natif : `pg_isready`
- **Mailpit** (`axllent/mailpit`)
  - Activé uniquement avec le profil Compose `local`.
  - SMTP : `127.0.0.1:1025` ; interface web : `http://127.0.0.1:8025`.

---

## Prérequis

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) en cours d'exécution.
- Docker Compose v2+.

---

## Configuration (`.env`)

1. Copier le fichier d'exemple dans un fichier `.env` local :
   ```bash
   cp .env.example .env
   # ou sur PowerShell :
   Copy-Item .env.example .env
   ```

2. Configurer un mot de passe sécurisé dans `infra/.env` :
   ```dotenv
   POSTGRES_DB=eparly_db
   POSTGRES_USER=eparly_user
   POSTGRES_PASSWORD=votre_mot_de_passe_robuste
   POSTGRES_PORT=5432
   ```

> [!IMPORTANT]
> Le fichier `infra/.env` contient des secrets et est ignoré par Git via le `.gitignore`. Ne jamais le commiter.

---

## Commandes Docker Compose

Toutes les commandes ci-dessous doivent être exécutées depuis le dossier `infra/` :

### 1. Démarrer les services en arrière-plan
```bash
docker compose --profile local up -d
```

### 2. Vérifier l'état et la santé du conteneur
```bash
docker compose ps
```
Les conteneurs `eparly-postgres` et `eparly-mailpit` doivent être démarrés ; PostgreSQL doit afficher `healthy`.

### 3. Consulter les logs
```bash
# Logs en temps réel
docker compose logs -f postgres

# 50 dernières lignes de log
docker compose logs --tail=50 postgres

# Consulter les emails reçus dans Mailpit : http://127.0.0.1:8025
```

### 4. Tester la connexion SQL avec psql
```bash
docker compose exec postgres psql -U eparly_user -d eparly_db -c "SELECT version();"
```

Pour ouvrir une session interactive `psql` :
```bash
docker compose exec -it postgres psql -U eparly_user -d eparly_db
```

### 5. Arrêter les services
```bash
# Arrêter les conteneurs en conservant les données
docker compose stop

# Arrêter et supprimer les conteneurs (conserve le volume de données)
docker compose down
```

> [!CAUTION]
> Ne jamais exécuter `docker compose down -v` sans autorisation explicite car cela supprime définitivement le volume de données persistant (`eparly_postgres_data`).
