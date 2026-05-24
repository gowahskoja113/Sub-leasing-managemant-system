package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneRequest {
    @NotBlank(message = "Tên vùng (Zone name) không được để trống")
    private String name;

    private String description;
}