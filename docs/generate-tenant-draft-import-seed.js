/**
 * Sinh 50 HĐ nháp map SampleDataSeeder → docs/SLMS2026_import_tenant_draft_contracts.xlsx
 * Chạy: node generate-tenant-draft-import-seed.js
 */
const path = require("path");
const XLSX = require("xlsx");

const OUT = path.join(__dirname, "SLMS2026_import_tenant_draft_contracts.xlsx");

const STREETS = [
  "Lê Lợi", "Nguyễn Huệ", "Hai Bà Trưng", "Trần Hưng Đạo", "Cách Mạng Tháng 8",
  "Nguyễn Đình Chiểu", "Pasteur", "Lý Tự Trọng", "Võ Văn Tần", "Điện Biên Phủ",
  "Nguyễn Thị Minh Khai", "Phan Xích Long", "Quang Trung", "Cộng Hòa", "Trường Chinh",
  "Lạc Long Quân", "Nguyễn Oanh", "Lê Văn Sỹ", "Hoàng Văn Thụ", "Phạm Văn Đồng",
  "Nguyễn Văn Trỗi", "Xô Viết Nghệ Tĩnh", "Phan Đăng Lưu", "Nơ Trang Long", "Bạch Đằng",
  "Đinh Tiên Hoàng", "Nguyễn Trãi", "Trần Quang Khải", "Hoàng Sa", "Trường Sa",
];

const HO = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ"];
const DEM = ["Văn", "Thị", "Hữu", "Đức", "Minh", "Ngọc", "Gia", "Quang", "Thanh", "Khánh"];
const TEN = [
  "An", "Bình", "Cường", "Dũng", "Phúc", "Giang", "Hà", "Hiếu", "Khoa", "Long",
  "Mai", "Nam", "Oanh", "Phương", "Quân", "Quỳnh", "Sơn", "Tâm", "Uyên", "Vy",
  "Xuân", "Yến", "Bảo", "Châu", "Duy", "Hải", "Khang", "Linh", "Trang", "Tú",
];

const ISSUE_PLACES = [
  "CA TP. Hồ Chí Minh", "CA Quận 1", "CA Bình Thạnh", "CA Gò Vấp", "CA Phú Nhuận",
  "CA Tân Bình", "CA Thủ Đức", "CA Quận 3", "CA Quận 7", "CA Quận 10",
];

const ADDR_STREETS = [
  "Nguyễn Huệ", "Lê Lợi", "Phạm Văn Đồng", "Quang Trung",
  "Cộng Hòa", "Hoàng Văn Thụ", "Võ Văn Ngân", "Nguyễn Văn Trỗi",
];

function fullName(idx) {
  const i = idx + 100; // lệch so với fullNameByIndex của seeder
  return `${HO[i % HO.length]} ${DEM[Math.floor(i / TEN.length) % DEM.length]} ${TEN[i % TEN.length]}`;
}

/** 0987500001..0987500050 — tránh seed 0901–0905 và mẫu cũ 090100000x */
function phone(i) {
  return "09875" + String(i).padStart(5, "0");
}

/** CCCD 12 số dải riêng */
function cccd12(i) {
  return "079186" + String(i).padStart(6, "0");
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

const headers = [
  "Mã HĐ inbound", "Mã BĐS", "Tên tòa nhà", "Loại thuê", "Số phòng",
  "Họ tên khách thuê", "CCCD", "Số điện thoại", "Ngày sinh", "Ngày cấp CCCD",
  "Nơi cấp CCCD", "Hộ khẩu thường trú", "Ngày vào ở", "Ngày kết thúc",
  "Giá thuê/tháng", "Số tháng cọc", "Tiền cọc", "Ngày đón khách dự kiến",
];

const rows = [headers];
const ref = [[
  "#", "Tên tòa nhà", "Loại", "Số phòng", "Giá thuê/tháng (theo seed)",
  "SĐT khách (import)", "Ghi chú",
]];

// 25 nhà nguyên căn
for (let i = 0; i < 25; i++) {
  const n = i + 1;
  const street = STREETS[i % STREETS.length];
  const name = `Nhà nguyên căn ${street} ${pad2(n)}`;
  const rent = 16_000_000 + (i % 6) * 4_000_000;
  const depositMonths = 1 + (i % 2);
  const phoneNum = phone(n);
  const moveIn = "2026-09-01";
  const end = "2027-08-31";
  const birthYear = 1988 + (i % 12);
  const issueYear = 2018 + (i % 6);

  rows.push([
    "",
    "",
    name,
    "NGUYEN_CAN",
    "",
    fullName(i),
    cccd12(n),
    phoneNum,
    `${birthYear}-${pad2(1 + (i % 12))}-${pad2(5 + (i % 20))}`,
    `${issueYear}-${pad2(1 + (i % 12))}-${pad2(10 + (i % 15))}`,
    ISSUE_PLACES[i % ISSUE_PLACES.length],
    `${20 + i} ${ADDR_STREETS[i % ADDR_STREETS.length]}, TP.HCM`,
    moveIn,
    end,
    rent,
    depositMonths,
    rent * depositMonths,
    moveIn,
  ]);
  ref.push([n, name, "NGUYEN_CAN", "(nguyên căn)", rent, phoneNum, "SampleDataSeeder createWholeHouse"]);
}

// 25 nhà chia phòng 01..25 — phòng P01
for (let i = 0; i < 25; i++) {
  const n = i + 1;
  const globalIdx = 25 + n;
  const street = STREETS[i % STREETS.length];
  const name = `Nhà chia phòng ${street} ${pad2(n)}`;
  const rent = 2_500_000 + (i % 6) * 700_000;
  const depositMonths = 1;
  const phoneNum = phone(globalIdx);
  const moveInMonth = 9 + (i % 3);
  const moveIn = `2026-${pad2(moveInMonth)}-15`;
  const end =
    moveInMonth === 9 ? "2027-09-14" :
    moveInMonth === 10 ? "2027-10-14" :
      "2027-11-14";
  const birthYear = 1990 + (i % 10);
  const issueYear = 2019 + (i % 5);

  rows.push([
    "",
    "",
    name,
    "THEO_PHONG",
    "P01",
    fullName(i + 25),
    cccd12(globalIdx),
    phoneNum,
    `${birthYear}-${pad2(1 + (i % 12))}-${pad2(3 + (i % 25))}`,
    `${issueYear}-${pad2(1 + (i % 10))}-${pad2(5 + (i % 20))}`,
    ISSUE_PLACES[i % ISSUE_PLACES.length],
    `${50 + i} ${ADDR_STREETS[i % ADDR_STREETS.length]}, TP.HCM`,
    moveIn,
    end,
    rent,
    depositMonths,
    rent * depositMonths,
    moveIn,
  ]);
  ref.push([
    globalIdx, name, "THEO_PHONG", "P01", rent, phoneNum,
    "SampleDataSeeder createRoomBased room P01",
  ]);
}

const guide = [
  ["Hướng dẫn import HĐ thuê nháp (DRAFT) — 50 dòng cho SampleDataSeeder"],
  [""],
  ["1. Map theo TÊN tòa đúng SampleDataSeeder (không cần Mã HĐ inbound / Mã BĐS)."],
  ["2. 25 HĐ NGUYEN_CAN: Nhà nguyên căn {đường} 01..25."],
  ["3. 25 HĐ THEO_PHONG: Nhà chia phòng {đường} 01..25 — phòng P01."],
  ["4. 5 căn Nhà chia phòng 26..30 không nằm trong file 50 dòng."],
  ["5. SĐT 0987500001..0987500050 — tránh trùng seed 0901–0905xxxxxx và mẫu cũ 090100000x."],
  ["6. CCCD 079186000001..079186000050."],
  ["7. Lưu ý: Seed đã tạo nhiều HĐ ACTIVE — import có thể lỗi chồng lấn thời gian."],
  ["   Nên ?dryRun=true trước; hoặc import trên DB chỉ có BĐS/không conflict occupancy."],
  ["8. API: POST /api/v1/import/tenant-draft-contracts-excel?dryRun=true|false"],
  ["9. Auth: ADMIN hoặc MANAGER."],
  [""],
  ["Tài khoản seed (password 123456): admin01, manager01, tenant01..tenant50"],
  ["SĐT seed: admin 0901…, owner 0902…, manager 0903…, tenant 0904…, user 0905…"],
];

const wb = XLSX.utils.book_new();

const wsGuide = XLSX.utils.aoa_to_sheet(guide);
wsGuide["!cols"] = [{ wch: 110 }];
XLSX.utils.book_append_sheet(wb, wsGuide, "0. Huong_Dan");

const wsData = XLSX.utils.aoa_to_sheet(rows);
wsData["!cols"] = headers.map((h) => ({ wch: Math.min(28, Math.max(12, h.length + 2)) }));
XLSX.utils.book_append_sheet(wb, wsData, "1. Hop_Dong_Nhap_Khach");

const wsRef = XLSX.utils.aoa_to_sheet(ref);
wsRef["!cols"] = [
  { wch: 4 }, { wch: 40 }, { wch: 12 }, { wch: 12 },
  { wch: 18 }, { wch: 14 }, { wch: 45 },
];
XLSX.utils.book_append_sheet(wb, wsRef, "0. Tham_Chieu_BDS");

XLSX.writeFile(wb, OUT);

const data = rows.slice(1);
const phones = data.map((r) => r[7]);
const seedPhones = [];
for (let i = 1; i <= 2; i++) seedPhones.push("0901" + String(i).padStart(6, "0"));
for (let i = 1; i <= 6; i++) seedPhones.push("0902" + String(i).padStart(6, "0"));
for (let i = 1; i <= 5; i++) seedPhones.push("0903" + String(i).padStart(6, "0"));
for (let i = 1; i <= 50; i++) seedPhones.push("0904" + String(i).padStart(6, "0"));
for (let i = 1; i <= 5; i++) seedPhones.push("0905" + String(i).padStart(6, "0"));

console.log("Wrote", OUT);
console.log("contracts", data.length);
console.log("unique phones", new Set(phones).size);
console.log("seed collisions", phones.filter((p) => seedPhones.includes(p)).length);
console.log("range", phones[0], "...", phones[49]);
