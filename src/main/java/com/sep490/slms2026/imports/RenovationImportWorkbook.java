package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RenovationImportWorkbook {

    private List<ExploitationConfigImportRow> configRows;
    private List<RoomImportRow> roomRows;
    private List<RenovationImportRow> renovationLines;
    private List<PurchasedEquipmentImportRow> purchasedRows;
}
