package com.sep490.slms2026.vision;

import com.sep490.slms2026.dto.response.VisionLabelItem;

import java.util.List;

/**
 * Strategy nhận diện nhãn trên ảnh (Google Cloud Vision, model local ONNX, …).
 * API công khai FE vẫn là {@code POST /api/v1/vision/labels} — không đổi.
 */
public interface VisionProvider {

    /** "google" | "local" */
    String name();

    /** Key/model đã sẵn sàng hay chưa. */
    boolean isAvailable();

    /**
     * Nhận diện nhãn. Provider mạng dùng {@link ImageSource#url()};
     * provider local gọi {@link ImageSource#bytes()} (tải lazy, cache 1 lần).
     */
    List<VisionLabelItem> detect(ImageSource src);
}
