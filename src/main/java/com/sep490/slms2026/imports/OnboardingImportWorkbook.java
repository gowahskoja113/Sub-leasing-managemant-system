package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class OnboardingImportWorkbook {

    private List<LeaseContractImportRow> leaseContracts;
    private List<RenovationImportRow> renovationLines;
    private List<EquipmentImportRow> equipmentRows;
}
