/**
 * Sinh bộ Excel ma trận đầy đủ 10 TH onboarding + 4 TH cải tạo bổ sung.
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
  ['Giường', 'Giường ngủ các loại'],
  ['Nóng lạnh', 'Máy nước nóng'],
  ['Quạt', 'Quạt điện / quạt trần'],
];

const houseAreas = [
  ['Mã khu vực', 'Mô tả'],
  ['LIVING_ROOM', 'Phòng khách'],
  ['KITCHEN', 'Nhà bếp'],
  ['BATHROOM', 'Phòng tắm / WC'],
  ['BALCONY', 'Ban công'],
];

/**
 * 10 trường hợp onboarding hợp lệ:
 * 2 NORENO + 8 RENO (NGUYEN_CAN/THEO_PHONG × TB mua × TB bàn giao)
 */
const scenarios = [
  {
    matrixId: 1,
    code: 'HD-MTX-01-NORENO-NOFURN',
    name: 'MTX#01 NORENO trống',
    address: '1 Ma Trận Quận 1',
    district: 'Quận 1',
    province: 'TP. Hồ Chí Minh',
    area: 50,
    floors: 1,
    physicalRooms: 1,
    owner: 'Nguyễn Văn A',
    rent: 150_000_000,
    start: '2026-03-01',
    end: '2027-02-28',
    desc: 'Ma trận #1 — NORENO, không TB bàn giao, gửi Host sau đợt 1.',
    phase2: false,
    handover: [],
    matrixNote: '#1 NORENO | TB bàn giao: Không | Đợt 2: —',
  },
  {
    matrixId: 2,
    code: 'HD-MTX-02-NORENO-FURN',
    name: 'MTX#02 NORENO có TB',
    address: '2 Ma Trận Quận 1',
    district: 'Quận 1',
    province: 'TP. Hồ Chí Minh',
    area: 65,
    floors: 1,
    physicalRooms: 2,
    owner: 'Trần Thị B',
    rent: 180_000_000,
    start: '2026-03-01',
    end: '2027-02-28',
    desc: 'Ma trận #2 — NORENO, có TB bàn giao, gửi Host sau đợt 1.',
    phase2: false,
    handover: [
      ['Điều hòa', 'Máy 1.5HP', 'Phòng khách', 'GOOD', 1, ''],
      ['Giường', 'Giường gỗ', 'Phòng ngủ', 'GOOD', 1, ''],
    ],
    matrixNote: '#2 NORENO | TB bàn giao: Có | Đợt 2: —',
  },
  {
    matrixId: 3,
    code: 'HD-MTX-03-RENO-WH-NOFURN',
    name: 'MTX#03 NGUYEN_CAN cải tạo',
    address: '3 Ma Trận Quận 3',
    district: 'Quận 3',
    province: 'TP. Hồ Chí Minh',
    area: 80,
    floors: 2,
    physicalRooms: 3,
    owner: 'Lê Văn C',
    rent: 220_000_000,
    start: '2026-04-01',
    end: '2028-03-31',
    desc: 'Ma trận #3 — RENO NGUYEN_CAN, không TB bàn giao, có cải tạo, không mua TB.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [],
    renovations: [['PAINTING', 'Sơn sửa', 8_000_000, 'Sơn lại toàn bộ']],
    purchasedWholeHouse: [],
    matrixNote: '#3 RENO | NGUYEN_CAN | TB bàn giao: Không | Cải tạo: Có | TB mua: Không',
  },
  {
    matrixId: 4,
    code: 'HD-MTX-04-RENO-WH-EQUIP',
    name: 'MTX#04 NGUYEN_CAN mua TB',
    address: '4 Ma Trận Quận 3',
    district: 'Quận 3',
    province: 'TP. Hồ Chí Minh',
    area: 70,
    floors: 1,
    physicalRooms: 2,
    owner: 'Phạm Thị D',
    rent: 200_000_000,
    start: '2026-04-01',
    end: '2028-03-31',
    desc: 'Ma trận #4 — RENO NGUYEN_CAN, không TB bàn giao, cải tạo + mua TB.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [],
    renovations: [['EQUIPMENT', 'Thiết bị', 2_000_000, 'Hoàn thiện lắp đặt TB']],
    purchasedWholeHouse: [
      ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THEM_MOI', 1, 11_000_000, 24, '2026-05-01', '2028-04-30', ''],
      ['', 'KITCHEN', 'Tủ lạnh', 'NEW', 'THEM_MOI', 1, 7_500_000, 24, '2026-05-01', '2028-04-30', ''],
    ],
    supplement: {
      purchased: [
        ['', 'LIVING_ROOM', 'Quạt', 'NEW', 'THEM_MOI', 1, 900_000, 12, '2027-01-01', '2027-12-31', 'SUPP#2 chỉ TB THEM_MOI'],
      ],
    },
    matrixNote: '#4 RENO | NGUYEN_CAN | TB bàn giao: Không | Cải tạo: Có | TB mua: Có',
  },
  {
    matrixId: 5,
    code: 'HD-MTX-05-RENO-WH-HO-NOFURN',
    name: 'MTX#05 NGUYEN_CAN TB chủ',
    address: '5 Ma Trận Bình Thạnh',
    district: 'Bình Thạnh',
    province: 'TP. Hồ Chí Minh',
    area: 90,
    floors: 2,
    physicalRooms: 3,
    owner: 'Hoàng Văn E',
    rent: 240_000_000,
    start: '2026-05-01',
    end: '2028-04-30',
    desc: 'Ma trận #5 — RENO NGUYEN_CAN, có TB bàn giao, chỉ cải tạo.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [['Máy giặt', 'Máy cũ', 'Sân sau', 'GOOD', 1, 'TB chủ']],
    renovations: [['PLUMBING', 'Điện nước', 12_000_000, 'Sửa điện nước']],
    purchasedWholeHouse: [],
    matrixNote: '#5 RENO | NGUYEN_CAN | TB bàn giao: Có | Cải tạo: Có | TB mua: Không',
  },
  {
    matrixId: 6,
    code: 'HD-MTX-06-RENO-WH-HO-EQUIP',
    name: 'MTX#06 NGUYEN_CAN đủ',
    address: '6 Ma Trận Bình Thạnh',
    district: 'Bình Thạnh',
    province: 'TP. Hồ Chí Minh',
    area: 100,
    floors: 2,
    physicalRooms: 4,
    owner: 'Võ Thị F',
    rent: 260_000_000,
    start: '2026-05-01',
    end: '2028-04-30',
    desc: 'Ma trận #6 — RENO NGUYEN_CAN đầy đủ: TB chủ + cải tạo + mua TB.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [
      ['Quạt', 'Quạt trần', 'Phòng khách', 'GOOD', 2, ''],
      ['Tủ lạnh', 'Tủ 180L', 'Bếp', 'DAMAGED', 1, ''],
    ],
    renovations: [
      ['PAINTING', 'Sơn sửa', 10_000_000, ''],
      ['FLOORING', 'Sàn nhà', 15_000_000, ''],
    ],
    purchasedWholeHouse: [
      ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THEM_MOI', 1, 13_000_000, 36, '2026-06-01', '2029-05-31', ''],
      ['', 'BATHROOM', 'Nóng lạnh', 'NEW', 'THEM_MOI', 1, 2_800_000, 12, '2026-06-01', '2027-05-31', ''],
    ],
    supplement: {
      renovations: [['OTHER', 'Khác', 3_000_000, 'SUPP#1 chỉ cải tạo']],
      purchased: [
        ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THAY_THE', 1, 15_500_000, 36, '2027-02-01', '2030-01-31', 'SUPP#3 THAY_THE'],
      ],
    },
    matrixNote: '#6 RENO | NGUYEN_CAN | TB bàn giao: Có | Cải tạo: Có | TB mua: Có',
  },
  {
    matrixId: 7,
    code: 'HD-MTX-07-RENO-RM-NOFURN',
    name: 'MTX#07 THEO_PHONG cải tạo',
    address: '7 Ma Trận Gò Vấp',
    district: 'Gò Vấp',
    province: 'TP. Hồ Chí Minh',
    area: 75,
    floors: 2,
    physicalRooms: 4,
    owner: 'Đặng Văn G',
    rent: 210_000_000,
    start: '2026-06-01',
    end: '2028-05-31',
    desc: 'Ma trận #7 — RENO THEO_PHONG 3 phòng, không TB bàn giao, chỉ cải tạo.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 3,
    handover: [],
    renovations: [['PAINTING', 'Sơn sửa', 9_000_000, 'Sơn 3 phòng']],
    purchasedPerRoom: false,
    matrixNote: '#7 RENO | THEO_PHONG (3) | TB bàn giao: Không | Cải tạo: Có | TB mua: Không',
  },
  {
    matrixId: 8,
    code: 'HD-MTX-08-RENO-RM-EQUIP',
    name: 'MTX#08 THEO_PHONG mua TB',
    address: '8 Ma Trận Gò Vấp',
    district: 'Gò Vấp',
    province: 'TP. Hồ Chí Minh',
    area: 78,
    floors: 2,
    physicalRooms: 4,
    owner: 'Bùi Thị H',
    rent: 215_000_000,
    start: '2026-06-01',
    end: '2028-05-31',
    desc: 'Ma trận #8 — RENO THEO_PHONG 3 phòng, không TB chủ, cải tạo + mua TB.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 3,
    handover: [],
    renovations: [['PLUMBING', 'Điện nước', 7_500_000, 'Sửa WC từng phòng']],
    purchasedPerRoom: true,
    matrixNote: '#8 RENO | THEO_PHONG (3) | TB bàn giao: Không | Cải tạo: Có | TB mua: Có',
  },
  {
    matrixId: 9,
    code: 'HD-MTX-09-RENO-RM-HO-NOFURN',
    name: 'MTX#09 THEO_PHONG TB chủ',
    address: '9 Ma Trận Phú Nhuận',
    district: 'Phú Nhuận',
    province: 'TP. Hồ Chí Minh',
    area: 72,
    floors: 2,
    physicalRooms: 4,
    owner: 'Ngô Văn I',
    rent: 205_000_000,
    start: '2026-07-01',
    end: '2028-06-30',
    desc: 'Ma trận #9 — RENO THEO_PHONG 3 phòng, có TB chủ, chỉ cải tạo.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 3,
    handover: [['Giường', 'Giường sắt cũ', 'Tầng 2', 'GOOD', 2, '']],
    renovations: [['FLOORING', 'Sàn nhà', 11_000_000, 'Lát gạch 3 phòng']],
    purchasedPerRoom: false,
    matrixNote: '#9 RENO | THEO_PHONG (3) | TB bàn giao: Có | Cải tạo: Có | TB mua: Không',
  },
  {
    matrixId: 10,
    code: 'HD-MTX-10-RENO-RM-HO-EQUIP',
    name: 'MTX#10 THEO_PHONG đủ',
    address: '10 Ma Trận Phú Nhuận',
    district: 'Phú Nhuận',
    province: 'TP. Hồ Chí Minh',
    area: 84,
    floors: 2,
    physicalRooms: 4,
    owner: 'Dương Thị K',
    rent: 230_000_000,
    start: '2026-07-01',
    end: '2028-06-30',
    desc: 'Ma trận #10 — RENO THEO_PHONG 3 phòng đầy đủ.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 3,
    handover: [['Nóng lạnh', 'Máy cũ', 'WC chung', 'DAMAGED', 1, '']],
    renovations: [
      ['PAINTING', 'Sơn sửa', 8_500_000, ''],
      ['PLUMBING', 'Điện nước', 6_000_000, ''],
    ],
    purchasedPerRoom: true,
    supplement: {
      renovations: [['PAINTING', 'Sơn sửa', 4_000_000, 'SUPP#4 cải tạo + TB']],
      purchased: [
        ['102', '', 'Điều hòa', 'NEW', 'THAY_THE', 1, 9_800_000, 24, '2027-03-01', '2029-02-28', 'SUPP#4 THAY_THE phòng 102'],
      ],
    },
    matrixNote: '#10 RENO | THEO_PHONG (3) | TB bàn giao: Có | Cải tạo: Có | TB mua: Có',
  },
];

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

function buildPurchasedRowsForRooms(contractCode, totalRooms, warrantyStart) {
  const items = [
    ['Giường', 4_000_000, 12],
    ['Điều hòa', 8_500_000, 24],
    ['Nóng lạnh', 2_400_000, 12],
  ];
  return buildRoomNumbers(totalRooms).map((roomNumber, i) => {
    const [catalog, price, months] = items[i % items.length];
    const start = warrantyStart;
    const endYear = parseInt(start.slice(0, 4), 10) + Math.floor(months / 12);
    const end = `${endYear}-${start.slice(5, 7)}-28`;
    return [
      contractCode,
      roomNumber,
      '',
      catalog,
      'NEW',
      'THEM_MOI',
      1,
      price,
      months,
      start,
      end,
      `TB mới phòng ${roomNumber}`,
    ];
  });
}

function buildOnboardingMatrix() {
  return [
    [
      'STT',
      'Mã hợp đồng',
      'RENO/NORENO',
      'TB bàn giao đ1',
      'Hình thức đ2',
      'Cải tạo đ2',
      'TB mua đ2',
      'File đợt 2',
      'Diễn giải',
    ],
    ...scenarios.map((s) => [
      s.matrixId,
      s.code,
      s.phase2 ? 'RENO' : 'NORENO',
      (s.handover?.length ?? 0) > 0 ? 'Có' : 'Không',
      s.phase2
        ? s.exploitation === 'THEO_PHONG'
          ? `THEO_PHONG (${s.exploitRooms})`
          : 'NGUYEN_CAN'
        : '—',
      s.phase2 ? 'Có (≥1 dòng)' : '—',
      s.phase2
        ? (s.purchasedWholeHouse?.length ?? 0) > 0 || s.purchasedPerRoom
          ? 'Có'
          : 'Không'
        : '—',
      s.phase2 ? 'Có' : 'Không',
      s.matrixNote,
    ]),
    [''],
    ['Tổng: 10 trường hợp onboarding hợp lệ (2 NORENO + 8 RENO).'],
    ['NORENO: import đợt 1 xong → tự gửi Host. RENO: cần import đợt 2.'],
    ['RENO bắt buộc ≥1 hạng mục cải tạo ở sheet 3 trước khi gửi Host.'],
  ];
}

function buildSupplementMatrix() {
  return [
    ['STT', 'Mã HĐ', 'Cải tạo bổ sung', 'TB mua bổ sung', 'Hành động TB', 'Ghi chú'],
    ['1', 'HD-MTX-06-RENO-WH-HO-EQUIP', 'Có', 'Không', '—', 'SUPP chỉ cải tạo — sau khi nhà ACTIVE'],
    ['2', 'HD-MTX-04-RENO-WH-EQUIP', 'Không', 'Có', 'THEM_MOI', 'SUPP chỉ thêm TB mới'],
    ['3', 'HD-MTX-06-RENO-WH-HO-EQUIP', 'Không', 'Có', 'THAY_THE', 'SUPP thay TB đã mua đợt 2'],
    [
      '4',
      'HD-MTX-10-RENO-RM-HO-EQUIP',
      'Có',
      'Có',
      'THAY_THE',
      'SUPP cải tạo + thay TB phòng 102',
    ],
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

const purchasedRows = phase2Scenarios.flatMap((s) => {
  const warrantyStart = `${s.start.slice(0, 8)}15`;
  const rows = [];
  if (s.purchasedWholeHouse?.length) {
    for (const p of s.purchasedWholeHouse) {
      rows.push([s.code, ...p]);
    }
  }
  if (s.purchasedPerRoom) {
    rows.push(...buildPurchasedRowsForRooms(s.code, s.exploitRooms, warrantyStart));
  }
  return rows;
});

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
  ['Hướng dẫn — Ma trận đủ 10 TH onboarding (đợt 1)'],
  [''],
  ['File này cover đủ 10 trường hợp hợp lệ theo logic import SLMS2026.'],
  ['Sheet "0. Ma_Tran_Onboarding": tra cứu STT #1–#10.'],
  ['API: POST /api/v1/import/lease-excel?dryRun='],
  [''],
  ['#1–#2 NORENO: sau đợt 1 tự gửi Host — KHÔNG import file đợt 2.'],
  ['#3–#10 RENO: sau đợt 1 ở UNDER_RENOVATION — cần file đợt 2.'],
];

const huongDanDot2 = [
  ['Hướng dẫn — Ma trận 8 TH RENO (đợt 2)'],
  [''],
  ['Chỉ import sau khi đã import đợt 1 (#3–#10).'],
  ['API: POST /api/v1/import/renovation-excel?dryRun='],
  ['Sheet "0. Ma_Tran_Onboarding": đối chiếu từng HĐ.'],
];

const huongDanSupplement = [
  ['Hướng dẫn — Ma trận 4 TH cải tạo bổ sung'],
  [''],
  ['Dùng SAU KHI nhà #4, #6, #10 đã ACTIVE (host xác nhận + gán OM).'],
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

console.log('Đã tạo bộ ma trận onboarding:');
console.log('  Đợt 1:', OUT_DOT1);
console.log(`    - ${leaseRows.length} HĐ (#1–#10)`);
console.log(`    - ${handoverRows.length} dòng TB bàn giao`);
console.log('  Đợt 2:', OUT_DOT2);
console.log(`    - ${configRows.length} cấu hình (#3–#10)`);
console.log(`    - ${roomListRows.length} phòng THEO_PHONG`);
console.log(`    - ${renovationRows.length} dòng cải tạo`);
console.log(`    - ${purchasedRows.length} dòng TB mua`);
console.log('  Bổ sung:', OUT_SUPPLEMENT);
console.log(`    - ${supplementRenovationRows.length} cải tạo, ${supplementPurchasedRows.length} TB (4 TH)`);
