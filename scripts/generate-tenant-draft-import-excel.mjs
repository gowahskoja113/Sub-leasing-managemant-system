/**
 * Sinh file Excel demo import hàng loạt hợp đồng thuê nháp (DRAFT).
 * BĐS lấy từ docs/SLMS2026_import_matrix_dot1.xlsx + dot2.xlsx
 *
 * 50 tenant seed (có TK, MK 123456) ưu tiên gán THEO_PHONG;
 * phần còn lại = khách walk-in chưa có tài khoản.
 * Ngày onboard: 2026-08-17 (2 căn theo phòng) → 2026-08-24.
 *
 * Chạy: node scripts/generate-tenant-draft-import-excel.mjs
 */
import XLSX from 'xlsx';
import path from 'path';
import { fileURLToPath } from 'url';
import {
  TENANT_COUNT,
  TENANT_PASSWORD,
  seededTenant,
  walkInTenant,
  tenantPhone,
  tenantFullName,
} from './demo-tenants.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.join(__dirname, '..', 'docs');
const OUT = path.join(DOCS, 'SLMS2026_import_tenant_draft_contracts.xlsx');
const DOT1 = path.join(DOCS, 'SLMS2026_import_matrix_dot1.xlsx');
const DOT2 = path.join(DOCS, 'SLMS2026_import_matrix_dot2.xlsx');

function pad2(n) {
  return String(n).padStart(2, '0');
}

function addMonths(iso, months) {
  const [y, m, d] = iso.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1 + months, d));
  return `${dt.getUTCFullYear()}-${pad2(dt.getUTCMonth() + 1)}-${pad2(dt.getUTCDate())}`;
}

function addDaysIso(iso, days) {
  const [y, m, d] = iso.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + days));
  return `${dt.getUTCFullYear()}-${pad2(dt.getUTCMonth() + 1)}-${pad2(dt.getUTCDate())}`;
}

function matrixIdFromCode(code) {
  const m = String(code).match(/HD-MTX-(\d+)/i);
  return m ? parseInt(m[1], 10) : 0;
}

function furnishFromCode(code) {
  if (code.endsWith('-FULL')) return 'FULL';
  if (code.endsWith('-BASIC')) return 'BASIC';
  return 'NONE';
}

/** 2 căn theo phòng onboard hôm nay 17/8; các căn còn lại trải đến 24/8. */
function moveInDateForHouse(id, exploitation) {
  if (exploitation === 'THEO_PHONG') {
    if (id === 41 || id === 45) return '2026-08-17';
    const rest = [42, 43, 44, 46, 47, 48, 49, 50];
    const idx = rest.indexOf(id);
    if (idx < 0) return '2026-08-24';
    if (idx <= 5) return `2026-08-${pad2(18 + idx)}`; // 18–23
    return '2026-08-24'; // 49, 50
  }
  // 40 nguyên căn: 17/8 chưa onboard (nhường 2 căn theo phòng) → 18–24
  const dayOffset = Math.min(6, Math.floor((id - 1) / 6)); // 0..6
  return `2026-08-${pad2(18 + dayOffset)}`;
}

function rentFor(exploitation, furnish, roomIndex) {
  if (exploitation === 'THEO_PHONG') {
    const base = furnish === 'FULL' ? 5_600_000 : furnish === 'BASIC' ? 4_100_000 : 3_300_000;
    return base + roomIndex * 80_000;
  }
  return furnish === 'FULL' ? 12_500_000 : furnish === 'BASIC' ? 9_200_000 : 7_400_000;
}

function extraProfile(index, district, address) {
  const year = 1987 + (index % 13);
  const month = pad2(1 + (index % 12));
  const day = pad2(1 + (index * 3) % 27);
  const issueYear = 2018 + (index % 6);
  return {
    dob: `${year}-${month}-${day}`,
    cccdDate: `${issueYear}-${month}-15`,
    cccdPlace: `CA ${district}`,
    hktt: `${address}, ${district}, TP.HCM`,
  };
}

const wb1 = XLSX.readFile(DOT1);
const wb2 = XLSX.readFile(DOT2);
const leases = XLSX.utils.sheet_to_json(wb1.Sheets['1. Hop_Dong_Thue']);
const configs = XLSX.utils.sheet_to_json(wb2.Sheets['1. Cau_Hinh_Khai_Thac']);
const roomRows = XLSX.utils.sheet_to_json(wb2.Sheets['2. Danh_Sach_Phong']);

const leaseByCode = new Map(leases.map((r) => [r['Mã hợp đồng'], r]));
const configByCode = new Map(configs.map((r) => [r['Mã hợp đồng thuê'], r]));
const roomsByCode = new Map();
for (const r of roomRows) {
  const code = r['Mã hợp đồng thuê'];
  if (!roomsByCode.has(code)) roomsByCode.set(code, []);
  roomsByCode.get(code).push(String(r['Số phòng']));
}

const houses = leases.map((lease) => {
  const code = lease['Mã hợp đồng'];
  const cfg = configByCode.get(code) ?? {};
  const exploitation = cfg['Hình thức khai thác'] || 'NGUYEN_CAN';
  return {
    code,
    name: lease['Tên tòa nhà'],
    district: lease['Quận/Huyện'],
    address: lease['Địa chỉ chi tiết'],
    exploitation,
    furnish: furnishFromCode(code),
    matrixId: matrixIdFromCode(code),
    rooms: roomsByCode.get(code) ?? [],
  };
}).sort((a, b) => a.matrixId - b.matrixId);

const occupancies = [];
for (const h of houses.filter((x) => x.exploitation === 'THEO_PHONG')) {
  const rooms = h.rooms.length ? h.rooms : ['101', '102', '103'];
  rooms.forEach((room, roomIndex) => {
    occupancies.push({ house: h, room, roomIndex });
  });
}
for (const h of houses.filter((x) => x.exploitation === 'NGUYEN_CAN')) {
  occupancies.push({ house: h, room: '', roomIndex: 0 });
}

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

const demoRows = [];
const accountRows = [];
let seededUsed = 0;
let walkInUsed = 0;

occupancies.forEach((occ, i) => {
  const seeded = i < TENANT_COUNT;
  const tenant = seeded ? seededTenant(i + 1) : walkInTenant(i - TENANT_COUNT + 1);
  if (seeded) seededUsed++;
  else walkInUsed++;

  const { house, room, roomIndex } = occ;
  const moveIn = moveInDateForHouse(house.matrixId, house.exploitation);
  const end = addDaysIso(addMonths(moveIn, 12), -1);
  const rent = rentFor(house.exploitation, house.furnish, roomIndex);
  const depositMonths = house.furnish === 'FULL' ? 2 : 1;
  const extra = extraProfile(i + 1, house.district, house.address);

  demoRows.push([
    house.code,
    '',
    house.name,
    house.exploitation,
    room,
    tenant.fullName,
    tenant.cccd,
    tenant.phone,
    extra.dob,
    extra.cccdDate,
    extra.cccdPlace,
    extra.hktt,
    moveIn,
    end,
    rent,
    depositMonths,
    rent * depositMonths,
    moveIn,
  ]);

  accountRows.push([
    i + 1,
    tenant.fullName,
    tenant.phone,
    tenant.cccd,
    tenant.seeded ? `Có TK — MK ${TENANT_PASSWORD}` : 'Chưa có TK (import sẽ tạo, firstLogin)',
    house.code,
    house.name,
    house.exploitation,
    room || '—',
    moveIn,
  ]);
});

const rmHouses = houses.filter((h) => h.exploitation === 'THEO_PHONG');
const whHouses = houses.filter((h) => h.exploitation === 'NGUYEN_CAN');

const guide = [
  ['Hướng dẫn import hợp đồng thuê nháp (DRAFT) — 50 căn matrix 2026'],
  [''],
  ['1. BĐS phải ĐÃ TỒN TẠI và ACTIVE (import đợt 1 + đợt 2 + Host duyệt + gán OM).'],
  ['2. Ưu tiên điền "Mã HĐ inbound" (= mã cột "Mã hợp đồng" file đợt 1).'],
  ['3. Thuê theo phòng → bắt buộc "Số phòng" (101, 102, … — file matrix đợt 2).'],
  ['4. Ngày: YYYY-MM-DD. Onboard: 17/08/2026 (2 căn THEO_PHONG #41, #45) → 24/08/2026.'],
  ['5. Nếu có "Số tháng cọc" mà trống "Tiền cọc" → BE tính deposit = giá thuê × số tháng.'],
  ['6. API: POST /api/v1/import/tenant-draft-contracts-excel?dryRun=true|false'],
  [''],
  ['Tài khoản tenant seed (50): login = SĐT, mật khẩu 123456. Sheet "0. Tai_Khoan_San".'],
  ['Ưu tiên gán 50 SĐT seed cho toàn bộ phòng THEO_PHONG, phần dư sang nguyên căn.'],
  ['Dòng "Chưa có TK" = khách walk-in — import tạo account mới (chưa đặt mật khẩu).'],
];

const refHeaders = ['Mã HĐ inbound (đợt 1)', 'Tên tòa (matrix)', 'Loại', 'Phòng / NT', 'Ngày onboard', 'Ghi chú'];
const refRows = houses.map((h) => {
  const moveIn = moveInDateForHouse(h.matrixId, h.exploitation);
  const rooms = h.exploitation === 'THEO_PHONG' ? h.rooms.join(', ') : '—';
  return [
    h.code,
    h.name,
    h.exploitation,
    `${h.furnish} | ${rooms}`,
    moveIn,
    h.exploitation === 'THEO_PHONG'
      ? 'Ưu tiên khách đã có TK'
      : 'TK seed nếu còn; không thì walk-in',
  ];
});

const accHeaders = [
  'STT',
  'Họ tên',
  'SĐT (username)',
  'CCCD',
  'Tài khoản',
  'Mã HĐ inbound',
  'Tòa nhà',
  'Loại thuê',
  'Phòng',
  'Ngày vào ở',
];

const wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(guide), '0. Huong_Dan');
XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([refHeaders, ...refRows]), '0. Tham_Chieu_BDS');
XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([accHeaders, ...accountRows]), '0. Tai_Khoan_San');
XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([headers, ...demoRows]), '1. Hop_Dong_Nhap_Khach');
XLSX.writeFile(wb, OUT);

const todayRows = demoRows.filter((r) => r[12] === '2026-08-17');
const todayHouses = [...new Set(todayRows.map((r) => r[0]))];
const phones = new Set(demoRows.map((r) => r[7]));
if (phones.size !== demoRows.length) {
  throw new Error('Trùng SĐT trong file HĐ nháp');
}

console.log('Wrote', OUT);
console.log('HĐ nháp:', demoRows.length);
console.log('  THEO_PHONG nhà / phòng:', rmHouses.length, '/', occupancies.filter((o) => o.room).length);
console.log('  NGUYEN_CAN:', whHouses.length);
console.log('  Có TK seed:', seededUsed, '| Walk-in chưa TK:', walkInUsed);
console.log('  Onboard 17/08 (hôm nay):', todayHouses.join(', '), `(${todayRows.length} HĐ)`);
console.log('  Seed SĐT mẫu #1 / #50:', tenantPhone(1), tenantFullName(1), '/', tenantPhone(50), tenantFullName(50));
console.log('  MK seed:', TENANT_PASSWORD);
