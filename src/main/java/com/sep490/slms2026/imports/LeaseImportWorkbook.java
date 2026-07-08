package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LeaseImportWorkbook {

    private List<LeaseContractImportRow> leaseContracts;
    private List<HandoverEquipmentImportRow> handoverRows;
}
