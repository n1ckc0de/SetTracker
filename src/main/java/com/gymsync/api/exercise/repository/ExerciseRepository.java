package com.gymsync.api.exercise.repository;

import com.gymsync.api.exercise.domain.Exercise;
import com.gymsync.api.exercise.domain.ExerciseCategory;
import com.gymsync.api.user.domain.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

  @Query(
      """
      SELECT e FROM Exercise e
      WHERE (e.createdBy IS NULL OR e.createdBy = :user)
      AND (:query IS NULL OR LOWER(e.normalizedName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
      AND (:category IS NULL OR e.category = :category)
      """)
  Page<Exercise> findVisibleExercises(
      @Param("user") User user,
      @Param("query") String query,
      @Param("category") ExerciseCategory category,
      Pageable pageable);

  @Query(
      """
      SELECT e FROM Exercise e
      WHERE (e.createdBy IS NULL OR e.createdBy = :user)
      AND e.normalizedName = :normalizedName
      """)
  java.util.List<Exercise> findVisibleByNormalizedName(
      @Param("user") User user, @Param("normalizedName") String normalizedName);
}
