package com.gymsync.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.auth.dto.RegisterRequest;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real PostgreSQL database (via Testcontainers) and the full
 * Spring web/security stack. Subclasses get:
 *
 * <ul>
 *   <li>A single Postgres container + Flyway-migrated schema shared across the whole test run.
 *   <li>A configured {@link MockMvc} for exercising controllers end-to-end.
 *   <li>A {@link #registerUser} helper that drives the real {@code /api/auth/register} endpoint, so
 *       tests exercise the same registration/JWT-issuance path a real client would.
 * </ul>
 *
 * <p>The container is started eagerly in a static initializer (the "singleton container" pattern)
 * rather than being managed by {@code @Testcontainers}/{@code @Container}. Those annotations start
 * and stop the container per test class; since this container is a single static field shared by
 * every subclass, letting the JUnit extension manage it would stop it after the first test class
 * finishes and leave later classes pointed at a dead container/stale port. Starting it once here
 * and never stopping it (Ryuk reaps it when the JVM exits) keeps the mapped port stable for the
 * {@link DynamicPropertySource} below across the entire test run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("gymsync")
          .withUsername("gymsync")
          .withPassword("gymsync");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired protected MockMvc mockMvc;

  @Autowired protected ObjectMapper objectMapper;

  /** Result of registering a user through the real endpoint: id, email, and a usable JWT. */
  protected record RegisteredUser(UUID id, String email, String accessToken) {

    public String authorizationHeader() {
      return "Bearer " + accessToken;
    }
  }

  protected RegisteredUser registerUser(String email, String password, String name)
      throws Exception {
    String requestBody =
        objectMapper.writeValueAsString(new RegisterRequest(email, password, name));

    String responseBody =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode json = objectMapper.readTree(responseBody);
    JsonNode userNode = json.get("user");
    if (userNode == null || json.get("accessToken") == null) {
      throw new IllegalStateException(
          "Registration did not return the expected payload: " + responseBody);
    }
    return new RegisteredUser(
        UUID.fromString(userNode.get("id").asText()),
        userNode.get("email").asText(),
        json.get("accessToken").asText());
  }
}
