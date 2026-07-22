package com.gymsync.api.program.domain;

import com.gymsync.api.user.domain.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "programs")
public class Program {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(length = 2000)
  private String description;

  @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("dayNumber ASC")
  private List<ProgramDay> days = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Program() {}

  public Program(User user, String name, String description) {
    this.user = Objects.requireNonNull(user);
    this.name = Objects.requireNonNull(name);
    this.description = description;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void rename(String name, String description) {
    this.name = Objects.requireNonNull(name);
    this.description = description;
  }

  public void addDay(ProgramDay day) {
    days.add(day);
  }

  public void removeDay(ProgramDay day) {
    days.remove(day);
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public List<ProgramDay> getDays() {
    return days;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getVersion() {
    return version;
  }
}
