package com.sep490.slms2026.dto.request;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Ảnh bằng chứng onboard.
 * {@code url} bắt buộc. {@code capturedAt} từ client bị bỏ — BE luôn đóng dấu giờ server lúc lưu.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractEvidencePhotoRequest {
    private String url;
    private LocalDateTime capturedAt;
}
