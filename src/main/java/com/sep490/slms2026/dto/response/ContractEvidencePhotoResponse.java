package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractEvidencePhotoResponse {
    private String url;
    private LocalDateTime capturedAt;
}
