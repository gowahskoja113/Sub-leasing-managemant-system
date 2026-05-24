package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class ZoneResponse {
    private UUID id;
    private String name;
    private String description;
}