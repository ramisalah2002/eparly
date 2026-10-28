package com.eparly.api.imports;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.eparly.api.TestJwtConfiguration;
import com.eparly.api.account.domain.Account;
import com.eparly.api.account.repository.AccountRepository;
import com.eparly.api.user.domain.User;
import com.eparly.api.user.domain.UserStatus;
import com.eparly.api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static com.eparly.api.imports.ImportTestSupport.MAPPING_FR;
import static com.eparly.api.imports.ImportTestSupport.statement;
import static com.eparly.api.imports.ImportTestSupport.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aucune donnee financiere (libelle, montant) ne doit apparaitre dans les logs applicatifs,
 * y compris au niveau DEBUG du code Eparly, pendant preview, import reussi et import refuse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class ImportLogLeakIntegrationTest {

    private static final String SECRET_LABEL = "MARQUEUR-LIBELLE-CONFIDENTIEL-7QX";
    private static final String SECRET_REJECTED_LABEL = "MARQUEUR-REFUSE-CONFIDENTIEL-9ZK";
    private static final String SECRET_AMOUNT_TEXT = "98765,43";
    private static final String SECRET_AMOUNT_MINOR = "9876543";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Aucun libelle ni montant dans les logs pendant preview, import reussi, reimport et import refuse")
    void financialDataNeverReachesTheLogs() throws Exception {
        User created = new User("log-" + UUID.randomUUID() + "@eparly.test", "DummyPasswordHash2026!");
        created.setStatus(UserStatus.ACTIVE);
        User user = userRepository.saveAndFlush(created);
        Account account = accountRepository.saveAndFlush(new Account(user, "Compte courant"));
        ImportHttpClient http = new ImportHttpClient(mockMvc, jwtEncoder, objectMapper);

        byte[] valid = utf8(statement("04/03/2025;;" + SECRET_LABEL + ";-" + SECRET_AMOUNT_TEXT));
        byte[] rejected = utf8(statement(
                "04/03/2025;;" + SECRET_REJECTED_LABEL + ";-" + SECRET_AMOUNT_TEXT,
                "31/02/2025;;AUTRE;-1,00"));

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Logger application = (Logger) LoggerFactory.getLogger("com.eparly");
        Level previousLevel = application.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        application.setLevel(Level.DEBUG);
        try {
            http.preview(user, account.getId(), valid, null).andExpect(status().isOk());
            http.importCsv(user, account.getId(), valid, MAPPING_FR).andExpect(status().isCreated());
            http.importCsv(user, account.getId(), valid, MAPPING_FR).andExpect(status().isCreated());
            http.importCsv(user, account.getId(), rejected, MAPPING_FR).andExpect(status().isUnprocessableContent());
        } finally {
            root.detachAppender(appender);
            application.setLevel(previousLevel);
        }

        List<ILoggingEvent> events = List.copyOf(appender.list);
        assertThat(events).as("des logs doivent avoir ete produits pendant le scenario").isNotEmpty();
        for (ILoggingEvent event : events) {
            String rendered = event.getFormattedMessage() + " " + describe(event.getThrowableProxy());
            assertThat(rendered)
                    .doesNotContain(SECRET_LABEL)
                    .doesNotContain(SECRET_REJECTED_LABEL)
                    .doesNotContain(SECRET_AMOUNT_TEXT)
                    .doesNotContain(SECRET_AMOUNT_MINOR);
        }
        assertThat(events.stream().map(ILoggingEvent::getFormattedMessage))
                .anyMatch(message -> message.contains("Import completed"))
                .anyMatch(message -> message.contains("Import rejected"));
    }

    private static String describe(IThrowableProxy throwable) {
        if (throwable == null) {
            return "";
        }
        return throwable.getClassName() + ":" + throwable.getMessage() + " " + describe(throwable.getCause());
    }
}
