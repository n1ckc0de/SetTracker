package com.gymsync.api.coach.repository;

import com.gymsync.api.coach.domain.CoachRecommendation;
import com.gymsync.api.coach.domain.RecommendationStatus;
import com.gymsync.api.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachRecommendationRepository extends JpaRepository<CoachRecommendation, UUID> {

  List<CoachRecommendation> findByUserOrderByCreatedAtDesc(User user);

  List<CoachRecommendation> findByUserAndStatusOrderByCreatedAtDesc(
      User user, RecommendationStatus status);

  Optional<CoachRecommendation> findByIdAndUserId(UUID id, UUID userId);

  Optional<CoachRecommendation> findFirstByUserAndExerciseIdAndStatusOrderByCreatedAtDesc(
      User user, UUID exerciseId, RecommendationStatus status);
}
