package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.DepositAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepositAuditLogRepository extends JpaRepository<DepositAuditLog, Long> {
}
