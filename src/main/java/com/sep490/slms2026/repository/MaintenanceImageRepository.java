package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenanceImageRepository extends JpaRepository<MaintenanceImage, UUID> {

}
