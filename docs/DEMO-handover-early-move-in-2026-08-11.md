# Demo 11/08/2026 — HĐ nháp nhận nhà sớm / đúng ngày

**File import:** `docs/SLMS2026_import_tenant_draft_contracts.xlsx`  
**Rule BE:** `contract.max-early-move-in-days: 3` (`application.yaml`)  
**Logic:** `TenantOnboardingServiceImpl.confirmContract` — nếu `today < moveInDate` thì `daysEarly = moveIn − today`; `> 3` → reject, `≤ 3` → set `moveInDate`/`startDate` = today, **giữ** `endDate`.

Regenerate: `node docs/generate-matrix-50.js`

---

## Phân bổ 25 dòng

| Nhóm | Case | SL | Ngày vào ở (kế hoạch) | daysEarly nếu confirm **11/08** | Kỳ vọng |
|------|------|----|------------------------|----------------------------------|---------|
| **A** Đúng ngày | A1–A8 | 8 | `2026-08-11` | 0 | OK |
| **B** Sớm ≤3 ngày | B1–B3 | 3 | `2026-08-12` | 1 | OK → moveIn = 11/08 |
| | B4–B6 | 3 | `2026-08-13` | 2 | OK → moveIn = 11/08 |
| | B7–B9 | 3 | `2026-08-14` | 3 (biên) | OK → moveIn = 11/08 |
| **C** Sớm >3 ngày | C1–C2 | 2 | `2026-08-15` | 4 | REJECT |
| | C3–C4 | 2 | `2026-08-16` | 5 | REJECT |
| | C5 | 1 | `2026-08-18` | 7 | REJECT |
| | C6 | 1 | `2026-08-20` | 9 | REJECT |
| | C7 | 1 | `2026-08-25` | 14 | REJECT |
| | C8 | 1 | `2026-09-01` | ~21 | REJECT |

Map tenant / BĐS / SĐT: sheet **`0. Tham_Chieu_BDS`** trong file xlsx.

---

## Chuỗi import demo

1. Seed: `tenant01..tenant28` / `123456`
2. `docs/SLMS2026_import_matrix_dot1.xlsx`
3. `docs/SLMS2026_import_matrix_dot2.xlsx` (RENO)
4. `POST /api/v1/import/tenant-draft-contracts-excel` ← file draft này  
5. Thanh toán cọc → confirm OTP **ngày hệ thống = 11/08/2026**

Message reject nhóm C (mẫu):

```text
Chỉ được nhận nhà sớm tối đa 3 ngày so với ngày vào ở dự kiến (2026-08-15). Vui lòng cập nhật lại ngày vào ở hoặc nhận đúng lịch.
```

---

## Gợi ý kịch bản demo

| # | Tenant gợi ý | Case | Thao tác |
|---|--------------|------|----------|
| 1 | tenant01 | A1 | Confirm 11/08 → active, moveIn vẫn 11/08 |
| 2 | tenant09 | B1 (+1) | Confirm 11/08 → active, moveIn **đổi** 12→11, endDate giữ |
| 3 | tenant15 | B7 (+3 biên) | Confirm 11/08 → OK (đúng ngưỡng) |
| 4 | tenant18 | C1 (+4) | Confirm 11/08 → **lỗi**; sửa moveIn về ≤14/08 hoặc nhận đúng lịch |

tenant seed index = `#` cột trong sheet tham chiếu (`tenant01`…`tenant25`).
