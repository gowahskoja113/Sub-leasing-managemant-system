package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.AdminHostDto;
import com.sep490.slms2026.dto.response.AdminInvoiceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import com.sep490.slms2026.dto.response.AdminDepositDto;

public interface AdminBillingService {
    List<AdminHostDto> getAdminHosts();
    Page<AdminDepositDto> getAdminDeposits(String status, Pageable pageable);
}
