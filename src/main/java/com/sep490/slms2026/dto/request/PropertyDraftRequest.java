package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PropertyDraftRequest {

    @NotBlank
    private String propertyName;

    @NotBlank
    private String address;

    @NotBlank
    private String descriptions;

    @NotNull
    private UUID zoneId;

    private Double areaSize;

    @NotNull
    @Min(1)
    private Integer totalFloor;

    @NotNull
    @Min(1)
    private Integer totalRooms;

    @NotNull
    private Long createdBy;

    private List<String> imageUrls;
}
