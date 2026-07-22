package com.gymsync.api.auth.service;

import com.gymsync.api.auth.dto.AuthResponse;
import com.gymsync.api.auth.dto.LoginRequest;
import com.gymsync.api.auth.dto.RegisterRequest;
import com.gymsync.api.auth.dto.UserResponse;
import com.gymsync.api.auth.security.JwtService;
import com.gymsync.api.common.exception.ConflictException;
import com.gymsync.api.common.exception.UnauthorizedException;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login business logic.
 *
 * <p>Security notes: emails are normalized (trimmed + lower-cased) before any uniqueness check or
 * lookup, so {@code Foo@Example.com} and {@code foo@example.com} are the same account. Login
 * failures always return the same generic "Invalid credentials" message regardless of whether the
 * email exists, to avoid leaking account existence (spec section 6). The database's unique
 * constraint on {@code users.email} is the final authority against concurrent duplicate
 * registrations; the pre-check below is just a fast, friendly path.
 */
@Service
public class AuthService {

  private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials.";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuthenticationManager authenticationManager) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String normalizedEmail = normalizeEmail(request.email());
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new ConflictException("An account with this email already exists.");
    }

    String passwordHash = passwordEncoder.encode(request.password());
    User user = new User(normalizedEmail, passwordHash, request.name().trim(), Role.USER);
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      // Race: another request registered the same email between the check above and this insert.
      throw new ConflictException("An account with this email already exists.");
    }
    return buildAuthResponse(user);
  }

  @Transactional
  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = normalizeEmail(request.email());
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
    } catch (AuthenticationException ex) {
      // Deliberately generic: never reveal whether the email exists.
      throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
    }

    User user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));
    return buildAuthResponse(user);
  }

  private AuthResponse buildAuthResponse(User user) {
    String token = jwtService.createToken(user.getId(), user.getEmail(), user.getRole());
    return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), UserResponse.from(user));
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }
}
