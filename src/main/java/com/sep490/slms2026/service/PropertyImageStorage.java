package com.sep490.slms2026.service;

public interface PropertyImageStorage {

    /**
     * Lưu ảnh và trả URL public có thể ghi vào {@code Property.imageUrls}.
     */
    String store(String contractCode, String originalFilename, byte[] content);
}
