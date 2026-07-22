package com.gymsync.api.auth.dto;

import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import java.util.UUID;

/** User representation for API responses. Deliberately excludes {@code passwordHash}. */
public record UserResponse(UUID id, String email, String name, Role role) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
  }
}
