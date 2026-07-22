package com.gymsync.api.auth.security;

import com.gymsync.api.common.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Convenience accessors for the currently authenticated {@link UserPrincipal}. Controllers
 * generally prefer {@code @AuthenticationPrincipal UserPrincipal}; this helper exists for code
 * (e.g. auditing, non-web entry points) that needs the current user without a method parameter.
 */
public final class SecurityUtils {

  private SecurityUtils() {}

  public static UserPrincipal getCurrentUserPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      throw new UnauthorizedException("Authentication is required.");
    }
    return principal;
  }

  public static UUID getCurrentUserId() {
    return getCurrentUserPrincipal().getId();
  }
}
