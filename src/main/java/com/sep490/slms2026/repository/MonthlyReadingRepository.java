package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MonthlyReading;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReadingRepository extends JpaRepository<MonthlyReading, Long> {
}
