package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InvoiceUnlockFailCounter;
import com.sep490.slms2026.entity.InvoiceUnlockFailCounterId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceUnlockFailCounterRepository
        extends JpaRepository<InvoiceUnlockFailCounter, InvoiceUnlockFailCounterId> {
}
