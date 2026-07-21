package com.sep490.slms2026.dto.request;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Ảnh bằng chứng onboard kèm thời điểm chụp.
 * {@code url} bắt buộc; {@code capturedAt} optional — null thì BE set = thời điểm lưu.
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
