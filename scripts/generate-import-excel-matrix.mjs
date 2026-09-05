/**
 * Sinh bộ Excel ma trận onboarding 50 căn + cải tạo bổ sung.
 *
 * 40 NGUYEN_CAN + 10 THEO_PHONG (≥3 phòng/căn).
 * NT khai thác chỉ tính thiết bị MUA MỚI đợt 2 (THEM_MOI):
 *   FULL  — nguyên căn: ĐH, quạt, giường, tủ lạnh, máy giặt, nóng lạnh, bàn ăn, tủ quần áo, bếp từ
 *           theo phòng: mỗi phòng giường + quạt + ĐH + nóng lạnh (+ tủ lạnh/máy giặt khu vực chung)
 *   BASIC — nguyên căn / theo phòng: giường + quạt
 *   NONE  — không dòng TB mua mới (đồ chủ bàn giao nếu có thì không tính)
 * TB bàn giao đợt 1 chỉ ghi nhận đồ chủ gốc, không dùng làm NT khai thác.
 *
 * Chạy: node scripts/generate-import-excel-matrix.mjs
 */
import XLSX from 'xlsx';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.join(__dirname, '..', 'docs');

const OUT_DOT1 = path.join(DOCS, 'SLMS2026_import_matrix_dot1.xlsx');
const OUT_DOT2 = path.join(DOCS, 'SLMS2026_import_matrix_dot2.xlsx');
const OUT_SUPPLEMENT = path.join(DOCS, 'SLMS2026_import_matrix_cai_tao_bo_sung.xlsx');

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
  ['BEDROOM', 'Phòng ngủ'],
  ['KITCHEN', 'Nhà bếp'],
  ['BATHROOM', 'Phòng tắm / WC'],
  ['BALCONY', 'Ban công'],
  ['GARAGE', 'Gara / sân'],
  ['OTHER', 'Khu vực khác'],
];

/** Chỉ dùng quận đã seed trong ZoneDataSeeder — import đợt 1 sẽ fail nếu lệch. */
const DISTRICTS = [
  { district: 'Quận 1', streets: ['Nguyễn Huệ', 'Lê Lợi', 'Đồng Khởi', 'Pasteur', 'Nguyễn Du'] },
  { district: 'Quận 3', streets: ['Võ Văn Tần', 'Nam Kỳ Khởi Nghĩa', 'Lý Chính Thắng', 'Cách Mạng Tháng 8'] },
  { district: 'Bình Thạnh', streets: ['Phan Văn Trị', 'Xô Viết Nghệ Tĩnh', 'Điện Biên Phủ', 'Nơ Trang Long'] },
  { district: 'Gò Vấp', streets: ['Quang Trung', 'Phạm Văn Chiêu', 'Nguyễn Oanh', 'Lê Đức Thọ'] },
  { district: 'Phú Nhuận', streets: ['Hoàng Văn Thụ', 'Phan Xích Long', 'Nguyễn Văn Trỗi', 'Trường Sa'] },
];

const OWNERS = [
  'Nguyễn Văn An', 'Trần Thị Bình', 'Lê Văn Cường', 'Phạm Thị Dung', 'Hoàng Văn Em',
  'Võ Thị Phương', 'Đặng Văn Giang', 'Bùi Thị Hoa', 'Ngô Văn Ích', 'Dương Thị Kim',
  'Lý Văn Long', 'Mai Thị My', 'Phan Văn Nam', 'Trịnh Thị Oanh', 'Vũ Văn Phát',
  'Cao Thị Quyên', 'Lương Văn Rạng', 'Tô Thị Sen', 'Huỳnh Văn Tâm', 'Đỗ Thị Uyên',
  'Hồ Văn Vinh', 'Đinh Thị Xuân', 'Lại Văn Yên', 'Tạ Thị Ánh', 'Châu Văn Bảo',
  'Kiều Thị Chi', 'Ông Văn Đạt', 'Thái Thị Hạnh', 'Từ Văn Khải', 'Lâm Thị Lan',
  'Quách Văn Minh', 'Hà Thị Nga', 'Trương Văn Phong', 'Đoàn Thị Quế', 'La Văn Sơn',
  'Ung Thị Trang', 'Vi Văn Út', 'Mạc Thị Vân', 'Chu Văn Xuân', 'Triệu Thị Yến',
  'Ông Thị Hòa', 'Bạch Văn Khoa', 'Nhan Thị Linh', 'Tống Văn Mạnh', 'Lục Thị Nhung',
  'Nghiêm Văn Phúc', 'Âu Thị Quyên', 'Giang Văn Sỹ', 'Thạch Thị Tuyết', 'Lữ Văn Vũ',
];

const PRICES = {
  'Điều hòa': [12_500_000, 24],
  'Tủ lạnh': [8_200_000, 24],
  'Máy giặt': [7_800_000, 24],
  'Bàn ăn': [4_600_000, 12],
  'Giường': [4_400_000, 12],
  'Tủ quần áo': [3_600_000, 12],
  'Bếp từ': [5_400_000, 24],
  'Nóng lạnh': [2_700_000, 12],
  'Quạt': [780_000, 12],
};

const FURNISH_LABEL = {
  FULL: 'full NT',
  BASIC: 'NT cơ bản',
  NONE: 'không NT',
};

function pad2(n) {
  return String(n).padStart(2, '0');
}

function addMonths(iso, months) {
  const [y, m, d] = iso.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1 + months, d));
  return `${dt.getUTCFullYear()}-${pad2(dt.getUTCMonth() + 1)}-${pad2(dt.getUTCDate())}`;
}

function endOfMonth(iso) {
  const [y, m] = iso.split('-').map(Number);
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return `${iso.slice(0, 7)}-${pad2(last)}`;
}

function inferDimensions(area) {
  const length = Math.round(Math.sqrt(area * 1.4) * 10) / 10;
  const width = Math.round((area / length) * 10) / 10;
  return { length, width };
}

function buildRoomNumbers(totalRooms) {
  const rooms = [];
  let floor = 1;
  let indexOnFloor = 1;
  while (rooms.length < totalRooms) {
    rooms.push(`${floor}${String(indexOnFloor).padStart(2, '0')}`);
    indexOnFloor++;
    if (indexOnFloor > 20) {
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

function warrantyEnd(start, months) {
  const endYear = parseInt(start.slice(0, 4), 10) + Math.floor(months / 12);
  return `${endYear}-${start.slice(5, 7)}-28`;
}

function samplePenaltyFee(price) {
  return Math.round(price * 0.3);
}

function varyPrice(base, id) {
  return Math.round(base * (1 + ((id % 7) - 3) * 0.02) / 1000) * 1000;
}

function loc(id) {
  const d = DISTRICTS[(id - 1) % DISTRICTS.length];
  const street = d.streets[(id - 1) % d.streets.length];
  return {
    district: d.district,
    province: 'TP. Hồ Chí Minh',
    address: `${10 + id} ${street}`,
  };
}

function sampleHandover(id) {
  // ~2/3 căn có đồ chủ gốc — chỉ ghi nhận, không tính NT khai thác
  if (id % 3 === 0) return [];
  const items = [
    ['Quạt', 'Quạt trần cũ của chủ', 'Phòng khách', 'GOOD', 1, 'Đồ chủ gốc — không tính NT khai thác'],
  ];
  if (id % 2 === 0) {
    items.push(['Tủ lạnh', 'Tủ 150L cũ', 'Bếp', 'DAMAGED', 1, 'Đồ chủ gốc hỏng — đợt 2 mua mới nếu có NT']);
  }
  if (id % 5 === 1) {
    items.push(['Giường', 'Giường sắt cũ', 'Phòng ngủ', 'GOOD', 1, 'Đồ chủ gốc — không tính NT khai thác']);
  }
  return items;
}

function renovationsFor(furnish, exploitation, rooms) {
  const paint = exploitation === 'THEO_PHONG'
    ? ['PAINTING', 'Sơn sửa', 6_000_000 + rooms * 1_500_000, `Sơn ${rooms} phòng khai thác`]
    : ['PAINTING', 'Sơn sửa', 8_000_000, 'Sơn lại toàn bộ'];
  if (furnish === 'NONE') {
    return [paint];
  }
  const furniture = ['FURNITURE', 'Nội thất', furnish === 'FULL' ? 6_000_000 : 3_000_000, 'Lắp đặt NT mua mới'];
  if (furnish === 'FULL') {
    return [
      paint,
      ['PLUMBING', 'Điện nước', 7_500_000, 'Sửa điện nước trước lắp TB'],
      furniture,
    ];
  }
  return [paint, furniture];
}

function makeScenario({ id, exploitation, furnish, exploitRooms }) {
  const { district, province, address } = loc(id);
  const isRoom = exploitation === 'THEO_PHONG';
  const rooms = isRoom ? exploitRooms : null;
  const physicalRooms = isRoom ? rooms + 1 : furnish === 'FULL' ? 3 + (id % 2) : 2 + (id % 2);
  const floors = isRoom ? (rooms >= 5 ? 3 : 2) : physicalRooms >= 4 ? 2 : 1;
  const area = isRoom ? 24 * rooms + 18 : 55 + physicalRooms * 12 + (id % 5) * 3;
  const start = addMonths('2026-03-01', (id - 1) % 10);
  const end = endOfMonth(addMonths(start, 24));
  const codeTag = isRoom ? 'RM' : 'WH';
  const code = `HD-MTX-${pad2(id)}-RENO-${codeTag}-${furnish}`;
  const name = `MTX#${pad2(id)} ${isRoom ? 'THEO_PHONG' : 'NGUYEN_CAN'} ${FURNISH_LABEL[furnish]}`;
  const rent = (isRoom ? 180_000_000 : 160_000_000)
    + id * 2_500_000
    + (furnish === 'FULL' ? 25_000_000 : furnish === 'BASIC' ? 10_000_000 : 0);
  const roomNote = isRoom ? `THEO_PHONG (${rooms} phòng)` : 'NGUYEN_CAN';
  const desc = [
    `${name} — RENO.`,
    `NT khai thác = TB mua mới đợt 2 (${furnish}).`,
    'Đồ chủ bàn giao (nếu có) chỉ ghi nhận, không tính NT.',
  ].join(' ');

  const scenario = {
    matrixId: id,
    code,
    name,
    address,
    district,
    province,
    area,
    floors,
    physicalRooms,
    owner: OWNERS[id - 1],
    rent,
    start,
    end,
    desc,
    phase2: true,
    exploitation,
    exploitRooms: rooms,
    furnish,
    handover: sampleHandover(id),
    renovations: renovationsFor(furnish, exploitation, rooms ?? physicalRooms),
    matrixNote: `#${id} RENO | ${roomNote} | NT mua mới: ${furnish} | TB bàn giao: không tính NT`,
  };

  return scenario;
}

/**
 * 50 căn:
 *  #1–#14  NGUYEN_CAN FULL
 *  #15–#27 NGUYEN_CAN BASIC
 *  #28–#40 NGUYEN_CAN NONE
 *  #41–#44 THEO_PHONG FULL  (3/4/5/3 phòng)
 *  #45–#48 THEO_PHONG BASIC (3/4/5/6 phòng)
 *  #49–#50 THEO_PHONG NONE  (3/4 phòng)
 */
function buildScenarios() {
  const scenarios = [];
  let id = 1;
  for (let i = 0; i < 14; i++) scenarios.push(makeScenario({ id: id++, exploitation: 'NGUYEN_CAN', furnish: 'FULL' }));
  for (let i = 0; i < 13; i++) scenarios.push(makeScenario({ id: id++, exploitation: 'NGUYEN_CAN', furnish: 'BASIC' }));
  for (let i = 0; i < 13; i++) scenarios.push(makeScenario({ id: id++, exploitation: 'NGUYEN_CAN', furnish: 'NONE' }));

  const roomPlan = [
    { furnish: 'FULL', rooms: 3 },
    { furnish: 'FULL', rooms: 4 },
    { furnish: 'FULL', rooms: 5 },
    { furnish: 'FULL', rooms: 3 },
    { furnish: 'BASIC', rooms: 3 },
    { furnish: 'BASIC', rooms: 4 },
    { furnish: 'BASIC', rooms: 5 },
    { furnish: 'BASIC', rooms: 6 },
    { furnish: 'NONE', rooms: 3 },
    { furnish: 'NONE', rooms: 4 },
  ];
  for (const p of roomPlan) {
    scenarios.push(makeScenario({
      id: id++,
      exploitation: 'THEO_PHONG',
      furnish: p.furnish,
      exploitRooms: p.rooms,
    }));
  }

  attachSupplements(scenarios);
  return scenarios;
}

function attachSupplements(scenarios) {
  const byId = (n) => scenarios.find((s) => s.matrixId === n);
  // SUPP#1: chỉ cải tạo — nhà FULL nguyên căn đã ACTIVE
  byId(1).supplement = {
    renovations: [['OTHER', 'Khác', 3_000_000, 'SUPP#1 chỉ cải tạo — sau khi nhà ACTIVE']],
  };
  // SUPP#2: chỉ thêm TB THEM_MOI
  byId(15).supplement = {
    purchased: [
      ['', 'LIVING_ROOM', 'Quạt', 'NEW', 'THEM_MOI', 1, 900_000, 12, '2027-01-01', '2027-12-31', 270_000, 'SUPP#2 chỉ TB THEM_MOI'],
    ],
  };
  // SUPP#3: THAY_THE ĐH đã mua đợt 2 (nhà #2 FULL có ĐH LIVING_ROOM)
  byId(2).supplement = {
    purchased: [
      ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THAY_THE', 1, 15_500_000, 36, '2027-02-01', '2030-01-31', 4_650_000, 'SUPP#3 THAY_THE ĐH phòng khách'],
    ],
  };
  // SUPP#4: cải tạo + THAY_THE ĐH phòng 102 nhà THEO_PHONG FULL #41 (3 phòng: 101–103)
  byId(41).supplement = {
    renovations: [['PAINTING', 'Sơn sửa', 4_000_000, 'SUPP#4 cải tạo + TB']],
    purchased: [
      ['102', '', 'Điều hòa', 'NEW', 'THAY_THE', 1, 9_800_000, 24, '2027-03-01', '2029-02-28', 2_940_000, 'SUPP#4 THAY_THE phòng 102'],
    ],
  };
}

const scenarios = buildScenarios();

function buildRoomListRows(scenario) {
  const n = scenario.exploitRooms;
  const perRoomArea = Math.round((scenario.area / n) * 10) / 10;
  return buildRoomNumbers(n).map((roomNumber) => {
    const { length, width } = inferDimensions(perRoomArea);
    return [
      scenario.code,
      roomNumber,
      inferFloor(roomNumber, scenario.floors),
      perRoomArea,
      length,
      width,
      `Phòng ${roomNumber}`,
    ];
  });
}

function purchasedRow(contractCode, roomNumber, houseArea, catalog, price, months, warrantyStart, note) {
  return [
    contractCode,
    roomNumber,
    houseArea,
    catalog,
    'NEW',
    'THEM_MOI',
    1,
    price,
    months,
    warrantyStart,
    warrantyEnd(warrantyStart, months),
    samplePenaltyFee(price),
    note,
  ];
}

function itemRow(contractCode, id, roomNumber, houseArea, catalog, warrantyStart, note) {
  const [base, months] = PRICES[catalog];
  return purchasedRow(
    contractCode,
    roomNumber,
    houseArea,
    catalog,
    varyPrice(base, id),
    months,
    warrantyStart,
    note,
  );
}

function buildBasicWholeHouse(s, warrantyStart) {
  const beds = Math.max(1, s.physicalRooms - 1);
  const rows = [];
  for (let i = 0; i < beds; i++) {
    rows.push(itemRow(s.code, s.matrixId, '', 'BEDROOM', 'Giường', warrantyStart, `Giường mua mới #${i + 1}`));
  }
  rows.push(itemRow(s.code, s.matrixId, '', 'LIVING_ROOM', 'Quạt', warrantyStart, 'Quạt phòng khách mua mới'));
  if (beds >= 2) {
    rows.push(itemRow(s.code, s.matrixId, '', 'BEDROOM', 'Quạt', warrantyStart, 'Quạt phòng ngủ mua mới'));
  }
  return rows;
}

function buildFullWholeHouse(s, warrantyStart) {
  const beds = Math.max(2, s.physicalRooms - 1);
  const rows = [
    itemRow(s.code, s.matrixId, '', 'LIVING_ROOM', 'Điều hòa', warrantyStart, 'ĐH phòng khách mua mới'),
    itemRow(s.code, s.matrixId, '', 'LIVING_ROOM', 'Quạt', warrantyStart, 'Quạt trần phòng khách'),
    itemRow(s.code, s.matrixId, '', 'LIVING_ROOM', 'Bàn ăn', warrantyStart, 'Bàn ăn mua mới'),
    itemRow(s.code, s.matrixId, '', 'KITCHEN', 'Tủ lạnh', warrantyStart, 'Tủ lạnh mua mới'),
    itemRow(s.code, s.matrixId, '', 'KITCHEN', 'Bếp từ', warrantyStart, 'Bếp từ mua mới'),
    itemRow(s.code, s.matrixId, '', 'BALCONY', 'Máy giặt', warrantyStart, 'Máy giặt mua mới'),
    itemRow(s.code, s.matrixId, '', 'BATHROOM', 'Nóng lạnh', warrantyStart, 'Nóng lạnh mua mới'),
    itemRow(s.code, s.matrixId, '', 'BEDROOM', 'Tủ quần áo', warrantyStart, 'Tủ quần áo mua mới'),
  ];
  for (let i = 0; i < beds; i++) {
    rows.push(itemRow(s.code, s.matrixId, '', 'BEDROOM', 'Giường', warrantyStart, `Giường phòng ngủ #${i + 1}`));
    rows.push(itemRow(s.code, s.matrixId, '', 'BEDROOM', 'Quạt', warrantyStart, `Quạt phòng ngủ #${i + 1}`));
  }
  return rows;
}

function buildBedFanRowsForRooms(s, warrantyStart) {
  const rows = [];
  for (const roomNumber of buildRoomNumbers(s.exploitRooms)) {
    rows.push(itemRow(s.code, s.matrixId, roomNumber, '', 'Giường', warrantyStart, `1 giường phòng ${roomNumber}`));
    rows.push(itemRow(s.code, s.matrixId, roomNumber, '', 'Quạt', warrantyStart, `1 quạt phòng ${roomNumber}`));
  }
  return rows;
}

function buildFullFurnishRowsForRooms(s, warrantyStart) {
  const perRoom = ['Giường', 'Quạt', 'Điều hòa', 'Nóng lạnh'];
  const rows = [];
  for (const roomNumber of buildRoomNumbers(s.exploitRooms)) {
    for (const catalog of perRoom) {
      rows.push(itemRow(s.code, s.matrixId, roomNumber, '', catalog, warrantyStart, `${catalog} phòng ${roomNumber}`));
    }
  }
  rows.push(itemRow(s.code, s.matrixId, '', 'KITCHEN', 'Tủ lạnh', warrantyStart, 'Tủ lạnh khu vực chung mua mới'));
  rows.push(itemRow(s.code, s.matrixId, '', 'BALCONY', 'Máy giặt', warrantyStart, 'Máy giặt khu vực chung mua mới'));
  return rows;
}

function buildPurchasedRows(s) {
  if (s.furnish === 'NONE') return [];
  const warrantyStart = `${s.start.slice(0, 8)}15`;
  if (s.exploitation === 'THEO_PHONG') {
    return s.furnish === 'FULL'
      ? buildFullFurnishRowsForRooms(s, warrantyStart)
      : buildBedFanRowsForRooms(s, warrantyStart);
  }
  return s.furnish === 'FULL'
    ? buildFullWholeHouse(s, warrantyStart)
    : buildBasicWholeHouse(s, warrantyStart);
}

function buildOnboardingMatrix() {
  return [
    [
      'STT',
      'Mã hợp đồng',
      'RENO/NORENO',
      'Hình thức đ2',
      'NT khai thác (mua mới)',
      'TB bàn giao đ1',
      'Cải tạo đ2',
      'TB mua đ2',
      'File đợt 2',
      'Diễn giải',
    ],
    ...scenarios.map((s) => [
      s.matrixId,
      s.code,
      'RENO',
      s.exploitation === 'THEO_PHONG' ? `THEO_PHONG (${s.exploitRooms})` : 'NGUYEN_CAN',
      s.furnish,
      (s.handover?.length ?? 0) > 0 ? 'Có (không tính NT)' : 'Không',
      'Có (≥1 dòng)',
      s.furnish === 'NONE' ? 'Không' : 'Có (khớp NT)',
      'Có',
      s.matrixNote,
    ]),
    [''],
    ['Tổng: 50 HĐ — 40 NGUYEN_CAN (#1–#40) + 10 THEO_PHONG (#41–#50, mỗi căn ≥3 phòng).'],
    ['#1–#14 FULL | #15–#27 BASIC | #28–#40 NONE — nguyên căn.'],
    ['#41–#44 FULL | #45–#48 BASIC | #49–#50 NONE — theo phòng.'],
    ['NT khai thác CHỈ tính TB mua mới đợt 2. Đồ chủ bàn giao đợt 1 không tính.'],
    ['FULL nguyên căn: ĐH + quạt + giường + tủ lạnh + máy giặt + nóng lạnh + bàn ăn + tủ quần áo + bếp từ.'],
    ['FULL theo phòng: mỗi phòng giường + quạt + ĐH + nóng lạnh; thêm tủ lạnh + máy giặt khu vực chung.'],
    ['BASIC: giường + quạt (nguyên căn theo khu vực / theo phòng từng phòng).'],
    ['NONE: không dòng TB mua mới. Tất cả 50 căn đều RENO — import đợt 2 sau đợt 1.'],
  ];
}

function buildSupplementMatrix() {
  return [
    ['STT', 'Mã HĐ', 'Cải tạo bổ sung', 'TB mua bổ sung', 'Hành động TB', 'Ghi chú'],
    ['1', 'HD-MTX-01-RENO-WH-FULL', 'Có', 'Không', '—', 'SUPP chỉ cải tạo — sau khi nhà ACTIVE'],
    ['2', 'HD-MTX-15-RENO-WH-BASIC', 'Không', 'Có', 'THEM_MOI', 'SUPP chỉ thêm quạt mới'],
    ['3', 'HD-MTX-02-RENO-WH-FULL', 'Không', 'Có', 'THAY_THE', 'SUPP thay ĐH đã mua đợt 2'],
    ['4', 'HD-MTX-41-RENO-RM-FULL', 'Có', 'Có', 'THAY_THE', 'SUPP cải tạo + thay ĐH phòng 102'],
    [''],
    ['Tiên quyết: nhà ACTIVE → POST /properties/{id}/start-renovation → import file bổ sung.'],
  ];
}

const leaseHeader = [
  'Mã hợp đồng',
  'Tên tòa nhà',
  'Địa chỉ chi tiết',
  'Quận/Huyện',
  'Tỉnh/Thành phố',
  'Diện tích (m²)',
  'Chiều dài (m)',
  'Chiều rộng (m)',
  'Tổng số tầng',
  'Tổng số phòng',
  'Tên chủ nhà',
  'Tổng tiền thuê',
  'Ngày bắt đầu',
  'Ngày kết thúc',
  'Mô tả chi tiết',
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

const configHeader = ['Mã hợp đồng thuê', 'Hình thức khai thác', 'Số phòng khai thác'];
const roomListHeader = [
  'Mã hợp đồng thuê',
  'Số phòng',
  'Tầng',
  'Diện tích phòng (m²)',
  'Chiều dài (m)',
  'Chiều rộng (m)',
  'Ghi chú',
];
const renovationHeader = [
  'Mã hợp đồng thuê',
  'Mã danh mục cải tạo',
  'Tên danh mục (Gợi ý)',
  'Chi phí cải tạo (VNĐ)',
  'Ghi chú chi tiết',
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
  'Giá phạt hết bảo hành (VNĐ)',
  'Ghi chú lắp đặt',
];

const leaseRows = scenarios.map((s) => {
  const { length, width } = inferDimensions(s.area);
  return [
    s.code,
    s.name,
    s.address,
    s.district,
    s.province,
    s.area,
    length,
    width,
    s.floors,
    s.physicalRooms,
    s.owner,
    s.rent,
    s.start,
    s.end,
    s.desc,
  ];
});

const handoverRows = scenarios.flatMap((s) => (s.handover ?? []).map((h) => [s.code, ...h]));

const phase2Scenarios = scenarios.filter((s) => s.phase2);

const configRows = phase2Scenarios.map((s) => [
  s.code,
  s.exploitation,
  s.exploitation === 'THEO_PHONG' ? s.exploitRooms : '',
]);

const roomListRows = phase2Scenarios
  .filter((s) => s.exploitation === 'THEO_PHONG')
  .flatMap((s) => buildRoomListRows(s));

const renovationRows = phase2Scenarios.flatMap((s) =>
  (s.renovations ?? []).map((r) => [s.code, ...r]),
);

const purchasedRows = phase2Scenarios.flatMap((s) => buildPurchasedRows(s));

const supplementRenovationRows = [];
const supplementPurchasedRows = [];
for (const s of scenarios) {
  if (!s.supplement) continue;
  for (const r of s.supplement.renovations ?? []) {
    supplementRenovationRows.push([s.code, ...r]);
  }
  for (const p of s.supplement.purchased ?? []) {
    supplementPurchasedRows.push([s.code, ...p]);
  }
}

const onboardingMatrix = buildOnboardingMatrix();
const supplementMatrix = buildSupplementMatrix();

const huongDanDot1 = [
  ['Hướng dẫn — 50 căn onboarding (đợt 1)'],
  [''],
  ['40 NGUYEN_CAN (#1–#40) + 10 THEO_PHONG (#41–#50, mỗi căn ≥3 phòng).'],
  ['NT khai thác CHỈ tính TB mua mới ở file đợt 2 — đồ chủ bàn giao không tính.'],
  ['Sheet "0. Ma_Tran_Onboarding": tra cứu STT / mức NT (FULL, BASIC, NONE).'],
  ['API: POST /api/v1/import/lease-excel?dryRun='],
  [''],
  ['Tất cả 50 căn là RENO: sau đợt 1 ở UNDER_RENOVATION — bắt buộc import đợt 2.'],
  ['Quận/Huyện chỉ gồm: Quận 1, Quận 3, Bình Thạnh, Gò Vấp, Phú Nhuận (Zone seeder).'],
];

const huongDanDot2 = [
  ['Hướng dẫn — 50 căn RENO (đợt 2)'],
  [''],
  ['Chỉ import sau khi đã import đợt 1. Mã HĐ phải khớp 1-1 với đợt 1.'],
  ['API: POST /api/v1/import/renovation-excel?dryRun='],
  ['Sheet "4. Thiet_Bi_Mua_Moi" quyết định mức NT: FULL / BASIC / NONE.'],
  [''],
  ['FULL NGUYEN_CAN (#1–#14): đủ catalog NT khu vực chung (kể cả BEDROOM).'],
  ['BASIC NGUYEN_CAN (#15–#27): chỉ Giường + Quạt.'],
  ['NONE NGUYEN_CAN (#28–#40): không dòng TB mua.'],
  ['FULL THEO_PHONG (#41–#44): mỗi phòng Giường+Quạt+ĐH+Nóng lạnh + tủ lạnh/máy giặt chung.'],
  ['BASIC THEO_PHONG (#45–#48): mỗi phòng đúng 1 Giường + 1 Quạt.'],
  ['NONE THEO_PHONG (#49–#50): có danh sách phòng, không TB mua.'],
];

const huongDanSupplement = [
  ['Hướng dẫn — Ma trận 4 TH cải tạo bổ sung'],
  [''],
  ['Dùng SAU KHI nhà #1, #2, #15, #41 đã ACTIVE (host xác nhận + gán OM).'],
  ['1. POST /properties/{id}/start-renovation'],
  ['2. POST /api/v1/import/renovation-supplement-excel?dryRun='],
  ['Sheet "0. Ma_Tran_Bo_Sung": 4 profile SUPP#1–#4.'],
];

function sheetFromAoA(data) {
  const ws = XLSX.utils.aoa_to_sheet(data);
  const headerRow = data[0] ?? [];
  ws['!cols'] = headerRow.map((h) => ({
    wch: Math.max(String(h).length + 4, 16),
  }));
  return ws;
}

function buildWorkbook(sheets) {
  const wb = XLSX.utils.book_new();
  for (const { name, data } of sheets) {
    XLSX.utils.book_append_sheet(wb, sheetFromAoA(data), name);
  }
  return wb;
}

function assertDataset() {
  const wh = scenarios.filter((s) => s.exploitation === 'NGUYEN_CAN');
  const rm = scenarios.filter((s) => s.exploitation === 'THEO_PHONG');
  if (scenarios.length !== 50) throw new Error(`Expected 50 houses, got ${scenarios.length}`);
  if (wh.length !== 40) throw new Error(`Expected 40 NGUYEN_CAN, got ${wh.length}`);
  if (rm.length !== 10) throw new Error(`Expected 10 THEO_PHONG, got ${rm.length}`);
  for (const s of rm) {
    if (!s.exploitRooms || s.exploitRooms < 3) {
      throw new Error(`${s.code} THEO_PHONG must have ≥3 rooms`);
    }
  }
  const purchasedByCode = new Map();
  for (const row of purchasedRows) {
    const code = row[0];
    if (!purchasedByCode.has(code)) purchasedByCode.set(code, []);
    purchasedByCode.get(code).push(row);
  }
  const codes1 = new Set(leaseRows.map((r) => r[0]));
  const codes2 = new Set(configRows.map((r) => r[0]));
  if (codes1.size !== codes2.size) {
    throw new Error('Đợt 1 / đợt 2 lệch số mã HĐ');
  }
  for (const code of codes1) {
    if (!codes2.has(code)) throw new Error(`Thiếu cấu hình đợt 2 cho ${code}`);
  }
  for (const s of scenarios) {
    const rows = purchasedByCode.get(s.code) ?? [];
    const catalogs = rows.map((r) => r[3]);
    if (s.furnish === 'NONE' && rows.length > 0) {
      throw new Error(`${s.code} NONE nhưng có TB mua`);
    }
    if (s.furnish !== 'NONE' && rows.length === 0) {
      throw new Error(`${s.code} ${s.furnish} nhưng không có TB mua`);
    }
    if (s.furnish === 'BASIC') {
      const unexpected = catalogs.filter((c) => c !== 'Giường' && c !== 'Quạt');
      if (unexpected.length) throw new Error(`${s.code} BASIC có TB không phải giường/quạt: ${unexpected}`);
    }
    if (s.furnish === 'FULL') {
      for (const need of ['Giường', 'Quạt', 'Điều hòa', 'Nóng lạnh']) {
        if (!catalogs.includes(need)) throw new Error(`${s.code} FULL thiếu ${need}`);
      }
      if (s.exploitation === 'NGUYEN_CAN') {
        for (const need of ['Tủ lạnh', 'Máy giặt', 'Bàn ăn', 'Tủ quần áo', 'Bếp từ']) {
          if (!catalogs.includes(need)) throw new Error(`${s.code} FULL nguyên căn thiếu ${need}`);
        }
      }
      if (s.exploitation === 'THEO_PHONG') {
        const perRoom = buildRoomNumbers(s.exploitRooms);
        for (const room of perRoom) {
          const roomCats = rows.filter((r) => r[1] === room).map((r) => r[3]);
          for (const need of ['Giường', 'Quạt', 'Điều hòa', 'Nóng lạnh']) {
            if (!roomCats.includes(need)) {
              throw new Error(`${s.code} phòng ${room} FULL thiếu ${need}`);
            }
          }
        }
      }
    }
  }
}

assertDataset();

const wb1 = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanDot1 },
  { name: '0. Ma_Tran_Onboarding', data: onboardingMatrix },
  { name: '0. Danh_Muc_Tham_Khao', data: [...equipmentCatalog, [''], ...houseAreas] },
  { name: '1. Hop_Dong_Thue', data: [leaseHeader, ...leaseRows] },
  { name: '2. Thiet_Bi_Ban_Giao', data: [handoverHeader, ...handoverRows] },
]);

const wb2 = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanDot2 },
  { name: '0. Ma_Tran_Onboarding', data: onboardingMatrix },
  {
    name: '0. Danh_Muc_Tham_Khao',
    data: [...renovationCategories, [''], ...equipmentCatalog, [''], ...houseAreas],
  },
  { name: '1. Cau_Hinh_Khai_Thac', data: [configHeader, ...configRows] },
  { name: '2. Danh_Sach_Phong', data: [roomListHeader, ...roomListRows] },
  { name: '3. Hop_Dong_Cai_Tao', data: [renovationHeader, ...renovationRows] },
  { name: '4. Thiet_Bi_Mua_Moi', data: [purchasedHeader, ...purchasedRows] },
]);

const wbSupplement = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanSupplement },
  { name: '0. Ma_Tran_Bo_Sung', data: supplementMatrix },
  {
    name: '0. Danh_Muc_Tham_Khao',
    data: [...renovationCategories, [''], ...equipmentCatalog, [''], ...houseAreas],
  },
  { name: '1. Hop_Dong_Cai_Tao', data: [renovationHeader, ...supplementRenovationRows] },
  { name: '2. Thiet_Bi_Mua_Moi', data: [purchasedHeader, ...supplementPurchasedRows] },
]);

XLSX.writeFile(wb1, OUT_DOT1);
XLSX.writeFile(wb2, OUT_DOT2);
XLSX.writeFile(wbSupplement, OUT_SUPPLEMENT);

const countFurnish = (ex, f) => scenarios.filter((s) => s.exploitation === ex && s.furnish === f).length;
const purchasedByFurnish = { FULL: 0, BASIC: 0, NONE: 0 };
for (const s of scenarios) {
  const n = purchasedRows.filter((r) => r[0] === s.code).length;
  purchasedByFurnish[s.furnish] += n;
}

console.log('Đã tạo bộ 50 căn (NT = TB mua mới đợt 2):');
console.log('  Đợt 1:', OUT_DOT1);
console.log(`    - ${leaseRows.length} HĐ`);
console.log(`    - ${handoverRows.length} dòng TB bàn giao (đồ chủ gốc, không tính NT)`);
console.log('  Đợt 2:', OUT_DOT2);
console.log(`    - ${configRows.length} cấu hình RENO`);
console.log(`    - ${roomListRows.length} phòng THEO_PHONG`);
console.log(`    - ${renovationRows.length} dòng cải tạo`);
console.log(`    - ${purchasedRows.length} dòng TB mua mới`);
console.log('  Phân bổ:');
console.log(`    NGUYEN_CAN FULL/BASIC/NONE = ${countFurnish('NGUYEN_CAN', 'FULL')}/${countFurnish('NGUYEN_CAN', 'BASIC')}/${countFurnish('NGUYEN_CAN', 'NONE')}`);
console.log(`    THEO_PHONG FULL/BASIC/NONE = ${countFurnish('THEO_PHONG', 'FULL')}/${countFurnish('THEO_PHONG', 'BASIC')}/${countFurnish('THEO_PHONG', 'NONE')}`);
console.log(`    Dòng TB mua theo mức NT: FULL=${purchasedByFurnish.FULL} BASIC=${purchasedByFurnish.BASIC} NONE=${purchasedByFurnish.NONE}`);
console.log('  Bổ sung:', OUT_SUPPLEMENT);
console.log(`    - ${supplementRenovationRows.length} cải tạo, ${supplementPurchasedRows.length} TB (4 TH)`);
