package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class ZoneRequest {
    @NotBlank(message = "Tên vùng (Zone name) không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Cấp độ (Level) không được để trống")
    @Min(value = 1, message = "Level nhỏ nhất là 1 (Tỉnh/Thành phố)")
    @Max(value = 2, message = "Level lớn nhất là 2 (Quận/Huyện)")
    private Integer level;

    private UUID parentId;
}