package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TenantDraftContractImportWorkbook {

    private List<TenantDraftContractImportRow> rows;
}
