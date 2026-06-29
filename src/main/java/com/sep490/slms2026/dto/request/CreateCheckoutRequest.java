package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCheckoutRequest {
    @NotNull(message = "contractId không được để trống")
    private Long contractId;

    @NotNull(message = "Ngày dự kiến trả phòng không được để trống")
    private LocalDate expectedMoveOutDate;

    @NotBlank(message = "Lý do không được để trống")
    private String reason;

    private String note;
}
