package com.eparly.api.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;

/**
 * Contrôleur distribuant la documentation interactive Scalar pour les APIs Eparly.
 * Uniquement accessible lorsque l'OpenAPI est actif en environnement local ou de test.
 */
@Controller
public class ScalarDocsController {

    private final Environment environment;
    private final boolean openApiPublic;

    private static final String SCALAR_HTML = """
            <!doctype html>
            <html lang="fr">
              <head>
                <title>Eparly API Reference | Scalar Documentation</title>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <meta name="description" content="Documentation interactive moderne des APIs Eparly avec Scalar." />
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&display=swap" rel="stylesheet">
                <style>
                  :root {
                    --scalar-font: 'Space Grotesk', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                  }
                  body {
                    margin: 0;
                    padding: 0;
                    background-color: #0b0c10;
                  }
                  noscript {
                    display: block;
                    padding: 2rem;
                    color: #fff;
                    font-family: sans-serif;
                    background: #1f1b2e;
                  }
                  noscript a {
                    color: #8b5cf6;
                  }
                </style>
              </head>
              <body>
                <script
                  id="api-reference"
                  data-url="/v3/api-docs"
                  data-configuration='{
                    "theme": "purple",
                    "layout": "modern",
                    "showSidebar": true,
                    "searchHotKey": "k",
                    "metaData": {
                      "title": "Eparly API Reference"
                    }
                  }'>
                </script>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                <noscript>
                  <h2>Eparly API Reference</h2>
                  <p>JavaScript est nécessaire pour afficher l'interface interactive Scalar.</p>
                  <p>Vous pouvez consulter la spécification brute sur <a href="/v3/api-docs">/v3/api-docs</a> ou Swagger UI sur <a href="/swagger-ui.html">/swagger-ui.html</a>.</p>
                </noscript>
              </body>
            </html>
            """;

    public ScalarDocsController(
            Environment environment,
            @Value("${eparly.openapi.public:false}") boolean openApiPublic
    ) {
        this.environment = environment;
        this.openApiPublic = openApiPublic;
    }

    private boolean isOpenApiAllowed() {
        return openApiPublic
                && environment.acceptsProfiles(Profiles.of("local", "test"))
                && !environment.acceptsProfiles(Profiles.of("prod"));
    }

    @GetMapping(value = {"/scalar", "/scalar/", "/docs", "/docs/"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> renderScalar() {
        if (!isOpenApiAllowed()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(SCALAR_HTML);
    }
}
