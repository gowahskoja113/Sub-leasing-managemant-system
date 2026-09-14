package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyAbsorbedResponse {
    private BigDecimal equivalentReplacement;
    private List<TenantOnOldPriceResponse> tenantsOnOldPrice;
}
