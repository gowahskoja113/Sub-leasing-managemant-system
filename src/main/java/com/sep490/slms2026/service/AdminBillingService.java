package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.AdminHostDto;
import com.sep490.slms2026.dto.response.AdminInvoiceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminBillingService {
    Page<AdminInvoiceDto> getAdminInvoices(String month, String hostId, String status, String keyword, Pageable pageable);
    List<AdminHostDto> getAdminHosts();
}
