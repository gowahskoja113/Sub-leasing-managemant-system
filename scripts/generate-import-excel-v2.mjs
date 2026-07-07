/**
 * Sinh 3 file Excel import theo quy trình 2 đợt + cải tạo bổ sung.
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

/**
 * Ma trận kịch bản — mỗi object sinh dữ liệu cho cả 3 file.
 * phase2: false → không có dòng trong file đợt 2 (gửi Host sau đợt 1).
 */
const scenarios = [
  {
    code: 'HD-WH-RENO-FURN',
    name: 'Villa Thảo Điền View',
    address: '12 Nguyễn Văn Hưởng',
    district: 'Quận 3',
    province: 'TP. Hồ Chí Minh',
    area: 180,
    floors: 2,
    physicalRooms: 5,
    owner: 'Trần Minh Tuấn',
    rent: 450_000_000,
    start: '2026-03-01',
    end: '2029-02-28',
    desc: 'Biệt thự thuê nguyên căn — chờ cải tạo đợt 2.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [
      ['Điều hòa', 'Máy lạnh 2HP cũ', 'Tầng 1, phòng khách', 'GOOD', 2, 'Chủ bàn giao'],
      ['Tủ lạnh', 'Tủ 250L', 'Nhà bếp', 'GOOD', 1, ''],
      ['Máy giặt', 'Máy giặt cửa trước', 'Sân sau', 'GOOD', 1, ''],
    ],
    renovations: [
      ['PAINTING', 'Sơn sửa', 18_000_000, 'Sơn lại toàn bộ trong nhà'],
      ['PLUMBING', 'Điện nước', 22_000_000, 'Thay đường ống tầng 2'],
      ['FLOORING', 'Sàn nhà', 12_000_000, 'Sàn gỗ phòng ngủ'],
    ],
    purchasedWholeHouse: [
      ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THEM_MOI', 1, 16_500_000, 36, '2026-04-01', '2029-03-31', 'Điều hòa multi phòng khách'],
      ['', 'KITCHEN', 'Bếp từ', 'NEW', 'THEM_MOI', 1, 5_500_000, 24, '2026-04-01', '2028-03-31', 'Bếp từ nhà bếp'],
      ['', 'BATHROOM', 'Nóng lạnh', 'NEW', 'THEM_MOI', 2, 2_800_000, 12, '2026-04-01', '2027-03-31', 'Nóng lạnh 2 WC'],
    ],
    supplement: {
      renovations: [
        ['PAINTING', 'Sơn sửa', 8_000_000, 'Cải tạo bổ sung lần 2 — sơn lại phòng ngủ'],
        ['EQUIPMENT', 'Thiết bị', 3_500_000, 'Thay router WiFi toàn nhà'],
      ],
      purchased: [
        ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THAY_THE', 1, 18_500_000, 36, '2027-01-01', '2030-12-31', 'Thay điều hòa mới phòng khách'],
        ['', 'LIVING_ROOM', 'Quạt', 'NEW', 'THEM_MOI', 2, 1_200_000, 12, '2027-01-01', '2027-12-31', 'Thêm quạt trần — giữ đồ cũ'],
      ],
    },
    matrixNote: 'Thuê nguyên căn — giữ nguyên căn sau cải tạo + mua nội thất',
  },
  {
    code: 'HD-WH-NORENO-FURN',
    name: 'Nhà phố Gò Vấp',
    address: '88 Quang Trung',
    district: 'Gò Vấp',
    province: 'TP. Hồ Chí Minh',
    area: 95,
    floors: 2,
    physicalRooms: 4,
    owner: 'Phạm Văn Đức',
    rent: 280_000_000,
    start: '2026-05-01',
    end: '2031-04-30',
    desc: 'Vào ở ngay — chỉ TB chủ bàn giao, không cải tạo.',
    phase2: false,
    handover: [
      ['Điều hòa', '3 máy lạnh', 'Các phòng trong nhà', 'GOOD', 3, ''],
      ['Máy giặt', 'Máy giặt sân sau', 'Sân sau', 'GOOD', 1, ''],
      ['Giường', 'Giường gỗ tầng 2', 'Phòng ngủ tầng 2', 'GOOD', 2, ''],
    ],
    matrixNote: 'Không cải tạo — sau đợt 1 gửi Host luôn',
  },
  {
    code: 'HD-WH-RENO-NOFURN',
    name: 'Penthouse Quận 1',
    address: '101 Lê Lợi',
    district: 'Quận 1',
    province: 'TP. Hồ Chí Minh',
    area: 200,
    floors: 1,
    physicalRooms: 4,
    owner: 'Đặng Quốc Bảo',
    rent: 550_000_000,
    start: '2026-09-01',
    end: '2031-08-31',
    desc: 'Cải tạo hoàn thiện, không mua TB mới.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [],
    renovations: [
      ['FURNITURE', 'Nội thất', 80_000_000, 'Hoàn thiện nội thất cao cấp'],
      ['PLUMBING', 'Điện nước', 30_000_000, 'Smart home'],
      ['PAINTING', 'Sơn sửa', 15_000_000, 'Sơn nước cao cấp'],
    ],
    purchasedWholeHouse: [],
    matrixNote: 'Thuê nguyên căn — chỉ cải tạo, không mua TB mới',
  },
  {
    code: 'HD-WH-NORENO-NOFURN',
    name: 'Căn hộ mini Phú Nhuận',
    address: '23 Hoàng Văn Thụ',
    district: 'Phú Nhuận',
    province: 'TP. Hồ Chí Minh',
    area: 55,
    floors: 1,
    physicalRooms: 1,
    owner: 'Nguyễn Thu Hà',
    rent: 180_000_000,
    start: '2026-06-01',
    end: '2027-05-31',
    desc: 'Nhà trống hoàn toàn, không cải tạo.',
    phase2: false,
    handover: [],
    matrixNote: 'Nhà trống, không cải tạo — gửi Host sau đợt 1',
  },
  {
    code: 'HD-WH-RENO-SPLIT',
    name: 'Nhà trọ Bình Thạnh',
    address: '45 Phan Văn Trị',
    district: 'Bình Thạnh',
    province: 'TP. Hồ Chí Minh',
    area: 120,
    floors: 3,
    physicalRooms: 8,
    owner: 'Lê Thị Hương',
    rent: 320_000_000,
    start: '2026-04-01',
    end: '2028-03-31',
    desc: 'Thuê nguyên căn từ chủ — đợt 2 chia 5 phòng khai thác.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 5,
    handover: [
      ['Quạt', 'Quạt trần cũ', 'Toàn nhà', 'GOOD', 5, 'TB chủ bàn giao trước cải tạo'],
      ['Nóng lạnh', 'Nóng lạnh chung', 'Tầng 1', 'DAMAGED', 1, 'Hỏng nhẹ, cần thay'],
    ],
    renovations: [
      ['FLOORING', 'Sàn nhà', 25_000_000, 'Lát gạch 5 phòng'],
      ['PLUMBING', 'Điện nước', 15_000_000, 'Sửa WC từng phòng'],
      ['PAINTING', 'Sơn sửa', 10_000_000, 'Sơn hành lang + phòng'],
    ],
    purchasedPerRoom: true,
    supplement: {
      renovations: [['PLUMBING', 'Điện nước', 6_000_000, 'Sửa rò rỉ phòng 102']],
      purchased: [
        ['102', '', 'Điều hòa', 'NEW', 'THAY_THE', 1, 9_500_000, 24, '2027-03-01', '2029-02-28', 'Thay điều hòa phòng 102'],
        ['103', '', 'Giường', 'NEW', 'THEM_MOI', 1, 4_500_000, 12, '2027-03-01', '2028-02-28', 'Thêm giường tầng phòng 103'],
      ],
    },
    matrixNote: 'Thuê nguyên căn đợt 1 — đợt 2 chia 5 phòng + mua TB từng phòng',
  },
  {
    code: 'HD-WH-RENO-SPLIT8',
    name: 'Nhà trọ Gò Vấp 8 phòng',
    address: '156 Phạm Văn Chiêu',
    district: 'Gò Vấp',
    province: 'TP. Hồ Chí Minh',
    area: 160,
    floors: 4,
    physicalRooms: 10,
    owner: 'Võ Minh Khang',
    rent: 380_000_000,
    start: '2026-02-01',
    end: '2029-01-31',
    desc: 'Nhà trọ lớn — chia 8 phòng khai thác.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 8,
    handover: [
      ['Quạt', 'Quạt trần', 'Mỗi phòng', 'GOOD', 8, ''],
      ['Điều hòa', 'Máy cũ tầng 1', 'Phòng 101', 'BROKEN', 1, 'Không dùng được'],
    ],
    renovations: [
      ['STRUCTURAL', 'Kết cấu', 35_000_000, 'Ngăn vách 8 phòng'],
      ['PLUMBING', 'Điện nước', 28_000_000, 'WC riêng từng phòng'],
      ['FLOORING', 'Sàn nhà', 32_000_000, 'Gạch chống trơn'],
      ['PAINTING', 'Sơn sửa', 18_000_000, 'Sơn toàn bộ'],
    ],
    purchasedPerRoom: true,
    supplement: {
      renovations: [['OTHER', 'Khác', 4_000_000, 'Lắp camera an ninh']],
      purchased: [
        ['105', '', 'Nóng lạnh', 'NEW', 'THEM_MOI', 1, 2_600_000, 12, '2027-06-01', '2028-05-31', 'Lắp nóng lạnh phòng 105'],
      ],
    },
    matrixNote: 'Nhà trọ 8 phòng — cải tạo kết cấu + TB từng phòng',
  },
  {
    code: 'HD-WH-RENO-SPLIT3',
    name: 'Nhà chia 3 phòng Quận 3',
    address: '67 Võ Văn Tần',
    district: 'Quận 3',
    province: 'TP. Hồ Chí Minh',
    area: 75,
    floors: 2,
    physicalRooms: 3,
    owner: 'Hoàng Thị Lan',
    rent: 220_000_000,
    start: '2026-07-01',
    end: '2028-06-30',
    desc: 'Nhà nhỏ chia 3 phòng — không TB bàn giao.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 3,
    handover: [],
    renovations: [
      ['PAINTING', 'Sơn sửa', 8_000_000, 'Sơn 3 phòng'],
      ['PLUMBING', 'Điện nước', 6_500_000, 'Sửa điện nước'],
    ],
    purchasedPerRoom: true,
    purchasedRoomSubset: 2,
    matrixNote: 'THEO_PHONG 3 phòng — chỉ mua TB 2 phòng đầu',
  },
  {
    code: 'HD-WH-RENO-EQUIPONLY',
    name: 'Căn hộ Bình Thạnh equip-only',
    address: '29 Xô Viết Nghệ Tĩnh',
    district: 'Bình Thạnh',
    province: 'TP. Hồ Chí Minh',
    area: 68,
    floors: 1,
    physicalRooms: 2,
    owner: 'Bùi Văn Nam',
    rent: 195_000_000,
    start: '2026-08-01',
    end: '2027-07-31',
    desc: 'Cải tạo nhẹ — chủ yếu mua TB mới nguyên căn.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [],
    renovations: [
      ['EQUIPMENT', 'Thiết bị', 2_000_000, 'Hoàn thiện lắp đặt trước bàn giao TB mới'],
    ],
    purchasedWholeHouse: [
      ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THEM_MOI', 1, 12_000_000, 24, '2026-09-01', '2028-08-31', ''],
      ['', 'KITCHEN', 'Tủ lạnh', 'NEW', 'THEM_MOI', 1, 8_500_000, 24, '2026-09-01', '2028-08-31', ''],
      ['', 'BATHROOM', 'Máy giặt', 'NEW', 'THEM_MOI', 1, 6_200_000, 12, '2026-09-01', '2027-08-31', 'Máy giặt cửa trước'],
    ],
    matrixNote: 'NGUYEN_CAN — 1 hạng mục cải tạo nhẹ + mua TB',
  },
  {
    code: 'HD-WH-RENO-MINIMAL',
    name: 'Nhà Cầu Giấy tối thiểu',
    address: '15 Dịch Vọng Hậu',
    district: 'Cầu Giấy',
    province: 'Hà Nội',
    area: 90,
    floors: 3,
    physicalRooms: 4,
    owner: 'Nguyễn Văn Hùng',
    rent: 240_000_000,
    start: '2026-04-15',
    end: '2029-04-14',
    desc: 'Hà Nội — cải tạo tối thiểu 1 hạng mục.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [
      ['Bàn ăn', 'Bộ bàn ghế gỗ', 'Phòng ăn', 'GOOD', 1, ''],
    ],
    renovations: [
      ['PAINTING', 'Sơn sửa', 5_500_000, 'Sơn lại tường tầng 1'],
    ],
    purchasedWholeHouse: [],
    matrixNote: 'Hà Nội — 1 dòng cải tạo, không mua TB',
  },
  {
    code: 'HD-WH-NORENO-DAMAGE',
    name: 'Nhà Quận 1 TB hỏng',
    address: '5 Nguyễn Huệ',
    district: 'Quận 1',
    province: 'TP. Hồ Chí Minh',
    area: 110,
    floors: 2,
    physicalRooms: 3,
    owner: 'Trịnh Quốc Anh',
    rent: 420_000_000,
    start: '2026-10-01',
    end: '2030-09-30',
    desc: 'TB bàn giao nhiều trạng thái hỏng — không cải tạo.',
    phase2: false,
    handover: [
      ['Điều hòa', 'Máy lạnh 1.5HP', 'Phòng khách', 'DAMAGED', 1, 'Rò gas'],
      ['Tủ lạnh', 'Tủ 180L', 'Bếp', 'BROKEN', 1, 'Không lạnh'],
      ['Giường', 'Giường tầng', 'Phòng ngủ', 'GOOD', 2, ''],
      ['Quạt', 'Quạt trần', 'Phòng ăn', 'NEW', 1, 'Mới lắp'],
    ],
    matrixNote: 'TB bàn giao DAMAGED/BROKEN/NEW — gửi Host sau đợt 1',
  },
  {
    code: 'HD-WH-RENO-STRUCT',
    name: 'Nhà cải tạo kết cấu Q3',
    address: '200 Nam Kỳ Khởi Nghĩa',
    district: 'Quận 3',
    province: 'TP. Hồ Chí Minh',
    area: 140,
    floors: 2,
    physicalRooms: 6,
    owner: 'Lý Thanh Tùng',
    rent: 360_000_000,
    start: '2026-05-15',
    end: '2030-05-14',
    desc: 'Cải tạo kết cấu + nội thất nguyên căn.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [
      ['Tủ quần áo', 'Tủ gỗ cũ', 'Phòng ngủ', 'GOOD', 2, ''],
    ],
    renovations: [
      ['STRUCTURAL', 'Kết cấu', 45_000_000, 'Phá vách mở rộng phòng khách'],
      ['FLOORING', 'Sàn nhà', 20_000_000, 'Sàn gỗ công nghiệp'],
      ['FURNITURE', 'Nội thất', 55_000_000, 'Bộ sofa + bàn ăn mới'],
      ['OTHER', 'Khác', 8_000_000, 'Hệ thống đèn LED'],
    ],
    purchasedWholeHouse: [
      ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THEM_MOI', 2, 14_000_000, 36, '2026-06-01', '2029-05-31', '2 máy phòng khách'],
      ['', 'KITCHEN', 'Bếp từ', 'NEW', 'THEM_MOI', 1, 7_200_000, 24, '2026-06-01', '2028-05-31', ''],
      ['', 'GARAGE', 'Khác', 'NEW', 'THEM_MOI', 1, 3_000_000, 12, '2026-06-01', '2027-05-31', 'Camera an ninh'],
    ],
    matrixNote: 'NGUYEN_CAN — nhiều hạng mục cải tạo STRUCTURAL + mua TB',
  },
  {
    code: 'HD-WH-RENO-SPLIT12',
    name: 'KTX mini 12 phòng Gò Vấp',
    address: '301 Quang Trung',
    district: 'Gò Vấp',
    province: 'TP. Hồ Chí Minh',
    area: 220,
    floors: 4,
    physicalRooms: 14,
    owner: 'Đỗ Thị Mai',
    rent: 520_000_000,
    start: '2026-01-01',
    end: '2030-12-31',
    desc: 'Ký túc xá mini — 12 phòng khai thác.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 12,
    handover: [
      ['Giường', 'Giường sắt cũ', 'Tầng 2-3', 'GOOD', 6, 'Chủ để lại'],
      ['Quạt', 'Quạt trần', 'Hành lang', 'GOOD', 4, ''],
    ],
    renovations: [
      ['STRUCTURAL', 'Kết cấu', 60_000_000, 'Ngăn 12 phòng'],
      ['PLUMBING', 'Điện nước', 42_000_000, 'WC chung + riêng'],
      ['FLOORING', 'Sàn nhà', 38_000_000, 'Gạch 12 phòng'],
      ['PAINTING', 'Sơn sửa', 22_000_000, ''],
      ['EQUIPMENT', 'Thiết bị', 15_000_000, 'Tủ locker chung'],
    ],
    purchasedPerRoom: true,
    matrixNote: 'THEO_PHONG 12 phòng — dataset lớn nhất',
  },
  {
    code: 'HD-WH-NORENO-HANOI',
    name: 'Nhà Hà Nội không cải tạo',
    address: '8 Trần Duy Hưng',
    district: 'Cầu Giấy',
    province: 'Hà Nội',
    area: 80,
    floors: 2,
    physicalRooms: 3,
    owner: 'Phan Minh Đức',
    rent: 200_000_000,
    start: '2026-11-01',
    end: '2028-10-31',
    desc: 'Hà Nội — vào ở ngay, không cải tạo.',
    phase2: false,
    handover: [
      ['Điều hòa', '2 máy Inverter', 'Phòng ngủ', 'GOOD', 2, ''],
    ],
    matrixNote: 'Hà Nội NORENO — gửi Host sau đợt 1',
  },
  {
    code: 'HD-WH-RENO-HANOI',
    name: 'Biệt thự Hà Nội nguyên căn',
    address: '22 Phạm Hùng',
    district: 'Cầu Giấy',
    province: 'Hà Nội',
    area: 250,
    floors: 3,
    physicalRooms: 7,
    owner: 'Vũ Thị Hồng',
    rent: 600_000_000,
    start: '2026-03-15',
    end: '2031-03-14',
    desc: 'Biệt thự Hà Nội — cải tạo + mua TB cao cấp.',
    phase2: true,
    exploitation: 'NGUYEN_CAN',
    exploitRooms: null,
    handover: [
      ['Điều hòa', 'Central 3HP', 'Phòng khách', 'GOOD', 1, ''],
      ['Tủ lạnh', 'Side by side', 'Bếp', 'GOOD', 1, ''],
    ],
    renovations: [
      ['PAINTING', 'Sơn sửa', 25_000_000, 'Sơn ngoại thất + nội thất'],
      ['PLUMBING', 'Điện nước', 35_000_000, 'Nâng cấp điện 3 pha'],
      ['FURNITURE', 'Nội thất', 90_000_000, 'Nội thất full'],
    ],
    purchasedWholeHouse: [
      ['', 'LIVING_ROOM', 'Điều hòa', 'NEW', 'THEM_MOI', 3, 17_000_000, 36, '2026-04-15', '2029-04-14', 'Bổ sung phòng ngủ'],
      ['', 'KITCHEN', 'Bếp từ', 'NEW', 'THEM_MOI', 1, 12_000_000, 36, '2026-04-15', '2029-04-14', ''],
      ['', 'BALCONY', 'Quạt', 'NEW', 'THEM_MOI', 2, 900_000, 12, '2026-04-15', '2027-04-14', ''],
    ],
    supplement: {
      renovations: [['FLOORING', 'Sàn nhà', 18_000_000, 'Thay sàn gỗ phòng master']],
      purchased: [
        ['', 'KITCHEN', 'Tủ lạnh', 'NEW', 'THAY_THE', 1, 22_000_000, 36, '2027-08-01', '2030-07-31', 'Thay tủ lạnh lớn hơn'],
      ],
    },
    matrixNote: 'Hà Nội NGUYEN_CAN — biệt thự cao cấp',
  },
  {
    code: 'HD-WH-RENO-SPLIT5-HN',
    name: 'Nhà trọ Cầu Giấy 5 phòng',
    address: '44 Hoàng Quốc Việt',
    district: 'Cầu Giấy',
    province: 'Hà Nội',
    area: 100,
    floors: 3,
    physicalRooms: 6,
    owner: 'Trần Văn Phúc',
    rent: 270_000_000,
    start: '2026-06-01',
    end: '2028-05-31',
    desc: 'Hà Nội — chia 5 phòng khai thác.',
    phase2: true,
    exploitation: 'THEO_PHONG',
    exploitRooms: 5,
    handover: [
      ['Nóng lạnh', 'Nóng lạnh tầng 1', 'WC tầng 1', 'DAMAGED', 1, ''],
    ],
    renovations: [
      ['PAINTING', 'Sơn sửa', 12_000_000, ''],
      ['PLUMBING', 'Điện nước', 14_000_000, 'Sửa WC'],
    ],
    purchasedPerRoom: true,
    matrixNote: 'Hà Nội THEO_PHONG 5 phòng',
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

function buildPurchasedRowsForRooms(contractCode, totalRooms, warrantyStart, subset = null) {
  const catalogs = ['Giường', 'Điều hòa', 'Tủ quần áo', 'Nóng lạnh', 'Quạt', 'Bàn ăn'];
  const prices = [4_200_000, 8_000_000, 3_200_000, 2_500_000, 800_000, 2_100_000];
  const months = [12, 24, 12, 12, 6, 12];
  const roomNumbers = buildRoomNumbers(totalRooms);
  const targetRooms = subset ? roomNumbers.slice(0, subset) : roomNumbers;

  return targetRooms.map((roomNumber, i) => {
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

function buildScenarioMatrix() {
  const header = [
    'Mã hợp đồng (demo)',
    'Đợt 1 TB bàn giao',
    'Đợt 2 hình thức',
    'Đợt 2 cải tạo',
    'Đợt 2 mua TB',
    'Diễn giải',
  ];
  const rows = scenarios.map((s) => [
    s.code,
    (s.handover?.length ?? 0) > 0 ? 'Có' : 'Không',
    s.phase2 ? (s.exploitation === 'THEO_PHONG' ? `THEO_PHONG (${s.exploitRooms} phòng)` : 'NGUYEN_CAN') : '—',
    s.phase2 && (s.renovations?.length ?? 0) > 0 ? 'Có' : s.phase2 ? 'Không' : '—',
    s.phase2 && ((s.purchasedWholeHouse?.length ?? 0) > 0 || s.purchasedPerRoom) ? 'Có' : s.phase2 ? 'Không' : '—',
    s.matrixNote,
  ]);
  return [
    header,
    ...rows,
    [''],
    ['Quy tắc:'],
    ['- Đợt 1: luôn import nhà nguyên căn (HĐ thuê từ chủ). TB bàn giao chỉ ghi nhận, không gắn phòng.'],
    ['- Đợt 2: sheet "1. Cau_Hinh_Khai_Thac" quyết định NGUYEN_CAN hay THEO_PHONG.'],
    ['- THEO_PHONG: sheet "2. Danh_Sach_Phong" bắt buộc đủ Số phòng khai thác.'],
    ['- HD-*-NORENO-*: không có dòng trong file đợt 2.'],
    [`- Tổng ${scenarios.length} kịch bản demo (${scenarios.filter((s) => s.phase2).length} có đợt 2).`],
  ];
}

// --- Headers ---

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

const configHeader = [
  'Mã hợp đồng thuê',
  'Hình thức khai thác',
  'Số phòng khai thác',
];

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

// --- Build data from scenarios ---

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

const handoverRows = scenarios.flatMap((s) =>
  (s.handover ?? []).map((h) => [s.code, ...h]),
);

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
  const warrantyStart = s.start.replace(/-\d{2}$/, '-01').replace(/^\d{4}-\d{2}/, (m) => {
    const [y, mo] = m.split('-').map(Number);
    return mo >= 10 ? `${y}-${String(mo).padStart(2, '0')}` : `${y}-${String(mo + 1).padStart(2, '0')}`;
  });
  const rows = [];
  if (s.purchasedWholeHouse?.length) {
    for (const p of s.purchasedWholeHouse) {
      rows.push([s.code, ...p]);
    }
  }
  if (s.purchasedPerRoom) {
    rows.push(
      ...buildPurchasedRowsForRooms(
        s.code,
        s.exploitRooms,
        s.start.slice(0, 8) + '01',
        s.purchasedRoomSubset ?? null,
      ),
    );
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

const scenarioMatrix = buildScenarioMatrix();

const huongDanDot1 = [
  ['Hướng dẫn — Đợt 1: Khởi tạo nhà (HĐ thuê từ chủ)'],
  [''],
  ['Sheet "1. Hop_Dong_Thue": tạo Property + hợp đồng thuê — LUÔN nguyên căn.'],
  ['Sheet "2. Thiet_Bi_Ban_Giao" (tùy chọn): TB chủ bàn giao — CHỈ HIỂN THỊ, không gắn phòng vận hành.'],
  ['"Tổng số phòng" = đặc tính vật lý căn nhà thuê, không phải số phòng khai thác.'],
  ['Chia phòng / hình thức khai thác → quyết định ở file đợt 2.'],
  [`Xem sheet "0. Ma_Tran_Trường_Hop" (${scenarios.length} kịch bản).`],
];

const huongDanDot2 = [
  ['Hướng dẫn — Đợt 2: Cấu hình khai thác + cải tạo + TB mua mới'],
  [''],
  ['Sheet "1. Cau_Hinh_Khai_Thac" (BẮT BUỘC): quyết định NGUYEN_CAN hoặc THEO_PHONG.'],
  ['Sheet "2. Danh_Sach_Phong": bắt buộc nếu THEO_PHONG — đủ Số phòng khai thác.'],
  ['Sheet "3. Hop_Dong_Cai_Tao" (tùy chọn): chi phí cải tạo.'],
  ['Sheet "4. Thiet_Bi_Mua_Moi" (tùy chọn): TB mua + bảo hành; Hành động = THEM_MOI hoặc THAY_THE.'],
  [''],
  ['HD-*-NORENO-*: không có trong file đợt 2 (đã gửi Host sau đợt 1).'],
  [`Xem sheet "0. Ma_Tran_Trường_Hop" (${phase2Scenarios.length} HĐ đợt 2).`],
];

const huongDanSupplement = [
  ['Hướng dẫn — Cải tạo bổ sung (lần 2, 3…)'],
  [''],
  ['Điều kiện: nhà đang ACTIVE → gọi API start-renovation → UNDER_RENOVATION (session ≥ 2).'],
  ['API: POST /api/v1/import/renovation-supplement-excel?dryRun='],
  [''],
  ['Sheet "1. Hop_Dong_Cai_Tao": chi phí cải tạo đợt này.'],
  ['Sheet "2. Thiet_Bi_Mua_Moi": THEM_MOI giữ TB cũ; THAY_THE disable TB ACTIVE cùng vị trí + catalog.'],
  ['KHÔNG có sheet cấu hình khai thác / danh sách phòng.'],
  [`Mẫu: ${supplementRenovationRows.length} dòng cải tạo, ${supplementPurchasedRows.length} dòng TB (4 HĐ).`],
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
  { name: '0. Danh_Muc_Tham_Khao', data: [...equipmentCatalog, [''], ...houseAreas] },
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

const wbSupplement = buildWorkbook([
  { name: '0. Huong_Dan', data: huongDanSupplement },
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
