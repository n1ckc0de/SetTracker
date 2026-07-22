package com.gymsync.api.auth.security;

import com.gymsync.api.user.domain.User;
import com.gymsync.api.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads {@link UserPrincipal}s for Spring Security. {@link #loadUserByUsername(String)} is keyed by
 * (normalized) email, used by the {@code AuthenticationManager} during login. {@link
 * #loadUserById(UUID)} is used by {@link JwtAuthenticationFilter} once a JWT's subject (the user
 * id) has been verified.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public CustomUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No user with the given email."));
    return UserPrincipal.fromUser(user);
  }

  @Transactional(readOnly = true)
  public UserDetails loadUserById(UUID id) throws UsernameNotFoundException {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("No user with the given id."));
    return UserPrincipal.fromUser(user);
  }
}
