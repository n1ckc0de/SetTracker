package com.gymsync.api.checkin.repository;

import com.gymsync.api.checkin.domain.CheckInEvent;
import com.gymsync.api.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInEventRepository extends JpaRepository<CheckInEvent, UUID> {

  Optional<CheckInEvent> findByExternalEventId(String externalEventId);

  Page<CheckInEvent> findByUserOrderByReceivedAtDesc(User user, Pageable pageable);
}
