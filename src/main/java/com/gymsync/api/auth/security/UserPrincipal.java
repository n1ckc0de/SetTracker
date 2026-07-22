package com.gymsync.api.auth.security;

import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal backed by the authenticated {@link User}. Carries the user's UUID id so
 * downstream controllers/services never need a second lookup just to know "who is making this
 * request".
 */
public final class UserPrincipal implements UserDetails {

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final Role role;
  private final Collection<? extends GrantedAuthority> authorities;

  public UserPrincipal(UUID id, String email, String passwordHash, Role role) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.email = Objects.requireNonNull(email, "email must not be null");
    this.passwordHash = passwordHash;
    this.role = Objects.requireNonNull(role, "role must not be null");
    this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  public static UserPrincipal fromUser(User user) {
    return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
  }

  public UUID getId() {
    return id;
  }

  public Role getRole() {
    return role;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
