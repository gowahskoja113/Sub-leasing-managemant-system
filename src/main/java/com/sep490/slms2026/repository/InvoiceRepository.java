package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Invoice;
import com.sep490.slms2026.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Page<Invoice> findByMonthAndStatusAndDeletedFalse(String month, InvoiceStatus status, Pageable pageable);
    Page<Invoice> findByMonthAndDeletedFalse(String month, Pageable pageable);
    Page<Invoice> findByStatusAndDeletedFalse(InvoiceStatus status, Pageable pageable);
    Page<Invoice> findByDeletedFalse(Pageable pageable);
}
