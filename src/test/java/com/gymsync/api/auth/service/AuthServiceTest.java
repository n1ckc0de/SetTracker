package com.gymsync.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymsync.api.auth.dto.AuthResponse;
import com.gymsync.api.auth.dto.LoginRequest;
import com.gymsync.api.auth.dto.RegisterRequest;
import com.gymsync.api.auth.security.JwtService;
import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.UnauthorizedException;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private JwtService jwtService;
  private AuthenticationManager authenticationManager;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    jwtService = mock(JwtService.class);
    authenticationManager = mock(AuthenticationManager.class);
    authService =
        new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);
  }

  @Test
  void register_normalizesEmail_hashesPassword_andReturnsToken() {
    RegisterRequest request =
        new RegisterRequest("  User@Example.com ", "supersecret1", "  Jane Doe  ");
    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(passwordEncoder.encode("supersecret1")).thenReturn("hashed-password");
    // saveAndFlush() is mocked, so it won't trigger Hibernate's real UUID generator;
    // assign one here to mimic what a real flush would do before the id is read.
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(
            invocation -> {
              User savedArg = invocation.getArgument(0);
              ReflectionTestUtils.setField(savedArg, "id", UUID.randomUUID());
              return savedArg;
            });
    when(jwtService.createToken(any(UUID.class), anyString(), any(Role.class)))
        .thenReturn("jwt-token");
    when(jwtService.getExpirationSeconds()).thenReturn(3600L);

    AuthResponse response = authService.register(request);

    ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userRepository).saveAndFlush(savedUserCaptor.capture());
    User savedUser = savedUserCaptor.getValue();
    assertThat(savedUser.getId()).isNotNull();
    assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
    assertThat(savedUser.getName()).isEqualTo("Jane Doe");
    assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
    assertThat(savedUser.getRole()).isEqualTo(Role.USER);

    assertThat(response.accessToken()).isEqualTo("jwt-token");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(3600L);
    assertThat(response.user().email()).isEqualTo("user@example.com");
  }

  @Test
  void register_duplicateEmail_throwsConflict() {
    RegisterRequest request = new RegisterRequest("taken@example.com", "supersecret1", "Jane Doe");
    when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);
  }

  @Test
  void login_validCredentials_returnsToken() {
    LoginRequest request = new LoginRequest("user@example.com", "supersecret1");
    User user = new User("user@example.com", "hashed-password", "Jane Doe", Role.USER);
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    UserPrincipal principal = UserPrincipal.fromUser(user);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(principal);
    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenReturn(authentication);
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(jwtService.createToken(any(), anyString(), any())).thenReturn("jwt-token");
    when(jwtService.getExpirationSeconds()).thenReturn(3600L);

    AuthResponse response = authService.login(request);

    assertThat(response.accessToken()).isEqualTo("jwt-token");
    assertThat(response.user().email()).isEqualTo("user@example.com");
  }

  @Test
  void login_badPassword_throwsGenericUnauthorized() {
    LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid credentials.");
  }

  @Test
  void login_unknownEmail_throwsSameGenericUnauthorized() {
    LoginRequest request = new LoginRequest("unknown@example.com", "supersecret1");
    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid credentials.");
  }
}
