package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MeterOverrideLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MeterOverrideLogRepository extends JpaRepository<MeterOverrideLog, Long> {

    @Query("SELECT l FROM MeterOverrideLog l ORDER BY l.createdAt DESC")
    List<MeterOverrideLog> findAllOrderByCreatedAtDesc();
}
