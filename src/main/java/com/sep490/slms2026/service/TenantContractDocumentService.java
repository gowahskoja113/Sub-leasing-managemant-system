package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.TenantContractDocumentResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;

import java.util.List;
import java.util.UUID;

public interface TenantContractDocumentService {

    /**
     * Trả metadata + URL file HĐ đã lưu (ưu tiên {@code draftContractFileUrl} từ Cloudinary).
     * Không render/lưu file mới trên BE.
     */
    TenantContractDocumentResponse generateAndStore(Long contractId);

    /**
     * Render DOCX từ template — chỉ khi {@code status = DRAFT}.
     * FE nhận file → upload Cloudinary → PUT {@code draftContractFileUrl}.
     */
    byte[] renderDraftDocument(Long contractId);

    /** Trả metadata + URL file HĐ (ưu tiên {@code draftContractFileUrl}). */
    TenantContractDocumentResponse getDocument(Long contractId);

    /**
     * Tải nội dung file DOCX đã lưu (Cloudinary hoặc legacy local) — dùng cho nút View Contract trên FE.
     */
    byte[] downloadContractDocument(Long contractId, UUID userId, String roleName);

    TenantContractResponse getContractForUser(Long contractId, UUID userId, String roleName);

    List<TenantContractResponse> listContractsForTenant(UUID tenantUserId);
}
