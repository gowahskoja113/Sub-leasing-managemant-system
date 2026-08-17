/**
 * Tài khoản tenant demo — giữ công thức đồng bộ với SampleDataSeeder.tenantPhone / fullNameByIndex.
 * 50 SĐT login sẵn; tenant ngoài dãy này = chưa có tài khoản (import HĐ nháp sẽ tạo mới, firstLogin).
 */

export const TENANT_COUNT = 50;
export const TENANT_PASSWORD = '123456';

/** Prefix 10 số VN, xen kẽ mạng — tránh dãy 00000xx. */
const PHONE_PREFIXES = [
  '090', '091', '093', '094', '096', '097', '098',
  '032', '033', '035', '036', '037', '038', '039',
  '070', '076', '077', '078', '079', '081',
  '082', '083', '084', '085',
];

const HO = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ'];
const DEM = ['Văn', 'Thị', 'Hữu', 'Đức', 'Minh', 'Ngọc', 'Gia', 'Quang', 'Thanh', 'Khánh'];
const TEN = [
  'An', 'Bình', 'Cường', 'Dũng', 'Phúc', 'Giang', 'Hà', 'Hiếu', 'Khoa', 'Long',
  'Mai', 'Nam', 'Oanh', 'Phương', 'Quân', 'Quỳnh', 'Sơn', 'Tâm', 'Uyên', 'Vy',
  'Xuân', 'Yến', 'Bảo', 'Châu', 'Duy', 'Hải', 'Khang', 'Linh', 'Trang', 'Tú',
];

/** SĐT tenant i (1-based): 10 số, đồng thời là username. Khớp SampleDataSeeder. */
export function tenantPhone(index) {
  const prefix = PHONE_PREFIXES[(index - 1) % PHONE_PREFIXES.length];
  const body = ((4_123_751 + (index - 1) * 1379) % 10_000_000 + 10_000_000) % 10_000_000;
  return prefix + String(body).padStart(7, '0');
}

/** Cùng công thức SampleDataSeeder.fullNameByIndex(i + 9). */
export function tenantFullName(index) {
  return fullNameByIndex(index + 9);
}

export function tenantCccd(index) {
  return `079${String(85_001_000 + index).padStart(9, '0')}`;
}

/** Tenant chưa có TK — SĐT 0885xxxxxx, không trùng dãy seed. */
export function newTenantPhone(index) {
  return `0885${String(512_317 + index * 41).padStart(6, '0').slice(-6)}`;
}

export function newTenantFullName(index) {
  return fullNameByIndex(index + 220);
}

export function newTenantCccd(index) {
  return `079${String(86_001_000 + index).padStart(9, '0')}`;
}

function fullNameByIndex(idx) {
  const ho = HO[idx % HO.length];
  const dem = DEM[Math.floor(idx / TEN.length) % DEM.length];
  const ten = TEN[idx % TEN.length];
  const round = Math.floor(idx / (HO.length * TEN.length));
  return round === 0 ? `${ho} ${dem} ${ten}` : `${ho} ${dem} ${ten} ${round}`;
}

export function seededTenant(index) {
  return {
    seeded: true,
    fullName: tenantFullName(index),
    phone: tenantPhone(index),
    cccd: tenantCccd(index),
  };
}

export function walkInTenant(index) {
  return {
    seeded: false,
    fullName: newTenantFullName(index),
    phone: newTenantPhone(index),
    cccd: newTenantCccd(index),
  };
}
