package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InvoiceUnlockLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceUnlockLogRepository extends JpaRepository<InvoiceUnlockLog, Long> {

    List<InvoiceUnlockLog> findAllByOrderByCreatedAtDesc();
}
