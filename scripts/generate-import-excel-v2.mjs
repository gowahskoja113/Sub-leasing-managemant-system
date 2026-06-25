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

/** Ma trận 6 trường hợp nghiệp vụ (sheet tham khảo, BE không đọc). */
const scenarioMatrix = [
  [
    'Mã hợp đồng (demo)',
    'Loại hình (BE)',
    'TB chủ bàn giao (đợt 1)',
    'Có dòng cải tạo (đợt 2 sheet 1)',
    'Có TB mua mới (đợt 2 sheet 2)',
    'Diễn giải',
  ],
  [
    'HD-WH-RENO-FURN',
    'Nguyên căn',
    'Có',
    'Có',
    'Có',
    'Thuê nguyên căn — cải tạo + mua nội thất thêm (mặc định phổ biến)',
  ],
  [
    'HD-WH-NORENO-FURN',
    'Nguyên căn',
    'Có',
    'Không',
    'Không',
    'Thuê nguyên căn — không cải tạo, chỉ TB chủ bàn giao (đợt 1). Không có dòng đợt 2.',
  ],
  [
    'HD-WH-RENO-NOFURN',
    'Nguyên căn',
    'Không',
    'Có',
    'Không',
    'Thuê nguyên căn — chỉ cải tạo, không mua TB mới',
  ],
  [
    'HD-WH-NORENO-NOFURN',
    'Nguyên căn',
    'Không',
    'Không',
    'Không',
    'Thuê nguyên căn — trống, không cải tạo không TB. Không có dòng đợt 2.',
  ],
  [
    'HD-ROOM-RENO-FURN',
    'Theo phòng',
    'Có',
    'Có',
    'Có',
    'Thuê theo phòng — mặc định có cải tạo + mua nội thất từng phòng',
  ],
  [
    'HD-ROOM-RENO-NOFURN',
    'Theo phòng',
    'Không',
    'Có',
    'Không',
    'Thuê theo phòng — chỉ cải tạo, không mua TB mới',
  ],
  [''],
  [
    'Cách BE suy ra từ file đợt 2 (không cần cột Có cải tạo / Có nội thất):',
  ],
  ['- Có cải tạo ⇔ tồn tại ≥1 dòng sheet "1. Hop_Dong_Cai_Tao" cho mã HĐ đó'],
  ['- Có nội thất mua thêm ⇔ tồn tại ≥1 dòng sheet "2. Thiet_Bi_Mua_Moi" cho mã HĐ đó'],
  ['- TB chủ bàn giao ⇔ chỉ có ở đợt 1 sheet "2. Thiet_Bi_Ban_Giao" (hiển thị, không vận hành)'],
  ['- Nguyên căn vs theo phòng: quyết định ở BE khi code đợt 1 (không có trong file đợt 2)'],
  ['  Gợi ý: prefix mã demo HD-WH-* = nguyên căn, HD-ROOM-* = theo phòng — hoặc rule nghiệp vụ BE chốt'],
  ['- Nhà THEO_PHONG: sheet "3. Danh_Sach_Phong" BẮT BUỘC đủ số phòng = cột Tổng số phòng'],
];

/** Sinh số phòng 101, 102, … — khớp logic ensureRoomNumbersForIndividualProperty (BE). */
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
  return buildRoomNumbers(totalRooms).map((roomNumber, i) => [
    contractCode,
    roomNumber,
    inferFloor(roomNumber, totalFloors),
    perRoomArea,
    `Phòng ${roomNumber}`,
  ]);
}

function buildPurchasedRowsForAllRooms(contractCode, totalRooms, warrantyStart) {
  const catalogs = ['Giường', 'Điều hòa', 'Tủ quần áo', 'Nóng lạnh'];
  const prices = [4_200_000, 8_000_000, 3_200_000, 2_500_000];
  const months = [12, 24, 12, 12];
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
      1,
      prices[catIdx],
      m,
      start,
      end,
      `Lắp mới phòng ${roomNumber}`,
    ];
  });
}

function buildHandoverRowsForRooms(contractCode, roomNumbers, device = 'Quạt') {
  return roomNumbers.map((roomNumber) => [
    contractCode,
    device,
    `TB chủ bàn giao phòng ${roomNumber}`,
    'GOOD',
    1,
    '',
  ]);
}

// --- Đợt 1: không có Xã/Phường ---

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
    '[NGUYEN_CAN] Biệt thự — cải tạo + mua nội thất thêm.',
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
    '[NGUYEN_CAN] Vào ở ngay — chỉ TB chủ bàn giao, không cải tạo.',
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
    '[NGUYEN_CAN] Cải tạo hoàn thiện, không mua TB mới.',
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
    '[NGUYEN_CAN] Nhà trống hoàn toàn, không cải tạo không TB.',
  ],
  [
    'HD-ROOM-RENO-FURN',
    'Nhà trọ Bình Thạnh',
    '45 Phan Văn Trị',
    'Bình Thạnh',
    'TP. Hồ Chí Minh',
    120,
    3,
    10,
    'Lê Thị Hương',
    320_000_000,
    '2026-04-01',
    '2028-03-31',
    '[THEO_PHONG] 10 phòng (101–110) — cải tạo + mua nội thất từng phòng.',
  ],
  [
    'HD-ROOM-RENO-NOFURN',
    'Nhà trọ sinh viên Cầu Giấy',
    '24 Xuân Thủy',
    'Cầu Giấy',
    'Hà Nội',
    110,
    4,
    10,
    'Hoàng Văn Nam',
    380_000_000,
    '2026-07-01',
    '2029-06-30',
    '[THEO_PHONG] 10 phòng — chỉ cải tạo, không mua TB.',
  ],
];

const handoverHeader = [
  'Mã hợp đồng thuê',
  'Tên thiết bị',
  'Mô tả chi tiết',
  'Trạng thái thiết bị',
  'Số lượng',
  'Ghi chú',
];

const roomListHeader = [
  'Mã hợp đồng thuê',
  'Số phòng',
  'Tầng',
  'Diện tích phòng (m²)',
  'Ghi chú',
];

const roomListRows = [
  ...buildRoomListRows('HD-ROOM-RENO-FURN', 10, 3, 120),
  ...buildRoomListRows('HD-ROOM-RENO-NOFURN', 10, 4, 110),
];

const handoverRows = [
  ['HD-WH-RENO-FURN', 'Điều hòa', 'Máy lạnh 2HP tầng 1', 'GOOD', 2, 'Chủ bàn giao'],
  ['HD-WH-RENO-FURN', 'Tủ lạnh', 'Tủ 250L cũ', 'GOOD', 1, ''],
  ['HD-WH-NORENO-FURN', 'Điều hòa', '3 máy lạnh các phòng', 'GOOD', 1, 'Phòng 101'],
  ['HD-WH-NORENO-FURN', 'Điều hòa', 'Máy lạnh phòng 102', 'GOOD', 1, ''],
  ['HD-WH-NORENO-FURN', 'Máy giặt', 'Máy giặt sân sau', 'GOOD', 1, ''],
  // THEO_PHONG: bàn giao lẻ từng phòng (phòng 101–110) — ghi vị trí trong Mô tả, không gán phòng
  ...buildHandoverRowsForRooms('HD-ROOM-RENO-FURN', buildRoomNumbers(10), 'Điều hòa'),
];

const huongDanDot1 = [
  ['Hướng dẫn — Đợt 1: Khởi tạo nhà'],
  [''],
  ['Sheet "1. Hop_Dong_Thue": tạo Property + hợp đồng thuê inbound.'],
  ['Sheet "2. Thiet_Bi_Ban_Giao" (tùy chọn): TB chủ nhà gốc bàn giao — CHỈ HIỂN THỊ, không gán phòng/khu vực.'],
  ['Sheet "3. Danh_Sach_Phong" (BẮT BUỘC nếu [THEO_PHONG]): liệt kê ĐỦ từng phòng = Tổng số phòng.'],
  ['Sheet "0. Ma_Tran_Trường_Hop": bảng demo 6 trường hợp nghiệp vụ (BE không đọc).'],
  [''],
  ['Địa chỉ / Zone:'],
  ['- Chỉ dùng Quận/Huyện + Tỉnh/Thành phố (2 cấp Zone). KHÔNG có cột Xã/Phường.'],
  ['- Địa chỉ chi tiết ghi đủ số nhà, ngõ/đường.'],
  [''],
  ['Tag trong Mô tả chi tiết (demo): [NGUYEN_CAN] hoặc [THEO_PHONG] — gợi ý cho BE khi code.'],
];

// --- Đợt 2: chỉ các HĐ có cải tạo và/hoặc mua TB ---

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
  ['HD-WH-RENO-NOFURN', 'FURNITURE', 'Nội thất', 80_000_000, 'Hoàn thiện nội thất cao cấp (không mua TB riêng)'],
  ['HD-WH-RENO-NOFURN', 'PLUMBING', 'Điện nước', 30_000_000, 'Smart home'],
  ['HD-ROOM-RENO-FURN', 'FLOORING', 'Sàn nhà', 35_000_000, 'Lát gạch phòng 101–110'],
  ['HD-ROOM-RENO-FURN', 'FURNITURE', 'Nội thất', 28_000_000, 'Ngân sách nội thất chung 10 phòng'],
  ['HD-ROOM-RENO-NOFURN', 'PAINTING', 'Sơn sửa', 15_000_000, 'Sơn hành lang + 10 phòng'],
  ['HD-ROOM-RENO-NOFURN', 'PLUMBING', 'Điện nước', 12_000_000, 'Sửa WC từng phòng'],
];

const purchasedHeader = [
  'Mã hợp đồng thuê',
  'Số phòng',
  'Khu vực chung',
  'Tên Catalog thiết bị',
  'Trạng thái thiết bị',
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
    1,
    16_500_000,
    36,
    '2026-04-01',
    '2029-03-31',
    'Điều hòa multi phòng khách',
  ],
  [
    'HD-WH-RENO-FURN',
    '201',
    '',
    'Giường',
    'NEW',
    1,
    7_500_000,
    24,
    '2026-04-01',
    '2028-03-31',
    'Phòng ngủ Master',
  ],
  // THEO_PHONG: mua TB mới cho ĐỦ 10 phòng (101–110)
  ...buildPurchasedRowsForAllRooms('HD-ROOM-RENO-FURN', 10, '2026-05-01'),
];

const huongDanDot2 = [
  ['Hướng dẫn — Đợt 2: Cải tạo + thiết bị mua mới'],
  [''],
  ['Phân biệt trường hợp KHÔNG cần cột riêng — chỉ cần có/không dòng theo mã HĐ:'],
  [''],
  ['| Cải tạo | TB mua mới | Cách nhận biết |'],
  ['| Có | Có | Có dòng sheet 1 VÀ sheet 2 | → HD-WH-RENO-FURN, HD-ROOM-RENO-FURN'],
  ['| Có | Không | Chỉ có dòng sheet 1 | → HD-WH-RENO-NOFURN, HD-ROOM-RENO-NOFURN'],
  ['| Không | Không | Không có dòng nào ở đợt 2 cho mã HĐ | → HD-WH-NORENO-*'],
  [''],
  ['TB chủ bàn giao (không cải tạo, không mua): chỉ ở đợt 1 — HD-WH-NORENO-FURN'],
  ['Nhà trống hoàn toàn: không sheet đợt 1 TB, không sheet đợt 2 — HD-WH-NORENO-NOFURN'],
  [''],
  ['Nhà THEO_PHONG có mua TB: sheet "2. Thiet_Bi_Mua_Moi" phải có dòng cho TỪNG phòng (101…110).'],
  ['Xem chi tiết sheet "0. Ma_Tran_Trường_Hop".'],
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
  { name: '3. Danh_Sach_Phong', data: [roomListHeader, ...roomListRows] },
]);

const wb2 = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanDot2 },
  { name: '0. Ma_Tran_Trường_Hop', data: scenarioMatrix },
  {
    name: '0. Danh_Muc_Tham_Khao',
    data: [...renovationCategories, [''], ...equipmentCatalog, [''], ...houseAreas],
  },
  { name: '1. Hop_Dong_Cai_Tao', data: [renovationHeader, ...renovationRows] },
  { name: '2. Thiet_Bi_Mua_Moi', data: [purchasedHeader, ...purchasedRows] },
]);

XLSX.writeFile(wb1, OUT_DOT1);
XLSX.writeFile(wb2, OUT_DOT2);

console.log('Đã tạo file đợt 1:', OUT_DOT1);
console.log(`  - ${leaseRows.length} hợp đồng (6 trường hợp)`);
console.log(`  - ${handoverRows.length} dòng TB bàn giao`);
console.log(`  - ${roomListRows.length} dòng danh sách phòng (THEO_PHONG)`);
console.log('Đã tạo file đợt 2:', OUT_DOT2);
console.log(`  - ${renovationRows.length} dòng cải tạo`);
console.log(`  - ${purchasedRows.length} dòng TB mua mới (gồm 10 phòng/HD-ROOM-RENO-FURN)`);
console.log('  - Không có dòng cho: HD-WH-NORENO-FURN, HD-WH-NORENO-NOFURN');
