package com.gymsync.api.program.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "program_days")
public class ProgramDay {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "program_id", nullable = false)
  private Program program;

  @Column(name = "day_number", nullable = false)
  private Integer dayNumber;

  @Column(nullable = false, length = 255)
  private String name;

  @OneToMany(mappedBy = "programDay", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderInDay ASC")
  private List<ProgramDayExercise> exercises = new ArrayList<>();

  protected ProgramDay() {}

  public ProgramDay(Program program, Integer dayNumber, String name) {
    this.program = Objects.requireNonNull(program);
    this.dayNumber = Objects.requireNonNull(dayNumber);
    this.name = Objects.requireNonNull(name);
  }

  public void rename(String name) {
    this.name = Objects.requireNonNull(name);
  }

  public void clearExercises() {
    exercises.clear();
  }

  public void addExercise(ProgramDayExercise exercise) {
    exercises.add(exercise);
  }

  public UUID getId() {
    return id;
  }

  public Program getProgram() {
    return program;
  }

  public Integer getDayNumber() {
    return dayNumber;
  }

  public String getName() {
    return name;
  }

  public List<ProgramDayExercise> getExercises() {
    return exercises;
  }
}
