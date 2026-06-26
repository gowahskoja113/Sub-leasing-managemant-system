package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.HostNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HostNotificationRepository extends JpaRepository<HostNotification, Long> {

    Page<HostNotification> findByUserIdAndRead(UUID userId, boolean read, Pageable pageable);

    Page<HostNotification> findByUserId(UUID userId, Pageable pageable);

    Optional<HostNotification> findByIdAndUserId(Long id, UUID userId);

    boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE HostNotification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    void markAllRead(@Param("userId") UUID userId);
}
