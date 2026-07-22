package com.gymsync.api.auth.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymsync.api.AbstractIntegrationTest;
import com.gymsync.api.auth.dto.LoginRequest;
import com.gymsync.api.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthControllerTest extends AbstractIntegrationTest {

  @Test
  void register_withValidPayload_returns201AndToken() throws Exception {
    RegisterRequest request =
        new RegisterRequest("newuser@example.com", "supersecret1", "New User");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken", notNullValue()))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.user.name").value("New User"))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
  }

  @Test
  void register_withWeakPassword_returns400ValidationError() throws Exception {
    RegisterRequest request = new RegisterRequest("weakpass@example.com", "short", "Weak Password");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
  }

  @Test
  void register_withDuplicateEmail_returns409Conflict() throws Exception {
    RegisterRequest request =
        new RegisterRequest("duplicate@example.com", "supersecret1", "First User");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    RegisterRequest secondRequest =
        new RegisterRequest("Duplicate@Example.com", "anotherpassword1", "Second User");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void login_withValidCredentials_returns200AndToken() throws Exception {
    registerUser("logintest@example.com", "supersecret1", "Login Test");

    LoginRequest loginRequest = new LoginRequest("logintest@example.com", "supersecret1");
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken", notNullValue()))
        .andExpect(jsonPath("$.user.email").value("logintest@example.com"));
  }

  @Test
  void login_withWrongPassword_returns401GenericMessage() throws Exception {
    registerUser("badpass@example.com", "supersecret1", "Bad Pass");

    LoginRequest loginRequest = new LoginRequest("badpass@example.com", "wrong-password");
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid credentials."));
  }

  @Test
  void login_withUnknownEmail_returns401WithSameGenericMessageAsWrongPassword() throws Exception {
    LoginRequest loginRequest = new LoginRequest("doesnotexist@example.com", "supersecret1");
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid credentials."));
  }

  @Test
  void endToEnd_registerThenLoginThenAccessProtectedEndpoint() throws Exception {
    RegisteredUser user = registerUser("e2e@example.com", "supersecret1", "End To End");

    LoginRequest loginRequest = new LoginRequest("e2e@example.com", "supersecret1");
    String loginResponse =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = objectMapper.readTree(loginResponse).get("accessToken").asText();

    mockMvc
        .perform(get("/api/exercises").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // Sanity: the token obtained at registration also works.
    mockMvc
        .perform(get("/api/exercises").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk());
  }

  @Test
  void protectedEndpoint_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/exercises")).andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_withInvalidToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/exercises").header("Authorization", "Bearer not-a-real-token"))
        .andExpect(status().isUnauthorized());
  }
}
