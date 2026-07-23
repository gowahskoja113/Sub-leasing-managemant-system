package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.MaintenancePhotoType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class MaintenancePhotoHistoryResponse {
    private MaintenancePhotoType type;
    private String url;
    private LocalDateTime createdAt;
}
