/**
 * Bổ sung dòng phòng thiếu vào sheet 3 của SLMS2026_v2_nhieu_can_nha.xlsx
 */
import XLSX from 'xlsx';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const excelPath = path.join(__dirname, '..', 'docs', 'SLMS2026_v2_nhieu_can_nha.xlsx');

const wb = XLSX.readFile(excelPath);
const sheetName = '3. Phan_Bo_Thiet_Bi';
const ws = wb.Sheets[sheetName];
const rows = XLSX.utils.sheet_to_json(ws, { defval: '' });

const leases = XLSX.utils.sheet_to_json(wb.Sheets['1. Hop_Dong_Thue'], { defval: '' });

const roomsByContract = new Map();
for (const row of rows) {
  const code = String(row['Mã hợp đồng thuê'] || '').trim();
  const room = String(row['Số phòng'] || '').trim();
  if (!code) continue;
  if (!roomsByContract.has(code)) roomsByContract.set(code, new Set());
  if (room) roomsByContract.get(code).add(room);
}

function generateRoomNumbers(totalRooms) {
  const rooms = [];
  let floor = 1;
  let indexOnFloor = 1;
  while (rooms.length < totalRooms) {
    rooms.push(String(`${floor}${String(indexOnFloor).padStart(2, '0')}`));
    indexOnFloor++;
    if (indexOnFloor > 99) {
      indexOnFloor = 1;
      floor++;
    }
  }
  return rooms;
}

const newRows = [];
for (const lease of leases) {
  const code = String(lease['Mã hợp đồng'] || '').trim();
  const type = String(lease['Hình thức thuê'] || '').trim();
  const totalRooms = Number(lease['Tổng số phòng']);
  if (type !== 'INDIVIDUAL_ROOM' || !totalRooms) continue;

  const existing = roomsByContract.get(code) || new Set();
  const expected = generateRoomNumbers(totalRooms);
  for (const roomNumber of expected) {
    if (existing.has(roomNumber)) continue;
    newRows.push({
      'Mã hợp đồng thuê': code,
      'Số phòng': roomNumber,
      'Khu vực chung': '',
      'Tên Catalog thiết bị': 'Quạt',
      'Nguồn gốc thiết bị': 'INITIAL_HANDOVER',
      'Trạng thái thiết bị': 'GOOD',
      'Số lượng': 1,
      'Đơn giá (VNĐ)': 0,
      'Ghi chú lắp đặt': 'Phòng trống — chưa lắp thiết bị riêng',
    });
    existing.add(roomNumber);
  }
  roomsByContract.set(code, existing);
}

if (newRows.length === 0) {
  console.log('Không có phòng thiếu — file giữ nguyên.');
  process.exit(0);
}

const allRows = [...rows, ...newRows];
const newWs = XLSX.utils.json_to_sheet(allRows, { header: Object.keys(rows[0] || newRows[0]) });
wb.Sheets[sheetName] = newWs;
XLSX.writeFile(wb, excelPath);

console.log(`Đã thêm ${newRows.length} dòng phòng vào sheet 3:`);
for (const row of newRows) {
  console.log(`  ${row['Mã hợp đồng thuê']} → phòng ${row['Số phòng']}`);
}
