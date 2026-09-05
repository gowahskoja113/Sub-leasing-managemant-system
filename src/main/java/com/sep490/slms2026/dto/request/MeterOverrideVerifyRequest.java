package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterOverrideVerifyRequest {

    @NotBlank
    private String passcode;

    /**
     * Optional — null khi manager đang đón khách mới (HĐ chưa tạo ở bước chỉ số).
     * Có contractId thì BE verify HĐ tồn tại và gắn token vào HĐ đó.
     */
    private Long contractId;

    /** ELEC | WATER */
    @NotBlank
    private String meterKind;
}
