package com.gymsync.api.program.domain;

import com.gymsync.api.exercise.domain.Exercise;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "program_day_exercises")
public class ProgramDayExercise {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "program_day_id", nullable = false)
  private ProgramDay programDay;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "exercise_id", nullable = false)
  private Exercise exercise;

  @Column(name = "order_in_day", nullable = false)
  private Integer orderInDay;

  @Column(name = "target_sets", nullable = false)
  private Integer targetSets;

  @Column(name = "target_reps_min")
  private Integer targetRepsMin;

  @Column(name = "target_reps_max")
  private Integer targetRepsMax;

  @Column(name = "target_weight", precision = 8, scale = 2)
  private BigDecimal targetWeight;

  protected ProgramDayExercise() {}

  public ProgramDayExercise(
      ProgramDay programDay,
      Exercise exercise,
      Integer orderInDay,
      Integer targetSets,
      Integer targetRepsMin,
      Integer targetRepsMax,
      BigDecimal targetWeight) {
    this.programDay = Objects.requireNonNull(programDay);
    this.exercise = Objects.requireNonNull(exercise);
    this.orderInDay = Objects.requireNonNull(orderInDay);
    this.targetSets = Objects.requireNonNull(targetSets);
    this.targetRepsMin = targetRepsMin;
    this.targetRepsMax = targetRepsMax;
    this.targetWeight = targetWeight;
  }

  public UUID getId() {
    return id;
  }

  public ProgramDay getProgramDay() {
    return programDay;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public Integer getOrderInDay() {
    return orderInDay;
  }

  public Integer getTargetSets() {
    return targetSets;
  }

  public Integer getTargetRepsMin() {
    return targetRepsMin;
  }

  public Integer getTargetRepsMax() {
    return targetRepsMax;
  }

  public BigDecimal getTargetWeight() {
    return targetWeight;
  }
}
