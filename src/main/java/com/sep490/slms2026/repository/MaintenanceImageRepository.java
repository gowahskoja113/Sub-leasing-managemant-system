package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceImageRepository extends JpaRepository<MaintenanceImage, Long> {

    List<MaintenanceImage> findByMaintenanceRequestIdOrderByCreatedAtAsc(Long maintenanceRequestId);

    boolean existsByMaintenanceRequestIdAndImageUrlAndType(
            Long maintenanceRequestId, String imageUrl, com.sep490.slms2026.enums.MaintenancePhotoType type);
}
