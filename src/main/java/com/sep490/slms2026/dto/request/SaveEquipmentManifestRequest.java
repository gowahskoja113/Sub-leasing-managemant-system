package com.sep490.slms2026.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveEquipmentManifestRequest {

    @NotEmpty
    @Valid
    private List<EquipmentManifestItemRequest> items;
}
