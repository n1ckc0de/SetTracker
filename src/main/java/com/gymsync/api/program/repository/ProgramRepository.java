package com.gymsync.api.program.repository;

import com.gymsync.api.program.domain.Program;
import com.gymsync.api.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProgramRepository
    extends JpaRepository<Program, UUID>, JpaSpecificationExecutor<Program> {

  List<Program> findByUserOrderByUpdatedAtDesc(User user);

  Optional<Program> findByIdAndUserId(UUID id, UUID userId);
}
