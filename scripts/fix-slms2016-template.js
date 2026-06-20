const XLSX = require('xlsx');
const path = require('path');

const outPath = path.join(__dirname, '..', 'docs', 'SLMS2016.xlsx');

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

const hopDongThue = [
  [
    'Mã hợp đồng',
    'Tên tòa nhà',
    'Địa chỉ chi tiết',
    'Xã/Phường',
    'Quận/Huyện',
    'Tỉnh/Thành phố',
    'Diện tích (m²)',
    'Tổng số tầng',
    'Tổng số phòng',
    'Tên chủ nhà',
    'Tổng tiền thuê',
    'Ngày bắt đầu',
    'Ngày kết thúc',
    'Hình thức thuê',
    'Có cải tạo không',
    'Tỷ lệ chi phí dự phòng (%)',
    'Mô tả chi tiết',
  ],
  [
    'HD-2026-VILLACG',
    'Vinhomes Villa Cầu Giấy',
    'Số 18, Biệt thự 2',
    'Dịch Vọng Hậu',
    'Cầu Giấy',
    'Hà Nội',
    250.5,
    3,
    6,
    'Nguyễn Văn A',
    600000000,
    '2026-06-01',
    '2031-05-31',
    'WHOLE_HOUSE',
    'TRUE',
    5,
    'Biệt thự sân vườn rộng, có gara ô tô.',
  ],
];

const hopDongCaiTao = [
  [
    'Mã hợp đồng thuê',
    'Mã danh mục cải tạo',
    'Tên danh mục (Gợi ý)',
    'Chi phí cải tạo (VNĐ)',
    'Ghi chú chi tiết',
  ],
  [
    'HD-2026-VILLACG',
    'PAINTING',
    'Sơn sửa',
    25000000,
    'Sơn chống thấm mặt ngoài biệt thự',
  ],
  [
    'HD-2026-VILLACG',
    'FLOORING',
    'Sàn nhà',
    40000000,
    'Lát sàn gỗ công nghiệp cho 3 phòng ngủ tầng 2',
  ],
];

const phanBoThietBi = [
  [
    'Mã hợp đồng thuê',
    'Số phòng',
    'Khu vực chung',
    'Tên Catalog thiết bị',
    'Nguồn gốc thiết bị',
    'Trạng thái thiết bị',
    'Số lượng',
    'Đơn giá (VNĐ)',
    'Ghi chú lắp đặt',
  ],
  [
    'HD-2026-VILLACG',
    '101',
    '',
    'Điều hòa',
    'INITIAL_HANDOVER',
    'GOOD',
    1,
    0,
    'Máy lạnh có sẵn chủ bàn giao tại phòng 101',
  ],
  [
    'HD-2026-VILLACG',
    '201',
    '',
    'Giường',
    'PURCHASED',
    'NEW',
    1,
    7500000,
    'Mua mới cho phòng ngủ Master',
  ],
  [
    'HD-2026-VILLACG',
    '',
    'LIVING_ROOM',
    'Điều hòa',
    'PURCHASED',
    'NEW',
    1,
    16500000,
    'Lắp đặt tại phòng khách tầng 1',
  ],
];

const huongDan = [
  ['Hướng dẫn điền file import SLMS'],
  [''],
  ['1. Điền Sheet "1. Hop_Dong_Thue" trước — mỗi dòng = 1 căn nhà + 1 hợp đồng thuê inbound.'],
  ['2. Sheet "2. Hop_Dong_Cai_Tao": chỉ điền khi cột "Có cải tạo không" = TRUE. Mã danh mục phải khớp sheet "0. Danh_Muc_Tham_Khao".'],
  ['3. Sheet "3. Phan_Bo_Thiet_Bi": điền Số phòng HOẶC Khu vực chung (không điền cả hai).'],
  ['4. Hình thức thuê: WHOLE_HOUSE (nguyên căn) hoặc INDIVIDUAL_ROOM (thuê từng phòng).'],
  ['5. Nguồn gốc INITIAL_HANDOVER → đơn giá = 0. PURCHASED → đơn giá > 0.'],
  ['6. Trạng thái thiết bị khi import: NEW hoặc GOOD.'],
  ['7. Ngày tháng định dạng YYYY-MM-DD.'],
  ['8. Zone chỉ 2 cấp: Tỉnh/Thành phố (level 1) + Quận/Huyện (level 2) — phải khớp DB.'],
  ['9. Xã/Phường là phần địa chỉ chi tiết, không map Zone (cột tùy chọn).'],
];

function sheetFromAoA(data) {
  const ws = XLSX.utils.aoa_to_sheet(data);
  ws['!cols'] = data[0].map((header) => ({
    wch: Math.max(String(header).length + 4, 14),
  }));
  return ws;
}

const wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, sheetFromAoA(huongDan), '0. Huong_Dan');
XLSX.utils.book_append_sheet(
  wb,
  sheetFromAoA([...renovationCategories, [''], ...equipmentCatalog]),
  '0. Danh_Muc_Tham_Khao'
);
XLSX.utils.book_append_sheet(wb, sheetFromAoA(hopDongThue), '1. Hop_Dong_Thue');
XLSX.utils.book_append_sheet(wb, sheetFromAoA(hopDongCaiTao), '2. Hop_Dong_Cai_Tao');
XLSX.utils.book_append_sheet(wb, sheetFromAoA(phanBoThietBi), '3. Phan_Bo_Thiet_Bi');

XLSX.writeFile(wb, outPath);
console.log('Updated:', outPath);
