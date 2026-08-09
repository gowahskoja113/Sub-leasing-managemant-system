package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdAndReadFalseOrderByIdDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdOrderByIdDesc(UUID userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND (:types IS NULL OR n.type IN :types) ORDER BY n.id DESC")
    Page<Notification> findByUserIdAndTypeInOrderByIdDesc(@Param("userId") UUID userId, @Param("types") List<String> types, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.read = false AND (:types IS NULL OR n.type IN :types) ORDER BY n.id DESC")
    Page<Notification> findByUserIdAndReadFalseAndTypeInOrderByIdDesc(@Param("userId") UUID userId, @Param("types") List<String> types, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId")
    void markAllAsRead(@Param("userId") UUID userId);
}
