package com.wunderlore;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import jakarta.persistence.EntityManagerFactory;

// Persistence is off by default, so the full context must start with no database at all:
// no DataSource, no EntityManagerFactory and no Flyway.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WunderloreApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Value("${local.server.port}")
	private int port;

	@Test
	void contextLoads() {
	}

	@Test
	void shouldNotCreateAnyDatabaseBeanWhilePersistenceIsDisabled() {
		assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
		assertThat(context.getBeanNamesForType(EntityManagerFactory.class)).isEmpty();
		assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
	}

	@Test
	void shouldServeRequestsWithoutADatabase() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("UP");
	}

}
