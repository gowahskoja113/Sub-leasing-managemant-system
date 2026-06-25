/**
 * Sinh 2 file Excel import theo quy trình mới (2 đợt).
 * Chạy: node scripts/generate-import-excel-v2.mjs
 */
import XLSX from 'xlsx';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.join(__dirname, '..', 'docs');

const OUT_DOT1 = path.join(DOCS, 'SLMS2026_import_dot1_khoi_tao.xlsx');
const OUT_DOT2 = path.join(DOCS, 'SLMS2026_import_dot2_cai_tao.xlsx');
const OUT_SUPPLEMENT = path.join(DOCS, 'SLMS2026_import_cai_tao_bo_sung.xlsx');

const renovationCategories = [
  ['Mã danh mục', 'Tên danh mục', 'Mô tả'],
  ['PAINTING', 'Sơn sửa', 'Sơn tường, trần nhà'],
  ['PLUMBING', 'Điện nước', 'Sửa chữa hệ thống điện nước'],
  ['FLOORING', 'Sàn nhà', 'Lát sàn, sửa sàn'],
  ['FURNITURE', 'Nội thất', 'Mua sắm nội thất mới'],
  ['EQUIPMENT', 'Thiết bị mua thêm', 'Mua thêm thiết bị trong đợt cải tạo'],
  ['STRUCTURAL', 'Kết cấu', 'Thay đổi kết cấu, vách ngăn'],
  ['OTHER', 'Khác', 'Hạng mục cải tạo khác'],
];

const equipmentCatalog = [
  ['Tên thiết bị (catalog)', 'Mô tả'],
  ['Điều hòa', 'Máy lạnh / điều hòa không khí'],
  ['Tủ lạnh', 'Tủ lạnh các loại'],
  ['Máy giặt', 'Máy giặt cửa trước / cửa trên'],
  ['Bàn ăn', 'Bàn ăn và ghế'],
  ['Giường', 'Giường ngủ các loại'],
  ['Tủ quần áo', 'Tủ đựng quần áo'],
  ['Bếp từ', 'Bếp từ / bếp gas'],
  ['Nóng lạnh', 'Máy nước nóng'],
  ['Quạt', 'Quạt điện / quạt trần'],
  ['Khác', 'Thiết bị khác'],
];

const houseAreas = [
  ['Mã khu vực', 'Mô tả'],
  ['LIVING_ROOM', 'Phòng khách'],
  ['KITCHEN', 'Nhà bếp'],
  ['BATHROOM', 'Phòng tắm / WC'],
  ['BALCONY', 'Ban công'],
  ['GARAGE', 'Gara / sân'],
  ['OTHER', 'Khu vực khác'],
];

const scenarioMatrix = [
  [
    'Mã hợp đồng (demo)',
    'Đợt 1 TB bàn giao',
    'Đợt 2 hình thức',
    'Đợt 2 cải tạo',
    'Đợt 2 mua TB',
    'Diễn giải',
  ],
  [
    'HD-WH-RENO-FURN',
    'Có',
    'NGUYEN_CAN',
    'Có',
    'Có',
    'Thuê nguyên căn — giữ nguyên căn sau cải tạo + mua nội thất',
  ],
  [
    'HD-WH-NORENO-FURN',
    'Có',
    '—',
    '—',
    '—',
    'Không cải tạo — sau đợt 1 gửi Host luôn (không có file đợt 2)',
  ],
  [
    'HD-WH-RENO-NOFURN',
    'Không',
    'NGUYEN_CAN',
    'Có',
    'Không',
    'Thuê nguyên căn — chỉ cải tạo, không mua TB mới',
  ],
  [
    'HD-WH-NORENO-NOFURN',
    'Không',
    '—',
    '—',
    '—',
    'Nhà trống, không cải tạo — gửi Host sau đợt 1',
  ],
  [
    'HD-WH-RENO-SPLIT',
    'Có',
    'THEO_PHONG (5 phòng)',
    'Có',
    'Có',
    'Thuê nguyên căn đợt 1 — đợt 2 chia 5 phòng khai thác + mua TB từng phòng',
  ],
  [''],
  ['Quy tắc:'],
  ['- Đợt 1: luôn import nhà nguyên căn (HĐ thuê từ chủ). TB bàn giao chỉ ghi nhận, không gắn phòng.'],
  ['- Đợt 2: sheet "1. Cau_Hinh_Khai_Thac" quyết định NGUYEN_CAN hay THEO_PHONG.'],
  ['- THEO_PHONG: sheet "2. Danh_Sach_Phong" bắt buộc đủ Số phòng khai thác.'],
  ['- HD-*-NORENO-*: không có dòng trong file đợt 2.'],
];

function buildRoomNumbers(totalRooms) {
  const rooms = [];
  let floor = 1;
  let indexOnFloor = 1;
  while (rooms.length < totalRooms) {
    rooms.push(`${floor}${String(indexOnFloor).padStart(2, '0')}`);
    indexOnFloor++;
    if (indexOnFloor > 99) {
      indexOnFloor = 1;
      floor++;
    }
  }
  return rooms;
}

function inferFloor(roomNumber, totalFloors) {
  const digits = roomNumber.replace(/\D/g, '');
  if (digits.length >= 3) {
    const floor = parseInt(digits.slice(0, -2), 10);
    if (floor >= 1 && (!totalFloors || floor <= totalFloors)) {
      return floor;
    }
  }
  return 1;
}

function buildRoomListRows(contractCode, totalRooms, totalFloors, areaSize) {
  const perRoomArea = Math.round((areaSize / totalRooms) * 10) / 10;
  return buildRoomNumbers(totalRooms).map((roomNumber) => [
    contractCode,
    roomNumber,
    inferFloor(roomNumber, totalFloors),
    perRoomArea,
    `Phòng ${roomNumber}`,
  ]);
}

function buildPurchasedRowsForAllRooms(contractCode, totalRooms, warrantyStart) {
  const catalogs = ['Giường', 'Điều hòa', 'Tủ quần áo', 'Nóng lạnh', 'Quạt'];
  const prices = [4_200_000, 8_000_000, 3_200_000, 2_500_000, 800_000];
  const months = [12, 24, 12, 12, 6];
  return buildRoomNumbers(totalRooms).map((roomNumber, i) => {
    const catIdx = i % catalogs.length;
    const m = months[catIdx];
    const start = warrantyStart;
    const endYear = parseInt(start.slice(0, 4), 10) + Math.floor(m / 12);
    const endMonth = parseInt(start.slice(5, 7), 10) + (m % 12);
    const end = `${endYear}-${String(((endMonth - 1) % 12) + 1).padStart(2, '0')}-28`;
    return [
      contractCode,
      roomNumber,
      '',
      catalogs[catIdx],
      'NEW',
      'THEM_MOI',
      1,
      prices[catIdx],
      m,
      start,
      end,
      `Lắp mới phòng ${roomNumber}`,
    ];
  });
}

// --- Đợt 1 ---

const leaseHeader = [
  'Mã hợp đồng',
  'Tên tòa nhà',
  'Địa chỉ chi tiết',
  'Quận/Huyện',
  'Tỉnh/Thành phố',
  'Diện tích (m²)',
  'Tổng số tầng',
  'Tổng số phòng',
  'Tên chủ nhà',
  'Tổng tiền thuê',
  'Ngày bắt đầu',
  'Ngày kết thúc',
  'Mô tả chi tiết',
];

const leaseRows = [
  [
    'HD-WH-RENO-FURN',
    'Villa Thảo Điền View',
    '12 Nguyễn Văn Hưởng',
    'Quận 3',
    'TP. Hồ Chí Minh',
    180,
    2,
    5,
    'Trần Minh Tuấn',
    450_000_000,
    '2026-03-01',
    '2029-02-28',
    'Biệt thự thuê nguyên căn — chờ cải tạo đợt 2.',
  ],
  [
    'HD-WH-NORENO-FURN',
    'Nhà phố Gò Vấp',
    '88 Quang Trung',
    'Gò Vấp',
    'TP. Hồ Chí Minh',
    95,
    2,
    4,
    'Phạm Văn Đức',
    280_000_000,
    '2026-05-01',
    '2031-04-30',
    'Vào ở ngay — chỉ TB chủ bàn giao, không cải tạo.',
  ],
  [
    'HD-WH-RENO-NOFURN',
    'Penthouse Quận 1',
    '101 Lê Lợi',
    'Quận 1',
    'TP. Hồ Chí Minh',
    200,
    1,
    4,
    'Đặng Quốc Bảo',
    550_000_000,
    '2026-09-01',
    '2031-08-31',
    'Cải tạo hoàn thiện, không mua TB mới.',
  ],
  [
    'HD-WH-NORENO-NOFURN',
    'Căn hộ mini Phú Nhuận',
    '23 Hoàng Văn Thụ',
    'Phú Nhuận',
    'TP. Hồ Chí Minh',
    55,
    1,
    1,
    'Nguyễn Thu Hà',
    180_000_000,
    '2026-06-01',
    '2027-05-31',
    'Nhà trống hoàn toàn, không cải tạo.',
  ],
  [
    'HD-WH-RENO-SPLIT',
    'Nhà trọ Bình Thạnh',
    '45 Phan Văn Trị',
    'Bình Thạnh',
    'TP. Hồ Chí Minh',
    120,
    3,
    8,
    'Lê Thị Hương',
    320_000_000,
    '2026-04-01',
    '2028-03-31',
    'Thuê nguyên căn từ chủ — đợt 2 sẽ chia phòng khai thác.',
  ],
];

const handoverHeader = [
  'Mã hợp đồng thuê',
  'Tên thiết bị',
  'Mô tả chi tiết',
  'Mô tả vị trí',
  'Trạng thái thiết bị',
  'Số lượng',
  'Ghi chú',
];

const handoverRows = [
  ['HD-WH-RENO-FURN', 'Điều hòa', 'Máy lạnh 2HP cũ', 'Tầng 1, phòng khách', 'GOOD', 2, 'Chủ bàn giao'],
  ['HD-WH-RENO-FURN', 'Tủ lạnh', 'Tủ 250L', 'Nhà bếp', 'GOOD', 1, ''],
  ['HD-WH-NORENO-FURN', 'Điều hòa', '3 máy lạnh', 'Các phòng trong nhà', 'GOOD', 3, ''],
  ['HD-WH-NORENO-FURN', 'Máy giặt', 'Máy giặt sân sau', 'Sân sau', 'GOOD', 1, ''],
  ['HD-WH-RENO-SPLIT', 'Quạt', 'Quạt trần cũ', 'Toàn nhà', 'GOOD', 5, 'TB chủ bàn giao trước cải tạo'],
];

const huongDanDot1 = [
  ['Hướng dẫn — Đợt 1: Khởi tạo nhà (HĐ thuê từ chủ)'],
  [''],
  ['Sheet "1. Hop_Dong_Thue": tạo Property + hợp đồng thuê — LUÔN nguyên căn.'],
  ['Sheet "2. Thiet_Bi_Ban_Giao" (tùy chọn): TB chủ bàn giao — CHỈ HIỂN THỊ, không gắn phòng vận hành.'],
  ['"Tổng số phòng" = đặc tính vật lý căn nhà thuê, không phải số phòng khai thác.'],
  ['Chia phòng / hình thức khai thác → quyết định ở file đợt 2.'],
  ['Xem sheet "0. Ma_Tran_Trường_Hop".'],
];

// --- Đợt 2 ---

const configHeader = [
  'Mã hợp đồng thuê',
  'Hình thức khai thác',
  'Số phòng khai thác',
];

const configRows = [
  ['HD-WH-RENO-FURN', 'NGUYEN_CAN', ''],
  ['HD-WH-RENO-NOFURN', 'NGUYEN_CAN', ''],
  ['HD-WH-RENO-SPLIT', 'THEO_PHONG', 5],
];

const roomListHeader = [
  'Mã hợp đồng thuê',
  'Số phòng',
  'Tầng',
  'Diện tích phòng (m²)',
  'Ghi chú',
];

const roomListRows = buildRoomListRows('HD-WH-RENO-SPLIT', 5, 3, 120);

const renovationHeader = [
  'Mã hợp đồng thuê',
  'Mã danh mục cải tạo',
  'Tên danh mục (Gợi ý)',
  'Chi phí cải tạo (VNĐ)',
  'Ghi chú chi tiết',
];

const renovationRows = [
  ['HD-WH-RENO-FURN', 'PAINTING', 'Sơn sửa', 18_000_000, 'Sơn lại toàn bộ trong nhà'],
  ['HD-WH-RENO-FURN', 'PLUMBING', 'Điện nước', 22_000_000, 'Thay đường ống tầng 2'],
  ['HD-WH-RENO-NOFURN', 'FURNITURE', 'Nội thất', 80_000_000, 'Hoàn thiện nội thất cao cấp'],
  ['HD-WH-RENO-NOFURN', 'PLUMBING', 'Điện nước', 30_000_000, 'Smart home'],
  ['HD-WH-RENO-SPLIT', 'FLOORING', 'Sàn nhà', 25_000_000, 'Lát gạch 5 phòng'],
  ['HD-WH-RENO-SPLIT', 'PLUMBING', 'Điện nước', 15_000_000, 'Sửa WC từng phòng'],
];

const purchasedHeader = [
  'Mã hợp đồng thuê',
  'Số phòng',
  'Khu vực chung',
  'Tên Catalog thiết bị',
  'Trạng thái thiết bị',
  'Hành động',
  'Số lượng',
  'Đơn giá (VNĐ)',
  'Số tháng bảo hành',
  'Ngày bắt đầu bảo hành',
  'Ngày hết bảo hành',
  'Ghi chú lắp đặt',
];

const purchasedRows = [
  [
    'HD-WH-RENO-FURN',
    '',
    'LIVING_ROOM',
    'Điều hòa',
    'NEW',
    'THEM_MOI',
    1,
    16_500_000,
    36,
    '2026-04-01',
    '2029-03-31',
    'Điều hòa multi phòng khách',
  ],
  [
    'HD-WH-RENO-FURN',
    '',
    'KITCHEN',
    'Bếp từ',
    'NEW',
    'THEM_MOI',
    1,
    5_500_000,
    24,
    '2026-04-01',
    '2028-03-31',
    'Bếp từ nhà bếp',
  ],
  ...buildPurchasedRowsForAllRooms('HD-WH-RENO-SPLIT', 5, '2026-05-01'),
];

const huongDanDot2 = [
  ['Hướng dẫn — Đợt 2: Cấu hình khai thác + cải tạo + TB mua mới'],
  [''],
  ['Sheet "1. Cau_Hinh_Khai_Thac" (BẮT BUỘC): quyết định NGUYEN_CAN hoặc THEO_PHONG.'],
  ['Sheet "2. Danh_Sach_Phong": bắt buộc nếu THEO_PHONG — đủ Số phòng khai thác.'],
  ['Sheet "3. Hop_Dong_Cai_Tao" (tùy chọn): chi phí cải tạo.'],
  ['Sheet "4. Thiet_Bi_Mua_Moi" (tùy chọn): TB mua + bảo hành; cột Hành động = THEM_MOI (mặc định) hoặc THAY_THE.'],
  [''],
  ['HD-*-NORENO-*: không có trong file đợt 2 (đã gửi Host sau đợt 1).'],
  ['Xem sheet "0. Ma_Tran_Trường_Hop".'],
];

function sheetFromAoA(data, colWidths) {
  const ws = XLSX.utils.aoa_to_sheet(data);
  const headerRow = data[0] ?? [];
  ws['!cols'] = (colWidths ?? headerRow).map((h) => ({
    wch: Math.max(String(h).length + 4, 18),
  }));
  return ws;
}

function buildWorkbook(sheets) {
  const wb = XLSX.utils.book_new();
  for (const { name, data, colWidths } of sheets) {
    XLSX.utils.book_append_sheet(wb, sheetFromAoA(data, colWidths), name);
  }
  return wb;
}

const wb1 = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanDot1 },
  { name: '0. Ma_Tran_Trường_Hop', data: scenarioMatrix },
  {
    name: '0. Danh_Muc_Tham_Khao',
    data: [...equipmentCatalog, [''], ...houseAreas],
  },
  { name: '1. Hop_Dong_Thue', data: [leaseHeader, ...leaseRows] },
  { name: '2. Thiet_Bi_Ban_Giao', data: [handoverHeader, ...handoverRows] },
]);

const wb2 = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanDot2 },
  { name: '0. Ma_Tran_Trường_Hop', data: scenarioMatrix },
  {
    name: '0. Danh_Muc_Tham_Khao',
    data: [...renovationCategories, [''], ...equipmentCatalog, [''], ...houseAreas],
  },
  { name: '1. Cau_Hinh_Khai_Thac', data: [configHeader, ...configRows] },
  { name: '2. Danh_Sach_Phong', data: [roomListHeader, ...roomListRows] },
  { name: '3. Hop_Dong_Cai_Tao', data: [renovationHeader, ...renovationRows] },
  { name: '4. Thiet_Bi_Mua_Moi', data: [purchasedHeader, ...purchasedRows] },
]);

XLSX.writeFile(wb1, OUT_DOT1);
XLSX.writeFile(wb2, OUT_DOT2);

// --- Cải tạo bổ sung (lần 2+) — 2 sheet giống sheet 3+4 của đợt 2 ---

const supplementRenovationRows = [
  ['HD-WH-RENO-FURN', 'PAINTING', 'Sơn sửa', 8_000_000, 'Cải tạo bổ sung lần 2 — sơn lại phòng ngủ'],
  ['HD-WH-RENO-FURN', 'EQUIPMENT', 'Thiết bị', 3_500_000, 'Thay router WiFi toàn nhà'],
];

const supplementPurchasedRows = [
  [
    'HD-WH-RENO-FURN',
    '',
    'LIVING_ROOM',
    'Điều hòa',
    'NEW',
    'THAY_THE',
    1,
    18_500_000,
    36,
    '2027-01-01',
    '2030-12-31',
    'Thay điều hòa mới phòng khách (disable bản v1)',
  ],
  [
    'HD-WH-RENO-FURN',
    '',
    'LIVING_ROOM',
    'Quạt',
    'NEW',
    'THEM_MOI',
    2,
    1_200_000,
    12,
    '2027-01-01',
    '2027-12-31',
    'Thêm quạt trần — giữ nguyên đồ cũ',
  ],
];

const huongDanSupplement = [
  ['Hướng dẫn — Cải tạo bổ sung (lần 2, 3…)'],
  [''],
  ['Điều kiện: nhà đang ACTIVE → gọi API start-renovation → UNDER_RENOVATION (session ≥ 2).'],
  ['API: POST /api/v1/import/renovation-supplement-excel?dryRun='],
  [''],
  ['Sheet "1. Hop_Dong_Cai_Tao": chi phí cải tạo đợt này (gắn session hiện tại).'],
  ['Sheet "2. Thiet_Bi_Mua_Moi": TB mua — THEM_MOI giữ TB cũ; THAY_THE disable TB ACTIVE cùng vị trí + catalog.'],
  ['KHÔNG có sheet cấu hình khai thác / danh sách phòng.'],
];

const wbSupplement = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanSupplement },
  {
    name: '0. Danh_Muc_Tham_Khao',
    data: [...renovationCategories, [''], ...equipmentCatalog, [''], ...houseAreas],
  },
  { name: '1. Hop_Dong_Cai_Tao', data: [renovationHeader, ...supplementRenovationRows] },
  { name: '2. Thiet_Bi_Mua_Moi', data: [purchasedHeader, ...supplementPurchasedRows] },
]);

XLSX.writeFile(wbSupplement, OUT_SUPPLEMENT);

console.log('Đã tạo file đợt 1:', OUT_DOT1);
console.log(`  - ${leaseRows.length} hợp đồng (luôn nguyên căn)`);
console.log(`  - ${handoverRows.length} dòng TB bàn giao`);
console.log('Đã tạo file đợt 2:', OUT_DOT2);
console.log(`  - ${configRows.length} dòng cấu hình khai thác`);
console.log(`  - ${roomListRows.length} dòng danh sách phòng (THEO_PHONG)`);
console.log(`  - ${renovationRows.length} dòng cải tạo`);
console.log(`  - ${purchasedRows.length} dòng TB mua mới`);
console.log('Đã tạo file cải tạo bổ sung:', OUT_SUPPLEMENT);
console.log(`  - ${supplementRenovationRows.length} dòng cải tạo mẫu`);
console.log(`  - ${supplementPurchasedRows.length} dòng TB mua mẫu`);
