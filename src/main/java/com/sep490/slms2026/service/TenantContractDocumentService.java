package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.TenantContractDocumentResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;

import java.util.List;
import java.util.UUID;

public interface TenantContractDocumentService {

    /** Xuất DOCX từ template, lưu storage, cập nhật documentUrl trên HĐ. */
    TenantContractDocumentResponse generateAndStore(Long contractId);

    /** Trả metadata + URL file đã lưu (không tạo mới nếu chưa có — dùng generateAndStore). */
    TenantContractDocumentResponse getDocument(Long contractId);

    TenantContractResponse getContractForUser(Long contractId, UUID userId, String roleName);

    List<TenantContractResponse> listContractsForTenant(UUID tenantUserId);
}
