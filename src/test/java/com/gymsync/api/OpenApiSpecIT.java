package com.gymsync.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@code docs/openapi.yaml} synchronized with the live springdoc specification.
 *
 * <p>Default behavior (CI / {@code ./mvnw verify}): fail if the committed file drifts from {@code
 * GET /v3/api-docs.yaml}.
 *
 * <p>Regenerate locally:
 *
 * <pre>{@code
 * ./mvnw -Dopenapi.update=true -Dtest=OpenApiSpecIT -Dit.test=OpenApiSpecIT verify
 * }</pre>
 *
 * <p>Uses MockMvc against the Testcontainers-backed Spring context — no separate process, no
 * readiness sleeps, and no production credentials.
 */
class OpenApiSpecIT extends AbstractIntegrationTest {

  private static final Path COMMITTED_SPEC = Path.of("docs", "openapi.yaml");

  @Test
  void openApiYaml_matchesCommittedSpecification() throws Exception {
    String generated =
        mockMvc
            .perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    String normalized = normalize(generated);
    assertThat(normalized)
        .as("springdoc must return a non-empty OpenAPI YAML document")
        .startsWith("openapi:");

    if (Boolean.getBoolean("openapi.update")) {
      Files.createDirectories(COMMITTED_SPEC.getParent());
      Files.writeString(COMMITTED_SPEC, normalized, StandardCharsets.UTF_8);
      return;
    }

    assertThat(COMMITTED_SPEC)
        .as("Committed OpenAPI spec missing. Generate with -Dopenapi.update=true")
        .exists();
    String committed = normalize(Files.readString(COMMITTED_SPEC, StandardCharsets.UTF_8));
    assertThat(normalized)
        .as(
            "docs/openapi.yaml is out of date. Regenerate with:"
                + " ./mvnw -Dopenapi.update=true -Dit.test=OpenApiSpecIT verify")
        .isEqualTo(committed);
  }

  private static String normalize(String yaml) {
    return yaml.replace("\r\n", "\n").trim() + "\n";
  }
}
