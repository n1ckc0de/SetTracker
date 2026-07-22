package com.gymsync.api.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  OpenAPI gymsyncOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("GymSync API")
                .description(
                    "Gym workout-tracking backend with real-time sync and webhook check-ins.")
                .version("0.1.0")
                .contact(new Contact().name("GymSync").email("dev@example.com"))
                .license(new License().name("MIT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token obtained from /api/auth/login")));
  }
}
