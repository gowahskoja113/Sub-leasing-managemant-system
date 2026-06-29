package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequestResponse {
    private Long id;
    private Long contractId;
    private String contractCode;
    private String propertyName;
    private String roomNumber;
    private LocalDate expectedMoveOutDate;
    private String reason;
    private String note;
    private String status;
    private LocalDateTime createdAt;
}
