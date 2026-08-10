/**
 * Sinh ~50 BĐS (ma trận onboarding) vào:
 *   - SLMS2026_import_matrix_dot1.xlsx  (đợt 1)
 *   - SLMS2026_import_matrix_dot2.xlsx  (đợt 2 — chỉ RENO)
 * và 25 HĐ nháp map tenant01..25 → 25 căn/phòng đợt 1:
 *   - SLMS2026_import_tenant_draft_contracts.xlsx
 *
 * Zone chỉ dùng quận có trong ZoneDataSeeder (tránh lỗi resolve zone khi import).
 * Chạy: node generate-matrix-50.js
 */
const path = require("path");
const XLSX = require("xlsx");

const DOT1 = path.join(__dirname, "SLMS2026_import_matrix_dot1.xlsx");
const DOT2 = path.join(__dirname, "SLMS2026_import_matrix_dot2.xlsx");
const DRAFT = path.join(__dirname, "SLMS2026_import_tenant_draft_contracts.xlsx");

// ZoneDataSeeder: HCM 5 quận + HN Cầu Giấy
const DISTRICTS = [
  { district: "Quận 1", province: "TP. Hồ Chí Minh" },
  { district: "Quận 3", province: "TP. Hồ Chí Minh" },
  { district: "Bình Thạnh", province: "TP. Hồ Chí Minh" },
  { district: "Gò Vấp", province: "TP. Hồ Chí Minh" },
  { district: "Phú Nhuận", province: "TP. Hồ Chí Minh" },
  { district: "Cầu Giấy", province: "Hà Nội" },
];

const OWNERS = [
  "Nguyễn Văn A", "Trần Thị B", "Lê Văn C", "Phạm Thị D", "Hoàng Văn E",
  "Võ Thị F", "Đặng Văn G", "Bùi Thị H", "Ngô Văn I", "Dương Thị K",
  "Lý Văn L", "Mai Thị M", "Phan Văn N", "Trịnh Thị O", "Huỳnh Văn P",
];

const HO = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ"];
const DEM = ["Văn", "Thị", "Hữu", "Đức", "Minh", "Ngọc", "Gia", "Quang", "Thanh", "Khánh"];
const TEN = [
  "An", "Bình", "Cường", "Dũng", "Phúc", "Giang", "Hà", "Hiếu", "Khoa", "Long",
  "Mai", "Nam", "Oanh", "Phương", "Quân", "Quỳnh", "Sơn", "Tâm", "Uyên", "Vy",
  "Xuân", "Yến", "Bảo", "Châu", "Duy", "Hải", "Khang", "Linh", "Trang", "Tú",
];

/** Đồng bộ SampleDataSeeder.fullNameByIndex */
function fullNameByIndex(idx) {
  const ho = HO[idx % HO.length];
  const dem = DEM[Math.floor(idx / TEN.length) % DEM.length];
  const ten = TEN[idx % TEN.length];
  const round = Math.floor(idx / (HO.length * TEN.length));
  return round === 0 ? `${ho} ${dem} ${ten}` : `${ho} ${dem} ${ten} ${round}`;
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function ymd(y, m, d) {
  return `${y}-${pad2(m)}-${pad2(d)}`;
}

function addMonths(y, m, d, add) {
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCMonth(dt.getUTCMonth() + add);
  return ymd(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
}

/**
 * 50 căn — cover:
 *  - Nguyên căn: full NT / NT cơ bản / không NT
 *  - Theo phòng: NT cơ bản / không NT (+ vài full)
 *  - RENO vs NORENO, có/không TB bàn giao, có/không TB mua đợt 2
 */
function buildProperties() {
  const props = [];
  let stt = 0;

  /**
   * @param {object} p
   * @param {'NORENO'|'RENO'} p.mode
   * @param {'WH'|'RM'} p.shape  WH = nguyên căn, RM = theo phòng (chỉ RENO)
   * @param {'NONE'|'BASIC'|'FULL'} p.furn
   * @param {boolean} [p.handover] TB bàn giao đợt 1
   * @param {boolean} [p.buyEquip] TB mua đợt 2 (chỉ RENO)
   * @param {number} [p.rooms] số phòng khai thác (THEO_PHONG)
   * @param {string} [p.tag] hậu tố mã
   * @param {string} [p.label]
   */
  function add(p) {
    stt += 1;
    const n = stt;
    const mode = p.mode;
    const shape = p.shape;
    const furn = p.furn;
    const handover = !!p.handover;
    const buyEquip = mode === "RENO" && !!p.buyEquip;
    const rooms = shape === "RM" ? (p.rooms || 3) : (p.rooms || 2 + (n % 3));
    const tag =
      p.tag ||
      [
        mode,
        shape === "WH" ? "WH" : "RM",
        furn,
        handover ? "HO" : "NOHO",
        buyEquip ? "BUY" : "NOBUY",
      ].join("-");

    const code = `HD-MTX-${pad2(n)}-${tag}`;
    const zone = DISTRICTS[(n - 1) % DISTRICTS.length];
    const owner = OWNERS[(n - 1) % OWNERS.length];
    const floors = shape === "RM" ? 2 + (n % 2) : 1 + (n % 2);
    const area = shape === "RM" ? 70 + rooms * 12 + (n % 5) * 3 : 55 + rooms * 15 + (n % 7) * 4;
    const width = Math.round((5.5 + (n % 5) * 0.4) * 10) / 10;
    const length = Math.round((area / width) * 10) / 10;
    const startY = 2026;
    const startM = 3 + ((n - 1) % 8);
    const start = ymd(startY, startM, 1);
    const end = addMonths(startY, startM, 1, 24 + (n % 12));
    const rent = shape === "RM"
      ? 180_000_000 + rooms * 12_000_000 + (n % 6) * 5_000_000
      : 150_000_000 + rooms * 25_000_000 + (n % 8) * 8_000_000;

    const furnLabel =
      furn === "FULL" ? "full nội thất" : furn === "BASIC" ? "nội thất cơ bản" : "không nội thất";
    const shapeLabel = shape === "WH" ? "nguyên căn" : `theo phòng (${rooms}p)`;
    const name = `MTX#${pad2(n)} ${shape === "WH" ? "NGUYEN_CAN" : "THEO_PHONG"} ${furn}`;
    const address = `${n} Ma Trận ${zone.district}`;
    const desc =
      `Ma trận #${n} — ${mode} | ${shapeLabel} | ${furnLabel}` +
      ` | TB bàn giao: ${handover ? "Có" : "Không"}` +
      (mode === "RENO" ? ` | TB mua đ2: ${buyEquip ? "Có" : "Không"}` : " | không đợt 2");

    // Monthly rent gợi ý cho HĐ nháp khách (không dùng tổng tiền thuê master)
    const monthlyRent =
      shape === "WH"
        ? 12_000_000 + (n % 8) * 2_000_000
        : 2_500_000 + (n % 6) * 400_000;

    props.push({
      stt: n,
      code,
      mode,
      shape,
      furn,
      handover,
      buyEquip,
      rooms,
      name,
      address,
      district: zone.district,
      province: zone.province,
      area,
      length,
      width,
      floors,
      totalRooms: shape === "RM" ? rooms + 1 : rooms,
      owner,
      rent,
      monthlyRent,
      start,
      end,
      desc,
      furnLabel,
      shapeLabel,
    });
  }

  // —— NORENO nguyên căn (đợt 1 xong → Host, không file đợt 2) ——
  for (let i = 0; i < 5; i++) add({ mode: "NORENO", shape: "WH", furn: "NONE", handover: false, tag: "NORENO-WH-NONE" });
  for (let i = 0; i < 5; i++) add({ mode: "NORENO", shape: "WH", furn: "BASIC", handover: true, tag: "NORENO-WH-BASIC" });
  for (let i = 0; i < 5; i++) add({ mode: "NORENO", shape: "WH", furn: "FULL", handover: true, tag: "NORENO-WH-FULL" });

  // —— RENO nguyên căn ——
  for (let i = 0; i < 3; i++) add({ mode: "RENO", shape: "WH", furn: "NONE", handover: false, buyEquip: false, tag: "RENO-WH-NONE" });
  for (let i = 0; i < 3; i++) add({ mode: "RENO", shape: "WH", furn: "BASIC", handover: true, buyEquip: false, tag: "RENO-WH-BASIC-HO" });
  for (let i = 0; i < 3; i++) add({ mode: "RENO", shape: "WH", furn: "BASIC", handover: false, buyEquip: true, tag: "RENO-WH-BASIC-BUY" });
  for (let i = 0; i < 3; i++) add({ mode: "RENO", shape: "WH", furn: "FULL", handover: true, buyEquip: true, tag: "RENO-WH-FULL" });
  // combo ma trận còn lại
  for (let i = 0; i < 2; i++) add({ mode: "RENO", shape: "WH", furn: "NONE", handover: true, buyEquip: false, tag: "RENO-WH-HO-NOBUY" });
  for (let i = 0; i < 2; i++) add({ mode: "RENO", shape: "WH", furn: "BASIC", handover: true, buyEquip: true, tag: "RENO-WH-HO-BUY" });

  // —— RENO theo phòng ——
  for (let i = 0; i < 4; i++) add({ mode: "RENO", shape: "RM", furn: "NONE", rooms: 3, handover: false, buyEquip: false, tag: "RENO-RM-NONE" });
  for (let i = 0; i < 4; i++) add({ mode: "RENO", shape: "RM", furn: "BASIC", rooms: 3, handover: true, buyEquip: false, tag: "RENO-RM-BASIC-HO" });
  for (let i = 0; i < 4; i++) add({ mode: "RENO", shape: "RM", furn: "BASIC", rooms: 3 + (i % 2), handover: false, buyEquip: true, tag: "RENO-RM-BASIC-BUY" });
  for (let i = 0; i < 4; i++) add({ mode: "RENO", shape: "RM", furn: "FULL", rooms: 4, handover: true, buyEquip: true, tag: "RENO-RM-FULL" });
  for (let i = 0; i < 3; i++) add({ mode: "RENO", shape: "RM", furn: "BASIC", rooms: 3, handover: true, buyEquip: true, tag: "RENO-RM-HO-BUY" });

  // stt should be 50: 15 + 16 + 19 = 50
  if (props.length !== 50) {
    throw new Error(`Expected 50 properties, got ${props.length}`);
  }
  return props;
}

// ─── Handover (đợt 1) ─────────────────────────────────────────
function handoverRows(p) {
  if (!p.handover) return [];
  const rows = [];
  const c = p.code;

  if (p.furn === "BASIC") {
    if (p.shape === "WH") {
      rows.push([c, "Điều hòa", "Máy 1.5HP", "Phòng khách", "GOOD", 1, "NT cơ bản"]);
      rows.push([c, "Giường", "Giường gỗ 1m6", "Phòng ngủ", "GOOD", 1, ""]);
    } else {
      rows.push([c, "Giường", "Giường sắt cũ", "Tầng 2", "GOOD", Math.min(2, p.rooms), "TB chủ"]);
      if (p.stt % 2 === 0) rows.push([c, "Quạt", "Quạt trần", "Hành lang", "GOOD", 1, ""]);
    }
  } else if (p.furn === "FULL") {
    if (p.shape === "WH") {
      rows.push([c, "Điều hòa", "1.5HP Inverter", "Phòng khách", "GOOD", 1, ""]);
      rows.push([c, "Điều hòa", "1HP", "Phòng ngủ 1", "GOOD", 1, ""]);
      rows.push([c, "Tủ lạnh", "Inverter 200L", "Bếp", "GOOD", 1, ""]);
      rows.push([c, "Máy giặt", "Cửa trước 8kg", "Sân sau", "GOOD", 1, ""]);
      rows.push([c, "Nóng lạnh", "15L", "WC tầng 1", "GOOD", 1, ""]);
      rows.push([c, "Giường", "Giường gỗ 1m6", "Phòng ngủ 1", "GOOD", 1, ""]);
      rows.push([c, "Giường", "Giường gỗ 1m6", "Phòng ngủ 2", "GOOD", 1, ""]);
      rows.push([c, "Quạt", "Quạt trần", "Phòng khách", "GOOD", 1, ""]);
      rows.push([c, "Quạt", "Quạt đứng", "Phòng ngủ 1", "GOOD", 1, ""]);
    } else {
      rows.push([c, "Máy giặt", "Máy cũ chung", "Sân phơi", "GOOD", 1, "TB dùng chung"]);
      rows.push([c, "Tủ lạnh", "Tủ cũ tầng 1", "Bếp chung", "GOOD", 1, ""]);
    }
  }
  return rows;
}

// ─── Đợt 2 rows ───────────────────────────────────────────────
function configRow(p) {
  if (p.mode !== "RENO") return null;
  if (p.shape === "WH") return [p.code, "NGUYEN_CAN", ""];
  return [p.code, "THEO_PHONG", p.rooms];
}

function roomRows(p) {
  if (p.mode !== "RENO" || p.shape !== "RM") return [];
  const out = [];
  const roomArea = Math.round((p.area / p.rooms) * 10) / 10;
  const w = Math.round((4 + (p.stt % 3) * 0.2) * 10) / 10;
  const l = Math.round((roomArea / w) * 10) / 10;
  for (let i = 1; i <= p.rooms; i++) {
    const num = String(100 + i);
    const floor = i <= Math.ceil(p.rooms / 2) ? 1 : 2;
    out.push([p.code, num, floor, roomArea, l, w, `Phòng ${num}`]);
  }
  return out;
}

function renoRows(p) {
  if (p.mode !== "RENO") return [];
  const c = p.code;
  const base = 7_000_000 + (p.stt % 5) * 1_500_000;
  const rows = [[c, "PAINTING", "Sơn sửa", base, p.shape === "RM" ? `Sơn ${p.rooms} phòng` : "Sơn lại toàn bộ"]];
  if (p.furn === "FULL" || p.buyEquip) {
    rows.push([c, "PLUMBING", "Điện nước", base + 2_000_000, "Hoàn thiện điện nước"]);
  }
  if (p.furn === "FULL") {
    rows.push([c, "FURNITURE", "Nội thất", base + 3_000_000, "Lắp đặt nội thất"]);
  }
  if (p.shape === "RM" && p.stt % 3 === 0) {
    rows.push([c, "FLOORING", "Sàn nhà", base + 1_000_000, "Lát gạch từng phòng"]);
  }
  return rows;
}

function buyEquipRows(p) {
  if (p.mode !== "RENO" || !p.buyEquip) return [];
  const c = p.code;
  const start = p.start;
  const end = addMonths(+p.start.slice(0, 4), +p.start.slice(5, 7), 1, 24);
  const out = [];

  function equip(room, area, name, price, months, note) {
    const pen = Math.round(price * 0.3);
    const eEnd = addMonths(+start.slice(0, 4), +start.slice(5, 7), 1, months);
    out.push([c, room, area, name, "NEW", "THEM_MOI", 1, price, months, start, eEnd, pen, note || ""]);
  }

  if (p.shape === "WH") {
    if (p.furn === "BASIC") {
      equip("", "LIVING_ROOM", "Điều hòa", 11_000_000, 24, "ĐH phòng khách");
      equip("", "KITCHEN", "Tủ lạnh", 7_500_000, 24, "");
    } else if (p.furn === "FULL") {
      equip("", "LIVING_ROOM", "Điều hòa", 14_000_000, 36, "");
      equip("", "LIVING_ROOM", "Quạt", 1_200_000, 12, "Quạt trần");
      equip("", "LIVING_ROOM", "Giường", 5_500_000, 12, "Giường PN chính");
      equip("", "LIVING_ROOM", "Giường", 4_800_000, 12, "Giường PN 2");
      equip("", "KITCHEN", "Tủ lạnh", 8_500_000, 24, "");
      equip("", "KITCHEN", "Máy giặt", 9_500_000, 24, "");
      equip("", "BATHROOM", "Nóng lạnh", 3_200_000, 12, "");
      equip("", "BALCONY", "Quạt", 650_000, 12, "");
    } else {
      // NONE but buy some equipment (case ma trận #4 style)
      equip("", "LIVING_ROOM", "Điều hòa", 11_000_000, 24, "");
    }
  } else {
    for (let i = 1; i <= p.rooms; i++) {
      const num = String(100 + i);
      if (p.furn === "NONE") {
        // no room equip when NONE unless buy — still put minimal? skip NONE without items
        continue;
      }
      if (p.furn === "BASIC") {
        equip(num, "", "Giường", 4_000_000, 12, `NT cơ bản phòng ${num}`);
        if (i % 2 === 1) equip(num, "", "Quạt", 750_000, 12, `Quạt phòng ${num}`);
        else equip(num, "", "Điều hòa", 8_500_000, 24, `ĐH phòng ${num}`);
      } else {
        // FULL
        equip(num, "", "Giường", 4_500_000, 12, `Giường phòng ${num}`);
        equip(num, "", "Quạt", 800_000, 12, `Quạt phòng ${num}`);
        equip(num, "", "Điều hòa", 9_200_000, 24, `ĐH phòng ${num}`);
        equip(num, "", "Nóng lạnh", 2_600_000, 12, `NL phòng ${num}`);
      }
    }
    // None + buyEquip: 1 item per room minimal (bed only = basic buy path already)
    if (p.furn === "NONE") {
      // Ma trận: RENO + không HO + có buy — 1 item/room or common area
      equip(String(101), "", "Giường", 3_500_000, 12, "Mua bổ sung tối thiểu");
    }
  }
  return out;
}

function matrixGuideRows(props) {
  const header = [
    "STT", "Mã hợp đồng", "RENO/NORENO", "TB bàn giao đ1", "Hình thức đ2",
    "Cải tạo đ2", "TB mua đ2", "File đợt 2", "Diễn giải",
  ];
  const rows = [header];
  for (const p of props) {
    const form =
      p.mode === "NORENO" ? "—" : p.shape === "WH" ? "NGUYEN_CAN" : `THEO_PHONG (${p.rooms})`;
    rows.push([
      p.stt,
      p.code,
      p.mode,
      p.handover ? "Có" : "Không",
      form,
      p.mode === "RENO" ? "Có (≥1 dòng)" : "—",
      p.mode === "RENO" ? (p.buyEquip ? "Có" : "Không") : "—",
      p.mode === "RENO" ? "Có" : "Không",
      `#${p.stt} ${p.mode} | ${p.shapeLabel} | ${p.furnLabel} | TB bàn giao: ${p.handover ? "Có" : "Không"}`,
    ]);
  }
  rows.push([]);
  rows.push([`Tổng: ${props.length} HĐ onboarding (nguyên căn full/cơ bản/không NT + theo phòng cơ bản/không NT).`]);
  rows.push(["NORENO: import đợt 1 xong → tự gửi Host. RENO: cần import đợt 2."]);
  rows.push(["Zone chỉ gồm quận trong ZoneDataSeeder: Q1, Q3, Bình Thạnh, Gò Vấp, Phú Nhuận, Cầu Giấy."]);
  rows.push(["Catalog thiết bị / hạng mục cải tạo: MasterDataSeeder."]);
  return rows;
}

function writeDot1(props) {
  const leaseHeader = [
    "Mã hợp đồng", "Tên tòa nhà", "Địa chỉ chi tiết", "Quận/Huyện", "Tỉnh/Thành phố",
    "Diện tích (m²)", "Chiều dài (m)", "Chiều rộng (m)", "Tổng số tầng", "Tổng số phòng",
    "Tên chủ nhà", "Tổng tiền thuê", "Ngày bắt đầu", "Ngày kết thúc", "Mô tả chi tiết",
  ];
  const leaseData = props.map((p) => [
    p.code, p.name, p.address, p.district, p.province,
    p.area, p.length, p.width, p.floors, p.totalRooms,
    p.owner, p.rent, p.start, p.end, p.desc,
  ]);

  const hoHeader = [
    "Mã hợp đồng thuê", "Tên thiết bị", "Mô tả chi tiết", "Mô tả vị trí",
    "Trạng thái thiết bị", "Số lượng", "Ghi chú",
  ];
  const hoData = props.flatMap(handoverRows);

  const cat = [
    ["Tên thiết bị (catalog)", "Mô tả"],
    ["Điều hòa", "Máy lạnh / điều hòa không khí"],
    ["Tủ lạnh", "Tủ lạnh các loại"],
    ["Máy giặt", "Máy giặt cửa trước / cửa trên"],
    ["Giường", "Giường ngủ các loại"],
    ["Nóng lạnh", "Máy nước nóng"],
    ["Quạt", "Quạt điện / quạt trần"],
    [],
    ["Mã khu vực", "Mô tả"],
    ["LIVING_ROOM", "Phòng khách"],
    ["KITCHEN", "Nhà bếp"],
    ["BATHROOM", "Phòng tắm / WC"],
    ["BALCONY", "Ban công"],
  ];

  const noreno = props.filter((p) => p.mode === "NORENO").length;
  const reno = props.length - noreno;
  const guide = [
    [`Hướng dẫn — Ma trận onboarding ${props.length} căn (đợt 1)`],
    [""],
    [`File cover ${props.length} HĐ: nguyên căn (full / cơ bản / không NT) + theo phòng (cơ bản / không NT / full).`],
    [`Sheet "0. Ma_Tran_Onboarding": tra cứu STT.`],
    ["API: POST /api/v1/import/lease-excel?dryRun="],
    [""],
    [`NORENO (${noreno} HĐ): sau đợt 1 tự gửi Host — KHÔNG import file đợt 2.`],
    [`RENO (${reno} HĐ): sau đợt 1 ở UNDER_RENOVATION — cần file đợt 2.`],
    ["Zone: chỉ quận đã seed (Q1, Q3, BT, GV, PN, Cầu Giấy)."],
  ];

  const wb = XLSX.utils.book_new();
  wb.SheetNames = [];
  wb.Sheets = {};

  const add = (name, aoa, colW) => {
    const ws = XLSX.utils.aoa_to_sheet(aoa);
    if (colW) ws["!cols"] = colW.map((w) => ({ wch: w }));
    XLSX.utils.book_append_sheet(wb, ws, name);
  };

  add("0. Huong_Dan", guide, [110]);
  add("0. Ma_Tran_Onboarding", matrixGuideRows(props), [5, 36, 12, 14, 16, 12, 10, 10, 60]);
  add("0. Danh_Muc_Tham_Khao", cat, [28, 40]);
  add("1. Hop_Dong_Thue", [leaseHeader, ...leaseData], leaseHeader.map((h) => Math.min(22, Math.max(12, h.length + 2))));
  add("2. Thiet_Bi_Ban_Giao", [hoHeader, ...hoData], [28, 14, 18, 16, 16, 10, 18]);

  XLSX.writeFile(wb, DOT1);
  console.log("Wrote", DOT1, "leases", leaseData.length, "handover", hoData.length);
}

function writeDot2(props) {
  const renoProps = props.filter((p) => p.mode === "RENO");

  const cfgH = ["Mã hợp đồng thuê", "Hình thức khai thác", "Số phòng khai thác"];
  const roomH = ["Mã hợp đồng thuê", "Số phòng", "Tầng", "Diện tích phòng (m²)", "Chiều dài (m)", "Chiều rộng (m)", "Ghi chú"];
  const renoH = ["Mã hợp đồng thuê", "Mã danh mục cải tạo", "Tên danh mục (Gợi ý)", "Chi phí cải tạo (VNĐ)", "Ghi chú chi tiết"];
  const buyH = [
    "Mã hợp đồng thuê", "Số phòng", "Khu vực chung", "Tên Catalog thiết bị", "Trạng thái thiết bị",
    "Hành động", "Số lượng", "Đơn giá (VNĐ)", "Số tháng bảo hành", "Ngày bắt đầu bảo hành",
    "Ngày hết bảo hành", "Giá phạt hết bảo hành (VNĐ)", "Ghi chú lắp đặt",
  ];

  const cfg = renoProps.map(configRow).filter(Boolean);
  const rooms = renoProps.flatMap(roomRows);
  const renos = renoProps.flatMap(renoRows);
  const buys = renoProps.flatMap(buyEquipRows);

  const cat = [
    ["Mã danh mục", "Tên danh mục", "Mô tả"],
    ["PAINTING", "Sơn sửa", "Sơn tường, trần nhà"],
    ["PLUMBING", "Điện nước", "Sửa chữa hệ thống điện nước"],
    ["FLOORING", "Sàn nhà", "Lát sàn, sửa sàn"],
    ["FURNITURE", "Nội thất", "Mua sắm nội thất mới"],
    ["EQUIPMENT", "Thiết bị mua thêm", "Mua thêm thiết bị trong đợt cải tạo"],
    ["STRUCTURAL", "Kết cấu", "Thay đổi kết cấu, vách ngăn"],
    ["OTHER", "Khác", "Hạng mục cải tạo khác"],
    [],
    ["Catalog TB", "Ghi chú"],
    ["Điều hòa", ""],
    ["Tủ lạnh", ""],
    ["Máy giặt", ""],
    ["Giường", ""],
    ["Nóng lạnh", ""],
    ["Quạt", ""],
    [],
    ["Khu vực chung", ""],
    ["LIVING_ROOM", ""],
    ["KITCHEN", ""],
    ["BATHROOM", ""],
    ["BALCONY", ""],
  ];

  const guide = [
    [`Hướng dẫn — Ma trận RENO ${renoProps.length} căn (đợt 2)`],
    [""],
    ["Chỉ import sau khi đã import đợt 1 (các HĐ RENO)."],
    ["API: POST /api/v1/import/renovation-excel?dryRun="],
    ['Sheet "0. Ma_Tran_Onboarding": đối chiếu từng HĐ.'],
    ["NGUYEN_CAN: không cần sheet phòng. THEO_PHONG: sheet phòng khớp số phòng khai thác."],
    ["RENO bắt buộc ≥1 hạng mục cải tạo trước khi gửi Host."],
  ];

  const wb = XLSX.utils.book_new();
  const add = (name, aoa, colW) => {
    const ws = XLSX.utils.aoa_to_sheet(aoa);
    if (colW) ws["!cols"] = colW.map((w) => ({ wch: w }));
    XLSX.utils.book_append_sheet(wb, ws, name);
  };

  add("0. Huong_Dan", guide, [100]);
  add("0. Ma_Tran_Onboarding", matrixGuideRows(props), [5, 36, 12, 14, 16, 12, 10, 10, 60]);
  add("0. Danh_Muc_Tham_Khao", cat, [18, 22, 40]);
  add("1. Cau_Hinh_Khai_Thac", [cfgH, ...cfg], [28, 18, 18]);
  add("2. Danh_Sach_Phong", [roomH, ...rooms], [28, 10, 8, 16, 12, 12, 14]);
  add("3. Hop_Dong_Cai_Tao", [renoH, ...renos], [28, 18, 18, 18, 28]);
  add("4. Thiet_Bi_Mua_Moi", [buyH, ...buys], buyH.map((h) => Math.min(22, Math.max(12, h.length + 1))));

  XLSX.writeFile(wb, DOT2);
  console.log("Wrote", DOT2, "reno", renoProps.length, "rooms", rooms.length, "renoLines", renos.length, "buy", buys.length);
}

/**
 * 25 HĐ nháp: tenant01..25 (SĐT seed 0904…) ↔ 25 BĐS đợt 1.
 * Ưu tiên: 15 NORENO nguyên căn (sẵn sau đợt 1) + 10 RENO (WH + RM 101) sau khi xong đợt 2.
 */
function writeDraft(props) {
  const headers = [
    "Mã HĐ inbound", "Mã BĐS", "Tên tòa nhà", "Loại thuê", "Số phòng",
    "Họ tên khách thuê", "CCCD", "Số điện thoại", "Ngày sinh", "Ngày cấp CCCD",
    "Nơi cấp CCCD", "Hộ khẩu thường trú", "Ngày vào ở", "Ngày kết thúc",
    "Giá thuê/tháng", "Số tháng cọc", "Tiền cọc", "Ngày đón khách dự kiến",
  ];

  const norenoWh = props.filter((p) => p.mode === "NORENO" && p.shape === "WH"); // 15
  const renoWh = props.filter((p) => p.mode === "RENO" && p.shape === "WH");
  const renoRm = props.filter((p) => p.mode === "RENO" && p.shape === "RM");

  const picks = [
    ...norenoWh.slice(0, 15),
    ...renoWh.slice(0, 5),
    ...renoRm.slice(0, 5),
  ];
  if (picks.length !== 25) {
    throw new Error(`Expected 25 draft properties, got ${picks.length}`);
  }

  const ISSUE = [
    "CA TP. Hồ Chí Minh", "CA Quận 1", "CA Bình Thạnh", "CA Gò Vấp", "CA Phú Nhuận",
  ];

  const rows = [headers];
  const ref = [["#", "Mã HĐ master", "Tên tòa nhà", "Loại", "Số phòng", "Tenant seed", "SĐT", "Ghi chú"]];

  for (let i = 0; i < 25; i++) {
    const p = picks[i];
    const t = i + 1; // tenant01..25
    const name = fullNameByIndex(t + 9); // khớp SampleDataSeeder
    const phone = `0904${String(t).padStart(6, "0")}`;
    const cccd = `079${String(t).padStart(9, "0")}`;
    const isRoom = p.shape === "RM";
    const rentType = isRoom ? "THEO_PHONG" : "NGUYEN_CAN";
    const roomNo = isRoom ? "101" : "";
    const rent = p.monthlyRent;
    const depositMonths = 1 + (i % 2);
    const moveIn = "2026-10-01";
    const end = "2027-09-30";
    const birthY = 1988 + (i % 12);
    const issueY = 2018 + (i % 6);

    rows.push([
      p.code, // Mã HĐ inbound — map nhanh sau import
      "",
      p.name,
      rentType,
      roomNo,
      name,
      cccd,
      phone,
      ymd(birthY, 1 + (i % 12), 5 + (i % 20)),
      ymd(issueY, 1 + (i % 12), 10 + (i % 15)),
      ISSUE[i % ISSUE.length],
      `${20 + i} Lê Lợi, ${p.district}`,
      moveIn,
      end,
      rent,
      depositMonths,
      rent * depositMonths,
      moveIn,
    ]);

    ref.push([
      t, p.code, p.name, rentType, roomNo || "(nguyên căn)",
      `tenant${pad2(t)}`, phone,
      isRoom
        ? "Cần import đợt 2 (THEO_PHONG) trước — phòng 101"
        : p.mode === "NORENO"
          ? "Sẵn sau đợt 1 (NORENO)"
          : "Cần import đợt 2 (NGUYEN_CAN) trước",
    ]);
  }

  const guide = [
    ["Hướng dẫn import HĐ thuê nháp (DRAFT) — 25 dòng map tenant01..25"],
    [""],
    ["1. Seed tài khoản: tenant01..tenant28 / 123456 (chưa thuê)."],
    ["2. Import đợt 1: docs/SLMS2026_import_matrix_dot1.xlsx"],
    ["3. Import đợt 2 (RENO): docs/SLMS2026_import_matrix_dot2.xlsx"],
    ["4. Import file này: POST /api/v1/import/tenant-draft-contracts-excel?dryRun="],
    ["5. Map BĐS: cột Mã HĐ inbound (= mã master) hoặc Tên tòa nhà."],
    ["6. 15 NORENO nguyên căn + 5 RENO NGUYEN_CAN + 5 RENO THEO_PHONG phòng 101."],
    ["7. SĐT/CCCD/họ tên khớp SampleDataSeeder tenant01..25."],
    ["8. tenant26..28 không có dòng nháp — dùng test gán tay."],
    ["9. Nên dryRun=true trước; Auth ADMIN hoặc MANAGER."],
  ];

  const wb = XLSX.utils.book_new();
  const add = (name, aoa, colW) => {
    const ws = XLSX.utils.aoa_to_sheet(aoa);
    if (colW) ws["!cols"] = colW.map((w) => ({ wch: w }));
    XLSX.utils.book_append_sheet(wb, ws, name);
  };
  add("0. Huong_Dan", guide, [110]);
  add("1. Hop_Dong_Nhap_Khach", rows, headers.map((h) => Math.min(26, Math.max(12, h.length + 2))));
  add("0. Tham_Chieu_BDS", ref, [4, 36, 32, 12, 12, 12, 12, 40]);

  XLSX.writeFile(wb, DRAFT);
  console.log("Wrote", DRAFT, "drafts", rows.length - 1);
}

// ─── main ─────────────────────────────────────────────────────
const props = buildProperties();

// summary
const cnt = (fn) => props.filter(fn).length;
console.log("Properties:", props.length);
console.log("  NORENO WH NONE/BASIC/FULL:",
  cnt((p) => p.mode === "NORENO" && p.furn === "NONE"),
  cnt((p) => p.mode === "NORENO" && p.furn === "BASIC"),
  cnt((p) => p.mode === "NORENO" && p.furn === "FULL"));
console.log("  RENO WH:", cnt((p) => p.mode === "RENO" && p.shape === "WH"));
console.log("  RENO RM NONE/BASIC/FULL:",
  cnt((p) => p.shape === "RM" && p.furn === "NONE"),
  cnt((p) => p.shape === "RM" && p.furn === "BASIC"),
  cnt((p) => p.shape === "RM" && p.furn === "FULL"));

writeDot1(props);
writeDot2(props);
writeDraft(props);
console.log("Done.");
