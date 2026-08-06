package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.VisionLabelsResponse;

public interface VisionService {

    /**
     * Gán nhãn vật thể trên ảnh (Cloudinary URL) để FE đối chiếu với thiết bị báo hỏng.
     */
    VisionLabelsResponse detectLabels(String imageUrl);
}
