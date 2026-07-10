package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.ContractTerminationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TerminateContractRequest {

    @NotNull(message = "Loại thanh lý không được để trống")
    private ContractTerminationType type;

    @NotBlank(message = "Lý do thanh lý không được để trống")
    private String reason;

    /** Ngày chấm dứt / trả phòng thực tế. Mặc định hôm nay nếu không gửi. */
    private LocalDate effectiveDate;

    /** Ghi chú nội bộ (tùy chọn). */
    private String note;
}
