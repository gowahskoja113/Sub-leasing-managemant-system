/**
 * Sinh dữ liệu demo nhiều căn nhà cho SLMS2026_v2.xlsx
 * Chạy: node generate-import-data.js
 */
const path = require("path");
const XLSX = require("xlsx");

const TEMPLATE = path.join(__dirname, "SLMS2026_v2.xlsx");
const OUTPUT = path.join(__dirname, "SLMS2026_v2_nhieu_can_nha.xlsx");

const LEASE_SHEET = "1. Hop_Dong_Thue";
const RENO_SHEET = "2. Hop_Dong_Cai_Tao";
const EQUIP_SHEET = "3. Phan_Bo_Thiet_Bi";

const leases = [
  {
    code: "HD-HCM-WH-RENO-01",
    name: "Villa Thảo Điền View",
    address: "12 Nguyễn Văn Hưởng",
    district: "Quận 3",
    province: "TP. Hồ Chí Minh",
    area: 180,
    floors: 2,
    rooms: 5,
    owner: "Trần Minh Tuấn",
    rent: 450_000_000,
    start: "2026-03-01",
    end: "2029-02-28",
    leaseType: "WHOLE_HOUSE",
    renovation: "TRUE",
    contingency: 5,
    desc: "Nhà phố 2 tầng, sân sau rộng, cần sơn sửa và nâng cấp điện nước.",
  },
  {
    code: "HD-HCM-ROOM-RENO-01",
    name: "Nhà trọ Bình Thạnh",
    address: "45 Phan Văn Trị",
    district: "Bình Thạnh",
    province: "TP. Hồ Chí Minh",
    area: 120,
    floors: 3,
    rooms: 8,
    owner: "Lê Thị Hương",
    rent: 320_000_000,
    start: "2026-04-01",
    end: "2028-03-31",
    leaseType: "INDIVIDUAL_ROOM",
    renovation: "TRUE",
    contingency: 4,
    desc: "8 phòng cho thuê, mỗi phòng có WC riêng, cần cải tạo sàn và nội thất.",
  },
  {
    code: "HD-HCM-WH-NORENO-01",
    name: "Nhà Gò Vấp",
    address: "88 Quang Trung",
    district: "Gò Vấp",
    province: "TP. Hồ Chí Minh",
    area: 95,
    floors: 2,
    rooms: 4,
    owner: "Phạm Văn Đức",
    rent: 280_000_000,
    start: "2026-05-01",
    end: "2031-04-30",
    leaseType: "WHOLE_HOUSE",
    renovation: "FALSE",
    contingency: "",
    desc: "Nhà đã hoàn thiện, vào ở ngay, không cần cải tạo.",
  },
  {
    code: "HD-HCM-ROOM-NORENO-01",
    name: "Căn hộ mini Phú Nhuận",
    address: "23 Hoàng Văn Thụ",
    district: "Phú Nhuận",
    province: "TP. Hồ Chí Minh",
    area: 75,
    floors: 1,
    rooms: 6,
    owner: "Nguyễn Thu Hà",
    rent: 240_000_000,
    start: "2026-06-01",
    end: "2027-05-31",
    leaseType: "INDIVIDUAL_ROOM",
    renovation: "FALSE",
    contingency: "",
    desc: "Gần sân bay, phù hợp sinh viên, thiết bị đầy đủ.",
  },
  {
    code: "HD-HN-WH-RENO-01",
    name: "Vinhomes Villa Cầu Giấy",
    address: "Số 18, Biệt thự 2",
    district: "Cầu Giấy",
    province: "Hà Nội",
    area: 250.5,
    floors: 3,
    rooms: 6,
    owner: "Nguyễn Văn A",
    rent: 600_000_000,
    start: "2026-06-01",
    end: "2031-05-31",
    leaseType: "WHOLE_HOUSE",
    renovation: "TRUE",
    contingency: 5,
    desc: "Biệt thự sân vườn rộng, có gara ô tô.",
  },
  {
    code: "HD-HN-ROOM-RENO-01",
    name: "Nhà trọ sinh viên Cầu Giấy",
    address: "24 Xuân Thủy",
    district: "Cầu Giấy",
    province: "Hà Nội",
    area: 110,
    floors: 4,
    rooms: 10,
    owner: "Hoàng Văn Nam",
    rent: 380_000_000,
    start: "2026-07-01",
    end: "2029-06-30",
    leaseType: "INDIVIDUAL_ROOM",
    renovation: "TRUE",
    contingency: 6,
    desc: "Gần ĐHQG, 10 phòng, cần sơn sửa và lắp điều hòa mới.",
  },
  {
    code: "HD-HN-WH-NORENO-01",
    name: "Nhà phố Hà Nội",
    address: "56 Pháo Đài Láng",
    district: "Cầu Giấy",
    province: "Hà Nội",
    area: 85,
    floors: 2,
    rooms: 3,
    owner: "Vũ Thanh Tùng",
    rent: 350_000_000,
    start: "2026-08-01",
    end: "2030-07-31",
    leaseType: "WHOLE_HOUSE",
    renovation: "FALSE",
    contingency: "",
    desc: "Mặt phố view hồ, nội thất cơ bản đã có.",
  },
  {
    code: "HD-HCM-WH-RENO-02",
    name: "Penthouse Quận 1",
    address: "101 Lê Lợi",
    district: "Quận 1",
    province: "TP. Hồ Chí Minh",
    area: 200,
    floors: 1,
    rooms: 4,
    owner: "Đặng Quốc Bảo",
    rent: 550_000_000,
    start: "2026-09-01",
    end: "2031-08-31",
    leaseType: "WHOLE_HOUSE",
    renovation: "TRUE",
    contingency: 5,
    desc: "Căn penthouse trung tâm, cải tạo nội thất cao cấp.",
  },
];

const renovations = [
  { code: "HD-HCM-WH-RENO-01", cat: "PAINTING", name: "Sơn sửa", cost: 18_000_000, note: "Sơn lại toàn bộ mặt ngoài và trong nhà" },
  { code: "HD-HCM-WH-RENO-01", cat: "PLUMBING", name: "Điện nước", cost: 22_000_000, note: "Thay đường ống nước tầng 2" },
  { code: "HD-HCM-ROOM-RENO-01", cat: "FLOORING", name: "Sàn nhà", cost: 35_000_000, note: "Lát gạch phòng 101–104" },
  { code: "HD-HCM-ROOM-RENO-01", cat: "FURNITURE", name: "Nội thất", cost: 28_000_000, note: "Mua giường tủ cho 4 phòng" },
  { code: "HD-HN-WH-RENO-01", cat: "PAINTING", name: "Sơn sửa", cost: 25_000_000, note: "Sơn chống thấm mặt ngoài biệt thự" },
  { code: "HD-HN-WH-RENO-01", cat: "FLOORING", name: "Sàn nhà", cost: 40_000_000, note: "Lát sàn gỗ công nghiệp 3 phòng ngủ tầng 2" },
  { code: "HD-HN-ROOM-RENO-01", cat: "PAINTING", name: "Sơn sửa", cost: 15_000_000, note: "Sơn lại hành lang và 10 phòng" },
  { code: "HD-HN-ROOM-RENO-01", cat: "EQUIPMENT", name: "Thiết bị mua thêm", cost: 45_000_000, note: "Mua điều hòa cho 10 phòng" },
  { code: "HD-HCM-WH-RENO-02", cat: "FURNITURE", name: "Nội thất", cost: 80_000_000, note: "Nội thất cao cấp phòng khách và bếp" },
  { code: "HD-HCM-WH-RENO-02", cat: "PLUMBING", name: "Điện nước", cost: 30_000_000, note: "Nâng cấp hệ thống điện smart home" },
];

const equipment = [
  { code: "HD-HCM-WH-RENO-01", room: "101", area: "", catalog: "Điều hòa", source: "INITIAL_HANDOVER", status: "GOOD", qty: 2, price: 0, note: "Điều hòa có sẵn tầng 1" },
  { code: "HD-HCM-WH-RENO-01", room: "", area: "LIVING_ROOM", catalog: "Tủ lạnh", source: "PURCHASED", status: "NEW", qty: 1, price: 12_000_000, note: "Mua mới cho phòng khách" },
  { code: "HD-HCM-ROOM-RENO-01", room: "101", area: "", catalog: "Điều hòa", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "Máy lạnh cũ còn dùng được" },
  { code: "HD-HCM-ROOM-RENO-01", room: "102", area: "", catalog: "Giường", source: "PURCHASED", status: "NEW", qty: 1, price: 4_500_000, note: "Giường 1m6 kèm nệm" },
  { code: "HD-HCM-ROOM-RENO-01", room: "103", area: "", catalog: "Tủ quần áo", source: "PURCHASED", status: "NEW", qty: 1, price: 3_200_000, note: "Tủ gỗ công nghiệp" },
  { code: "HD-HCM-WH-NORENO-01", room: "101", area: "", catalog: "Điều hòa", source: "INITIAL_HANDOVER", status: "GOOD", qty: 3, price: 0, note: "Mỗi phòng 1 máy lạnh" },
  { code: "HD-HCM-WH-NORENO-01", room: "", area: "LIVING_ROOM", catalog: "Máy giặt", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "Máy giặt sân sau" },
  { code: "HD-HCM-ROOM-NORENO-01", room: "101", area: "", catalog: "Điều hòa", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "" },
  { code: "HD-HCM-ROOM-NORENO-01", room: "102", area: "", catalog: "Nóng lạnh", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "Máy nước nóng phòng 102" },
  { code: "HD-HN-WH-RENO-01", room: "101", area: "", catalog: "Điều hòa", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "Máy lạnh có sẵn tại phòng 101" },
  { code: "HD-HN-WH-RENO-01", room: "201", area: "", catalog: "Giường", source: "PURCHASED", status: "NEW", qty: 1, price: 7_500_000, note: "Mua mới cho phòng ngủ Master" },
  { code: "HD-HN-WH-RENO-01", room: "", area: "LIVING_ROOM", catalog: "Điều hòa", source: "PURCHASED", status: "NEW", qty: 1, price: 16_500_000, note: "Lắp đặt tại phòng khách tầng 1" },
  { code: "HD-HN-ROOM-RENO-01", room: "101", area: "", catalog: "Quạt", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "Quạt trần có sẵn" },
  { code: "HD-HN-ROOM-RENO-01", room: "102", area: "", catalog: "Điều hòa", source: "PURCHASED", status: "NEW", qty: 1, price: 8_000_000, note: "Lắp mới phòng 102" },
  { code: "HD-HN-ROOM-RENO-01", room: "103", area: "", catalog: "Giường", source: "PURCHASED", status: "NEW", qty: 1, price: 3_800_000, note: "" },
  { code: "HD-HN-WH-NORENO-01", room: "101", area: "", catalog: "Bếp từ", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "Bếp từ tầng 1" },
  { code: "HD-HN-WH-NORENO-01", room: "", area: "LIVING_ROOM", catalog: "Bàn ăn", source: "INITIAL_HANDOVER", status: "GOOD", qty: 1, price: 0, note: "" },
  { code: "HD-HCM-WH-RENO-02", room: "", area: "LIVING_ROOM", catalog: "Điều hòa", source: "PURCHASED", status: "NEW", qty: 2, price: 18_000_000, note: "Điều hòa multi cho phòng khách lớn" },
  { code: "HD-HCM-WH-RENO-02", room: "201", area: "", catalog: "Tủ lạnh", source: "PURCHASED", status: "NEW", qty: 1, price: 22_000_000, note: "Tủ lạnh side-by-side" },
  { code: "HD-HCM-WH-RENO-02", room: "201", area: "", catalog: "Máy giặt", source: "PURCHASED", status: "NEW", qty: 1, price: 15_000_000, note: "Máy giặt sấy tích hợp" },
];

function leaseRow(l) {
  return [
    l.code, l.name, l.address, l.district, l.province,
    l.area, l.floors, l.rooms, l.owner, l.rent,
    l.start, l.end, l.leaseType, l.renovation, l.contingency, l.desc,
  ];
}

function renoRow(r) {
  return [r.code, r.cat, r.name, r.cost, r.note];
}

function equipRow(e) {
  return [e.code, e.room, e.area, e.catalog, e.source, e.status, e.qty, e.price, e.note];
}

function fillSheet(wb, sheetName, headerRow, dataRows) {
  const ws = wb.Sheets[sheetName];
  const aoa = [headerRow, ...dataRows];
  const newWs = XLSX.utils.aoa_to_sheet(aoa);
  // Giữ độ rộng cột; không pad thêm hàng trống (tránh file phình ~1MB+ vượt giới hạn upload)
  if (ws["!cols"]) newWs["!cols"] = ws["!cols"];
  wb.Sheets[sheetName] = newWs;
}

const wb = XLSX.readFile(TEMPLATE);

const leaseWs = wb.Sheets[LEASE_SHEET];
const leaseHeader = XLSX.utils.sheet_to_json(leaseWs, { header: 1, defval: "" })[0];
fillSheet(wb, LEASE_SHEET, leaseHeader, leases.map(leaseRow));

const renoWs = wb.Sheets[RENO_SHEET];
const renoHeader = XLSX.utils.sheet_to_json(renoWs, { header: 1, defval: "" })[0];
fillSheet(wb, RENO_SHEET, renoHeader, renovations.map(renoRow));

const equipWs = wb.Sheets[EQUIP_SHEET];
const equipHeader = XLSX.utils.sheet_to_json(equipWs, { header: 1, defval: "" })[0];
fillSheet(wb, EQUIP_SHEET, equipHeader, equipment.map(equipRow));

XLSX.writeFile(wb, OUTPUT);

console.log(`Đã tạo: ${OUTPUT}`);
console.log(`  - ${leases.length} hợp đồng thuê`);
console.log(`  - ${renovations.length} dòng cải tạo`);
console.log(`  - ${equipment.length} dòng thiết bị`);
