package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterOverridePasscodeResponse {
    private Long id;
    /** Chỉ hiện đủ khi mã còn hiệu lực; đã dùng/hết hạn có thể mask phía service. */
    private String code;
    private UUID createdBy;
    private String note;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private UUID usedBy;
    private LocalDateTime createdAt;
    private boolean usable;
    private String message;
}
