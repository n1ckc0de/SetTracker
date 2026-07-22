package com.gymsync.api.program.repository;

import com.gymsync.api.program.domain.Program;
import com.gymsync.api.user.domain.User;
import org.springframework.data.jpa.domain.Specification;

/** Optional list filters for {@code /api/programs}, mirroring {@code WorkoutSpecifications}. */
public final class ProgramSpecifications {

  private ProgramSpecifications() {}

  public static Specification<Program> forUser(User user) {
    return (root, query, cb) -> cb.equal(root.get("user"), user);
  }
}
