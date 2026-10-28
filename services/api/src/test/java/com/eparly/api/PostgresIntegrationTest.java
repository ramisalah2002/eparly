package com.eparly.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
class PostgresIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Should successfully connect to PostgreSQL container via HikariCP")
	void shouldConnectToDatabase() throws SQLException {
		assertThat(dataSource).isNotNull();
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(2)).isTrue();
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
		}
	}

	@Test
	@DisplayName("Should have executed Flyway V1 to V8 migrations in flyway_schema_history")
	void shouldHaveFlywayHistory() {
		Integer migrationCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
				Integer.class
		);
		assertThat(migrationCount).isNotNull().isEqualTo(1);
		Integer v3Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = true",
				Integer.class
		);
		assertThat(v3Count).isNotNull().isEqualTo(1);
		Integer v4Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = true",
				Integer.class
		);
		assertThat(v4Count).isNotNull().isEqualTo(1);
		Integer v5Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = true",
				Integer.class
		);
		assertThat(v5Count).isNotNull().isEqualTo(1);
		Integer v6Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = true",
				Integer.class
		);
		assertThat(v6Count).isNotNull().isEqualTo(1);
		Integer v7Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = true",
				Integer.class
		);
		assertThat(v7Count).isNotNull().isEqualTo(1);
		Integer v8Count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = true",
				Integer.class
		);
		assertThat(v8Count).isNotNull().isEqualTo(1);
	}

	@Test
	@DisplayName("Should verify pgcrypto extension is installed by Flyway")
	void shouldVerifyPgcryptoExtension() {
		Integer extensionCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM pg_extension WHERE extname = 'pgcrypto'",
				Integer.class
		);
		assertThat(extensionCount).isNotNull().isEqualTo(1);
	}
}
