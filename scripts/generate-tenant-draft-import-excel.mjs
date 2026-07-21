/**
 * Sinh file Excel demo import hàng loạt hợp đồng thuê nháp (DRAFT).
 * BĐS tham chiếu từ ma trận đợt 1/2: docs/SLMS2026_import_matrix_dot1.xlsx
 *
 * Chạy: node scripts/generate-tenant-draft-import-excel.mjs
 */
import XLSX from 'xlsx';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.join(__dirname, '..', 'docs');
const OUT = path.join(DOCS, 'SLMS2026_import_tenant_draft_contracts.xlsx');

const guide = [
  ['Hướng dẫn import hợp đồng thuê nháp (DRAFT)'],
  [''],
  ['1. BĐS phải ĐÃ TỒN TẠI trong hệ thống (sau import đợt 1 + đợt 2 + Host duyệt → ACTIVE).'],
  ['2. Ưu tiên điền "Mã HĐ inbound" (= mã cột "Mã hợp đồng" file đợt 1 / matrix).'],
  ['3. Có thể dùng "Mã BĐS" (ID) hoặc "Tên tòa nhà" nếu không trùng tên.'],
  ['4. Loại thuê: NGUYEN_CAN (nguyên căn) hoặc THEO_PHONG (theo phòng).'],
  ['5. Thuê theo phòng → bắt buộc "Số phòng" (vd 101, 102 — cùng file matrix đợt 2).'],
  ['6. Ngày: YYYY-MM-DD hoặc DD/MM/YYYY.'],
  ['7. Nếu có "Số tháng cọc" mà trống "Tiền cọc" → BE tính deposit = giá thuê × số tháng.'],
  ['8. API: POST /api/v1/import/tenant-draft-contracts-excel?dryRun=true|false'],
  ['9. Auth: ADMIN hoặc MANAGER.'],
  [''],
  ['Tham chiếu BĐS demo từ ma trận:'],
  ['Mã HĐ inbound', 'Tên tòa nhà (matrix)', 'Loại khai thác', 'Phòng mẫu'],
  ['HD-MTX-01-NORENO-NOFURN', 'MTX#01 NORENO trống', 'NGUYEN_CAN', '—'],
  ['HD-MTX-02-NORENO-FURN', 'MTX#02 NORENO có TB', 'NGUYEN_CAN', '—'],
  ['HD-MTX-03-RENO-WH-NOFURN', 'MTX#03 NGUYEN_CAN cải tạo', 'NGUYEN_CAN', '—'],
  ['HD-MTX-07-RENO-RM-NOFURN', 'MTX#07 THEO_PHONG cải tạo', 'THEO_PHONG', '101, 102, 103'],
  ['HD-MTX-08-RENO-RM-EQUIP', 'MTX#08 THEO_PHONG mua TB', 'THEO_PHONG', '101, 102, 103'],
  ['HD-MTX-10-RENO-RM-HO-EQUIP', 'MTX#10 THEO_PHONG đủ', 'THEO_PHONG', '101, 102, 103'],
];

// Lấy đúng mã từ generate-import-excel-matrix.mjs
const headers = [
  'Mã HĐ inbound',
  'Mã BĐS',
  'Tên tòa nhà',
  'Loại thuê',
  'Số phòng',
  'Họ tên khách thuê',
  'CCCD',
  'Số điện thoại',
  'Ngày sinh',
  'Ngày cấp CCCD',
  'Nơi cấp CCCD',
  'Hộ khẩu thường trú',
  'Ngày vào ở',
  'Ngày kết thúc',
  'Giá thuê/tháng',
  'Số tháng cọc',
  'Tiền cọc',
  'Ngày đón khách dự kiến',
];

const demoRows = [
  // Nguyên căn — MTX#01
  [
    'HD-MTX-01-NORENO-NOFURN',
    '',
    'MTX#01 NORENO trống',
    'NGUYEN_CAN',
    '',
    'Nguyễn Văn An',
    '079085001001',
    '0901000001',
    '1990-05-12',
    '2021-03-15',
    'CA TP. Hồ Chí Minh',
    '123 Nguyễn Huệ, Q1, TP.HCM',
    '2026-08-01',
    '2027-07-31',
    8_000_000,
    1,
    8_000_000,
    '2026-08-01',
  ],
  // Nguyên căn — MTX#02
  [
    'HD-MTX-02-NORENO-FURN',
    '',
    'MTX#02 NORENO có TB',
    'NGUYEN_CAN',
    '',
    'Trần Thị Bình',
    '079085001002',
    '0901000002',
    '15/08/1992',
    '10/06/2022',
    'CA Quận 1',
    '45 Lê Lợi, Q1, TP.HCM',
    '01/09/2026',
    '31/08/2027',
    9_500_000,
    2,
    '', // BE tính = 19_000_000
    '01/09/2026',
  ],
  // Theo phòng — MTX#07 phòng 101
  [
    'HD-MTX-07-RENO-RM-NOFURN',
    '',
    'MTX#07 THEO_PHONG cải tạo',
    'THEO_PHONG',
    '101',
    'Lê Minh Châu',
    '079085001003',
    '0901000003',
    '1995-01-20',
    '2020-11-01',
    'CA Bình Thạnh',
    '88 Phạm Văn Đồng, Bình Thạnh, TP.HCM',
    '2026-08-15',
    '2027-08-14',
    4_500_000,
    1,
    4_500_000,
    '2026-08-15',
  ],
  // Theo phòng — MTX#07 phòng 102
  [
    'HD-MTX-07-RENO-RM-NOFURN',
    '',
    'MTX#07 THEO_PHONG cải tạo',
    'THEO_PHONG',
    '102',
    'Phạm Quốc Dũng',
    '079085001004',
    '0901000004',
    '1988-12-03',
    '2019-07-20',
    'CA Gò Vấp',
    '12 Quang Trung, Gò Vấp, TP.HCM',
    '2026-08-15',
    '2027-02-14',
    4_200_000,
    1,
    4_200_000,
    '2026-08-15',
  ],
  // Theo phòng — MTX#10 phòng 101
  [
    'HD-MTX-10-RENO-RM-HO-EQUIP',
    '',
    'MTX#10 THEO_PHONG đủ',
    'THEO_PHONG',
    '101',
    'Hoàng Thị Em',
    '079085001005',
    '0901000005',
    '1998-04-08',
    '2023-01-05',
    'CA Phú Nhuận',
    '56 Nguyễn Văn Trỗi, Phú Nhuận, TP.HCM',
    '2026-09-01',
    '2027-08-31',
    5_000_000,
    2,
    10_000_000,
    '2026-09-01',
  ],
];

const wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(guide), '0. Huong_Dan');
XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([headers, ...demoRows]), '1. Hop_Dong_Nhap_Khach');

const refHeaders = ['Mã HĐ inbound (đợt 1)', 'Tên tòa (matrix)', 'Loại', 'Ghi chú'];
const refRows = [
  ['HD-MTX-01-NORENO-NOFURN', 'MTX#01 NORENO trống', 'NGUYEN_CAN', 'Import sau khi nhà ACTIVE'],
  ['HD-MTX-02-NORENO-FURN', 'MTX#02 NORENO có TB', 'NGUYEN_CAN', 'Có TB bàn giao'],
  ['HD-MTX-03-RENO-WH-NOFURN', 'MTX#03 NGUYEN_CAN cải tạo', 'NGUYEN_CAN', 'Sau đợt 2 + Host'],
  ['HD-MTX-07-RENO-RM-NOFURN', 'MTX#07 THEO_PHONG cải tạo', 'THEO_PHONG', 'Phòng 101–103'],
  ['HD-MTX-08-RENO-RM-EQUIP', 'MTX#08 THEO_PHONG mua TB', 'THEO_PHONG', 'Phòng 101–103'],
  ['HD-MTX-10-RENO-RM-HO-EQUIP', 'MTX#10 THEO_PHONG đủ', 'THEO_PHONG', 'Phòng 101–103'],
];
XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([refHeaders, ...refRows]), '0. Tham_Chieu_BDS');

XLSX.writeFile(wb, OUT);
console.log('Wrote', OUT);
console.log('Demo rows:', demoRows.length);
console.log('Note: mã HĐ inbound phải khớp file matrix đã import vào DB.');
