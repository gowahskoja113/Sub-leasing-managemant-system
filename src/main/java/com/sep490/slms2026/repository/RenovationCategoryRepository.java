package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.RenovationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RenovationCategoryRepository extends JpaRepository<RenovationCategory, Long> {

    List<RenovationCategory> findByActiveTrueOrderByNameAsc();

    Optional<RenovationCategory> findByCode(String code);
}
