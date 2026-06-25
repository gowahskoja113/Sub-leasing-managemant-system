package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MasterLease;
import com.sep490.slms2026.enums.MasterLeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MasterLeaseRepository extends JpaRepository<MasterLease, Long> {
    Page<MasterLease> findByStatusAndPropertyIdAndDeletedFalse(MasterLeaseStatus status, Long propertyId, Pageable pageable);
    Page<MasterLease> findByStatusAndDeletedFalse(MasterLeaseStatus status, Pageable pageable);
    Page<MasterLease> findByPropertyIdAndDeletedFalse(Long propertyId, Pageable pageable);
    Page<MasterLease> findByDeletedFalse(Pageable pageable);
    List<MasterLease> findByStatusAndDeletedFalse(MasterLeaseStatus status);
}
