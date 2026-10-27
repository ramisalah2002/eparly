package com.eparly.api.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI eparlyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Eparly API — Spécification d'Authentification et Sécurité")
                        .version("v1.2.0")
                        .description("""
                                ### Guide d'intégration Frontend (React Native / Expo)

                                #### 1. Cycle de vie des jetons
                                - **Access Token (JWT RS256)** : Durée de vie de 15 minutes. Transmis via l'en-tête standard `Authorization: Bearer <token>`.
                                - **Refresh Token (Opaque 256 bits)** : Durée de vie maximale de 30 jours avec rotation transactionnelle.
                                  Transmis exclusivement dans le corps JSON des requêtes `/v1/auth/refresh` et `/v1/auth/logout`.
                                - **Stockage mobile** : Utiliser impérativement `expo-secure-store` (Keystore sous Android, Keychain sous iOS). Ne jamais utiliser `AsyncStorage` pour les jetons.

                                #### 2. Récupération et Réinitialisation de mot de passe
                                - **`POST /v1/auth/forgot-password`** : Demande d'envoi de jeton. La réponse est **toujours neutre** (200 OK avec message générique) pour interdire toute énumération de comptes. En local, le message est capturé par Mailpit (`http://localhost:8025`).
                                - **Durée du jeton** : 30 minutes, usage unique strict.
                                - **`POST /v1/auth/reset-password`** : Soumission du jeton et du nouveau mot de passe (12 à 128 caractères).
                                - **Effets de bord de sécurité** :
                                  - Consommation atomique du jeton.
                                  - Révocation immédiate de l'ensemble des refresh tokens de l'utilisateur (`revoked_reason = 'PASSWORD_RESET'`).
                                  - Invalidation immédiate de tous les access tokens JWT antérieurs (`token_version` incrémentée, `token_invalid_before` mis à jour).
                                  - L'utilisateur n'est pas connecté automatiquement : l'application React Native doit purger les jetons en cache et rediriger vers l'écran de connexion (`/login`).

                                #### 3. Rate Limiting et Protection Anti-Bruteforce
                                - **Seuils par défaut** :
                                  - `POST /v1/auth/login` : 10 échecs par IP / 5 échecs par compte par fenêtre de 15 min. Temporisation progressive après 3 échecs consécutifs.
                                  - `POST /v1/auth/forgot-password` : 5 requêtes par IP par heure, 3 requêtes par email par 15 min.
                                  - `POST /v1/auth/resend-verification` : 5 requêtes par IP par heure, 3 requêtes par email par 15 min.
                                  - `POST /v1/auth/register` : 5 requêtes par IP par heure.
                                  - `POST /v1/auth/reset-password` : 10 tentatives par IP par 15 min.
                                  - `POST /v1/auth/refresh` : 60 requêtes par IP par minute.
                                - **Gestion HTTP 429 (Too Many Requests)** :
                                  - L'API retourne le code statut `429 Too Many Requests`.
                                  - L'en-tête standard `Retry-After: <secondes>` indique le temps d'attente requis avant nouvel essai.
                                  - Le corps JSON respecte le format d'erreur standardisé `ApiErrorResponse`.
                                  - **Comportement React Native attendu** : Extraire `Retry-After`, afficher un compte à rebours ou un message d'attente clair à l'utilisateur, et désactiver le bouton de validation pendant cette durée.
                                """)
                        .contact(new Contact().name("Équipe Backend Eparly").email("contact@eparly.fr"))
                        .license(new License().name("Propriétaire").url("https://eparly.fr/legal")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT RS256 d'accès utilisateur (15 minutes de validité)")));
    }
}
