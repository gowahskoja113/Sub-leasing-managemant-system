package com.sep490.slms2026.service;

public interface ContractDocumentStorage {

    /**
     * Lưu file hợp đồng DOCX và trả URL public (pattern giống {@link PropertyImageStorage}).
     */
    String store(String contractCode, String filename, byte[] content);
}
