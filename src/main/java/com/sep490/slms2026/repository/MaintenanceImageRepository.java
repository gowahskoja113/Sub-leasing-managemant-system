package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceImage;
import com.sep490.slms2026.enums.MaintenancePhotoType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceImageRepository extends JpaRepository<MaintenanceImage, Long> {

    List<MaintenanceImage> findByMaintenanceRequestIdOrderByCreatedAtAsc(Long maintenanceRequestId);

    boolean existsByMaintenanceRequestIdAndImageUrlAndType(
            Long maintenanceRequestId, String imageUrl, MaintenancePhotoType type);

    List<MaintenanceImage> findByMaintenanceRequestIdAndImageUrlAndType(
            Long maintenanceRequestId, String imageUrl, MaintenancePhotoType type);

    void deleteByMaintenanceRequestIdAndImageUrlAndType(
            Long maintenanceRequestId, String imageUrl, MaintenancePhotoType type);
}
