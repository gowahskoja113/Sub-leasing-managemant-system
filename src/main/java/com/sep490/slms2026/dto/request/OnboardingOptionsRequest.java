package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnboardingOptionsRequest {

    @NotNull
    private Boolean wholeHouse;

    @NotNull
    private Boolean hasRenovation;
}
