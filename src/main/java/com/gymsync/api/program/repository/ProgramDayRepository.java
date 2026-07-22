package com.gymsync.api.program.repository;

import com.gymsync.api.program.domain.ProgramDay;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramDayRepository extends JpaRepository<ProgramDay, UUID> {

  Optional<ProgramDay> findByProgramIdAndDayNumber(UUID programId, Integer dayNumber);

  boolean existsByProgramIdAndDayNumber(UUID programId, Integer dayNumber);
}
