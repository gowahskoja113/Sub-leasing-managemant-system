# View Contract — Hướng dẫn triển khai FE

> **Cập nhật 2026-07:** File hợp đồng đã đổi sang **PDF**. Guide đầy đủ (xuất + upload + xem): [`FE-contract-pdf-document.md`](./FE-contract-pdf-document.md).  
> Các mẫu DOCX / MIME Word bên dưới chỉ còn áp dụng cho **file cũ** còn trên Cloudinary.

Tài liệu **chỉ cho phần xem hợp đồng thuê** (nút **View Contract** / **Xem hợp đồng**).

**Luồng tạo file trước đó:** Admin `POST /draft-document` → upload Cloudinary (PDF) → `PUT draftContractFileUrl`  
**Tham chiếu đầy đủ:** [`FE-contract-pdf-document.md`](./FE-contract-pdf-document.md)

---

## 1. Tóm tắt nhanh

| Việc FE cần làm | Chi tiết |
|-----------------|----------|
| Kiểm tra có file chưa | `contractFileAvailable === true` từ API chi tiết HĐ |
| Gọi API xem/tải | `GET /api/v1/tenant-contracts/{id}/document/download` + JWT |
| Hiển thị | Web: mở blob tab mới · Mobile: lưu file + share / mở app Office |
| **Không** làm | Mở URL Cloudinary trực tiếp · Gọi `POST /draft-document` để xem |

```
[User bấm View Contract]
        │
        ▼
GET /tenant-contracts/{id}/document/download  (Bearer token)
        │
        ▼
Response: binary .docx
        │
        ├─ Web    → blob URL → window.open / download
        └─ Mobile → FileSystem → Sharing / Intent mở Word
```

---

## 2. Điều kiện hiện nút

### 2.1. Field từ API

Lấy từ `GET /api/v1/tenant-contracts/{id}` hoặc danh sách HĐ:

```json
{
  "id": 42,
  "contractCode": "TC-2026-00042",
  "status": "DRAFT",
  "contractFileAvailable": true,
  "draftContractFileUrl": "https://res.cloudinary.com/.../DRAFT-TC-2026-00042.docx",
  "documentUrl": "https://res.cloudinary.com/.../DRAFT-TC-2026-00042.docx"
}
```

| Field | Ý nghĩa |
|-------|---------|
| `contractFileAvailable` | `true` → **bật** nút View Contract |
| `draftContractFileUrl` | URL gốc trên Cloudinary (tham khảo, không mở trực tiếp) |
| `documentUrl` | BE map = `draftContractFileUrl` (cùng giá trị) |

### 2.2. Logic UI

```typescript
function canViewContract(contract: TenantContractResponse): boolean {
  return contract.contractFileAvailable === true;
  // fallback nếu BE cũ chưa có field:
  // return !!(contract.draftContractFileUrl ?? contract.documentUrl);
}
```

| `contractFileAvailable` | UI |
|-------------------------|-----|
| `true` | Nút **View Contract** enabled |
| `false` / `null` | Disabled + tooltip *"Chưa có file hợp đồng"* |
| Đang tải | Spinner trên nút |

> File HĐ **giữ nguyên** từ lúc Admin tạo — xem được ở `DRAFT`, `PENDING`, `ACTIVE`.

---

## 3. API View Contract

### 3.1. Tải file DOCX (dùng cho nút View)

```http
GET /api/v1/tenant-contracts/{contractId}/document/download
Authorization: Bearer {accessToken}
```

| | |
|--|--|
| **Response 200** | Body binary `.docx` |
| **Content-Type** | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| **Content-Disposition** | `inline; filename="TC-2026-00042.docx"` |
| **Phân quyền** | `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_TENANT` (tenant chỉ HĐ của mình) |

### 3.2. Lấy metadata URL (tùy chọn)

```http
GET /api/v1/tenant-contracts/{contractId}/document
Authorization: Bearer {accessToken}
```

```json
{
  "contractId": 42,
  "contractCode": "TC-2026-00042",
  "documentUrl": "https://res.cloudinary.com/.../DRAFT-TC-2026-00042.docx",
  "documentGeneratedAt": null,
  "effective": true,
  "effectiveLabel": "Còn hiệu lực"
}
```

> Chỉ cần **link** thì dùng API này. Để **mở/xem file** → dùng `/document/download`.

### 3.3. Tenant xem danh sách HĐ của mình

```http
GET /api/v1/me/tenant-contracts
Authorization: Bearer {accessToken}
```

Mỗi item có `contractFileAvailable`, `documentUrl` — logic View Contract giống trên.

---

## 4. Code mẫu — Admin Web (React)

### 4.1. Service

```typescript
const API_BASE = "/api/v1";

export async function downloadContractDocx(
  contractId: number,
  token: string,
): Promise<{ blob: Blob; filename: string }> {
  const res = await fetch(`${API_BASE}/tenant-contracts/${contractId}/document/download`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!res.ok) {
    const message = await res.text();
    throw new Error(message || `HTTP ${res.status}`);
  }

  const blob = await res.blob();
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const match = disposition.match(/filename="?([^"]+)"?/);
  const filename = match?.[1] ?? `contract-${contractId}.docx`;

  return { blob, filename };
}
```

### 4.2. Handler nút View Contract

```typescript
async function handleViewContract(contractId: number, token: string) {
  try {
    setLoading(true);
    const { blob, filename } = await downloadContractDocx(contractId, token);
    const url = URL.createObjectURL(blob);

    // Cách 1: mở tab mới (browser có thể download hoặc hỏi mở Word)
    window.open(url, "_blank", "noopener,noreferrer");

    // Cách 2: force download
    // const a = document.createElement("a");
    // a.href = url;
    // a.download = filename;
    // a.click();

    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (err) {
    toast.error(err instanceof Error ? err.message : "Không mở được hợp đồng");
  } finally {
    setLoading(false);
  }
}
```

### 4.3. Component gợi ý

```tsx
function ViewContractButton({
  contract,
  token,
}: {
  contract: { id: number; contractFileAvailable?: boolean };
  token: string;
}) {
  const [loading, setLoading] = useState(false);
  const enabled = contract.contractFileAvailable === true;

  return (
    <button
      type="button"
      disabled={!enabled || loading}
      title={enabled ? "Xem hợp đồng thuê" : "Chưa có file hợp đồng"}
      onClick={() => handleViewContract(contract.id, token)}
    >
      {loading ? "Đang tải..." : "View Contract"}
    </button>
  );
}
```

---

## 5. Code mẫu — Manager Mobile (Expo / React Native)

```typescript
import * as FileSystem from "expo-file-system";
import * as Sharing from "expo-sharing";

async function viewContractMobile(contractId: number, contractCode: string, token: string) {
  const url = `${API_BASE}/tenant-contracts/${contractId}/document/download`;
  const localUri = `${FileSystem.cacheDirectory}${contractCode}.docx`;

  const result = await FileSystem.downloadAsync(url, localUri, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (result.status !== 200) {
    throw new Error("Không tải được hợp đồng");
  }

  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(result.uri, {
      mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      dialogTitle: "Xem hợp đồng thuê",
    });
  } else {
    throw new Error("Thiết bị không hỗ trợ chia sẻ file");
  }
}
```

---

## 6. Xử lý lỗi

| HTTP | Nguyên nhân | FE hiển thị |
|------|-------------|-------------|
| **422** | Chưa có file (`draftContractFileUrl` null) | *"Chưa có hợp đồng. Admin cần tạo và lưu file trước."* |
| **403** | Không có quyền xem HĐ này | *"Bạn không có quyền xem hợp đồng này"* |
| **404** | `contractId` không tồn tại | Redirect / toast lỗi |
| **401** | Token hết hạn | Đăng nhập lại |
| Network / 5xx | BE hoặc Cloudinary lỗi | *"Không tải được file, thử lại"* + nút Retry |

Message 422 từ BE:

```
Hợp đồng chưa có file — gọi POST .../draft-document, upload Cloudinary, rồi PUT draftContractFileUrl
```

---

## 7. Màn hình cần có nút View Contract

| App | Màn hình | Ghi chú |
|-----|----------|---------|
| **Admin Web** | Chi tiết HĐ sau tạo nháp | Sau upload Cloudinary → `contractFileAvailable=true` |
| **Admin Web** | Danh sách HĐ DRAFT | Cột action View |
| **Manager Mobile** | Chi tiết HĐ tiếp khách | Trước & sau thu cọc |
| **Manager Mobile** | Sau `paymentStatus=PAID` | Cùng file, không chờ file mới |
| **Tenant App** | `GET /me/tenant-contracts` | Mỗi HĐ ACTIVE |

---

## 8. FAQ

**Hỏi: Sau khi khách ký (ACTIVE), có file mới không?**  
Không. View Contract vẫn tải **file đã lưu** lúc Admin tạo nháp.

**Hỏi: Có preview DOCX trong browser như PDF không?**  
Thường **không**. Browser download hoặc mở app Office. Mobile dùng share sheet.

**Hỏi: Có cần gọi `POST /draft-document` khi bấm View?**  
**Không.** View chỉ dùng `GET .../document/download`.

**Hỏi: `documentUrl` vs `draftContractFileUrl`?**  
Cùng URL. FE chỉ cần check `contractFileAvailable` và gọi `/document/download`.

**Hỏi: Tenant xem được không?**  
Có — `GET .../document/download` với `ROLE_TENANT` (HĐ của chính họ).

---

## 9. Checklist triển khai

- [ ] Đọc `contractFileAvailable` từ `GET /tenant-contracts/{id}`
- [ ] Disable nút khi `contractFileAvailable !== true`
- [ ] `GET /tenant-contracts/{id}/document/download` kèm `Authorization: Bearer`
- [ ] Web: `blob` → `URL.createObjectURL` → `window.open` hoặc download
- [ ] Mobile: `FileSystem.downloadAsync` → `Sharing.shareAsync`
- [ ] Loading state khi đang tải
- [ ] Toast lỗi 422 khi chưa có file
- [ ] Không mở URL Cloudinary trực tiếp
- [ ] Test: DRAFT / PENDING / ACTIVE đều xem được cùng file
- [ ] Test: Tenant chỉ xem được HĐ của mình

---

## 10. Test nhanh (Postman / curl)

```bash
# 1. Lấy chi tiết — kiểm tra contractFileAvailable
curl -H "Authorization: Bearer {TOKEN}" \
  http://localhost:8080/api/v1/tenant-contracts/42

# 2. Tải file xem
curl -H "Authorization: Bearer {TOKEN}" \
  -o contract.docx \
  http://localhost:8080/api/v1/tenant-contracts/42/document/download
```

Mở `contract.docx` bằng Word để xác nhận nội dung đã fill đúng.
