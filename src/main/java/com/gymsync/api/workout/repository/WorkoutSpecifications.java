package com.gymsync.api.workout.repository;

import com.gymsync.api.user.domain.User;
import com.gymsync.api.workout.domain.Workout;
import com.gymsync.api.workout.domain.WorkoutSource;
import com.gymsync.api.workout.domain.WorkoutStatus;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the optional {@code /api/workouts} list filters as {@link Specification} predicates
 * instead of a single static JPQL query with {@code :param IS NULL OR ...} placeholders. Only
 * filters the caller actually supplies end up in the generated SQL at all, which avoids Postgres
 * failing to infer a bind type for parameters that would otherwise only ever be compared via {@code
 * IS NULL} (see docs/DECISIONS.md).
 */
public final class WorkoutSpecifications {

  private WorkoutSpecifications() {}

  public static Specification<Workout> forUser(User user) {
    return (root, query, cb) -> cb.equal(root.get("user"), user);
  }

  public static Specification<Workout> hasStatus(WorkoutStatus status) {
    return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  public static Specification<Workout> hasSource(WorkoutSource source) {
    return source == null ? null : (root, query, cb) -> cb.equal(root.get("source"), source);
  }

  public static Specification<Workout> startedAtFrom(Instant from) {
    return from == null
        ? null
        : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startedAt"), from);
  }

  public static Specification<Workout> startedAtTo(Instant to) {
    return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startedAt"), to);
  }
}
