package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenovationSessionResponse {

    private Long id;
    private Integer sessionNumber;
    /** Nhãn hiển thị: v1, v2, … */
    private String versionLabel;
    /** IN_PROGRESS | ACTIVE | DISABLED */
    private String status;
    /** true nếu đây là đợt cải tạo đang có hiệu lực */
    private boolean currentEffective;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime disabledAt;
    private BigDecimal totalCost;
    private List<RenovationSessionLineResponse> lines;
    private List<SessionEquipmentResponse> equipments;
}
