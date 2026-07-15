# FE — Hợp đồng PDF (tạo / upload / xem)

**Gửi team FE** · Cập nhật **2026-07-15**

---

## TL;DR — FE cần làm gì ngay

1. **Đổi hết DOCX → PDF** (filename, MIME, Cloudinary endpoint)
2. **Upload Cloudinary** dùng `raw/upload`, không dùng `image/upload`
3. **Lưu URL** bằng `secure_url` từ Cloudinary → `PUT draftContractFileUrl`
4. **Xem file** qua `GET .../document/download` + JWT — **không** mở URL Cloudinary trực tiếp
5. **Bỏ checkbox** nội thất — BE tự gắn hết thiết bị nhà (xem [`FE-contract-handover-equipment.md`](./FE-contract-handover-equipment.md))
6. **Đọc lỗi BE** từ field `error` (422), đừng chỉ hiện `Request failed with status code 400`

---

## Luồng chuẩn

```
Tạo HĐ nháp (POST/PUT onboard)
        │
        ▼
POST /tenant-contracts/{id}/draft-document     ← BE trả binary PDF
        │
        ▼
FE upload PDF → Cloudinary (raw/upload)
        │
        ▼
PUT /tenant-contracts/{id}
     { "draftContractFileUrl": "https://res.cloudinary.com/.../xxx.pdf" }
        │
        ▼
contractFileAvailable = true

─── Khi user bấm Xem ───

GET /tenant-contracts/{id}/document/download   ← BE proxy từ Cloudinary
        │
        ▼
FE: blob → preview PDF (tab / iframe / share mobile)
```

---

## 1. API tạo PDF

```http
POST /api/v1/tenant-contracts/{contractId}/draft-document
Authorization: Bearer {token}
```

| | |
|--|--|
| Điều kiện | `status === "DRAFT"` |
| Success 200 | Body = **binary PDF** |
| Content-Type | `application/pdf` |
| Content-Disposition | `attachment; filename="DRAFT-{contractCode}.pdf"` |

**Không gửi body.** Chỉ cần JWT + `contractId`.

---

## 2. Upload Cloudinary (hay lỗi 400 ở đây)

PDF **không phải ảnh**. Nếu FE vẫn gọi `/image/upload` hoặc hard-code `.docx` → Cloudinary trả **400** → toast *"Request failed with status code 400"* dù BE đã OK.

```typescript
const form = new FormData();
form.append("file", pdfBlob, "DRAFT-TC-2026-00042.pdf"); // bắt buộc .pdf
form.append("upload_preset", UPLOAD_PRESET);
form.append("resource_type", "raw");

const res = await fetch(
  `https://api.cloudinary.com/v1_1/${CLOUD_NAME}/raw/upload`,
  { method: "POST", body: form },
);
```

**Lưu URL:** dùng `response.secure_url` nguyên vẹn — **đừng** tự ghép URL, đừng thêm khoảng trắng / ký tự lạ.

```typescript
await fetch(`${API_BASE}/tenant-contracts/${id}`, {
  method: "PUT",
  headers: {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({ draftContractFileUrl: secureUrl }),
});
```

---

## 3. API xem / tải

```http
GET /api/v1/tenant-contracts/{contractId}/document/download
Authorization: Bearer {token}
```

- Phân quyền: ADMIN, MANAGER, TENANT (tenant chỉ HĐ của mình)
- File mới: `application/pdf`, filename `{contractCode}.pdf`
- HĐ cũ còn `.docx` trên Cloudinary: BE vẫn trả DOCX — **đọc `Content-Type`**, đừng hard-code

Nút View chỉ bật khi `contractFileAvailable === true`.

---

## 4. Xử lý lỗi (quan trọng — đã fix BE 2026-07-15)

### Mã HTTP từ BE

| HTTP | Ý nghĩa | FE làm gì |
|------|---------|-----------|
| **422** | Lỗi nghiệp vụ — đọc message | Hiện `error` trong body (xem bên dưới) |
| **400** | Body JSON / validation sai | Kiểm tra payload `PUT` |
| **401 / 403** | Auth / quyền | Login lại |
| **404** | Không có HĐ | Toast + redirect |
| **500** | Lỗi hệ thống BE | Báo BE kèm `contractId` + thời điểm |

### Format body lỗi BE (422)

```json
{
  "timestamp": "2026-07-15T16:30:00",
  "status": 422,
  "error": "Không tạo được PDF hợp đồng (HĐ TC-2026-00042): ..."
}
```

**Helper đọc lỗi:**

```typescript
async function readApiError(res: Response): Promise<string> {
  const text = await res.text();
  try {
    const json = JSON.parse(text);
    return json.error ?? json.message ?? text;
  } catch {
    return text || `HTTP ${res.status}`;
  }
}

// Dùng khi draft-document fail:
if (!res.ok) {
  throw new Error(await readApiError(res));
}
```

### Phân biệt lỗi BE vs Cloudinary

| Triệu chứng | Request đỏ trong Network | Nguyên nhân |
|-------------|---------------------------|-------------|
| Tạo HĐ OK, toast *"KHÔNG sinh được file"* | `POST .../draft-document` → **422** | BE: template / dữ liệu HĐ / convert PDF |
| Cùng toast | `POST .../draft-document` → **200**, `api.cloudinary.com` → **400** | **FE:** upload sai (image thay raw, thiếu .pdf) |
| Cùng toast | `PUT .../tenant-contracts/{id}` → **400** | **FE:** body validation |
| Xem HĐ lỗi | `GET .../document/download` → **422** | Chưa `PUT` URL / URL Cloudinary hỏng |

> Toast generic *"Request failed with status code 400"* **không đủ** — mở Network tab, xem **URL request nào** 400.

---

## 5. Code mẫu — full flow tạo file sau onboard

```typescript
const API_BASE = "/api/v1";

export async function generateAndSaveContractPdf(
  contractId: number,
  token: string,
  cloud: { cloudName: string; uploadPreset: string },
) {
  // 1. BE sinh PDF
  const draftRes = await fetch(
    `${API_BASE}/tenant-contracts/${contractId}/draft-document`,
    { method: "POST", headers: { Authorization: `Bearer ${token}` } },
  );
  if (!draftRes.ok) {
    throw new Error(await readApiError(draftRes));
  }

  const raw = await draftRes.blob();
  const pdfBlob =
    raw.type === "application/pdf"
      ? raw
      : new Blob([raw], { type: "application/pdf" });

  const disposition = draftRes.headers.get("Content-Disposition") ?? "";
  const match = disposition.match(/filename="?([^"]+)"?/);
  const filename = match?.[1] ?? `DRAFT-${contractId}.pdf`;

  // 2. Cloudinary
  const form = new FormData();
  form.append("file", pdfBlob, filename);
  form.append("upload_preset", cloud.uploadPreset);
  form.append("resource_type", "raw");

  const uploadRes = await fetch(
    `https://api.cloudinary.com/v1_1/${cloud.cloudName}/raw/upload`,
    { method: "POST", body: form },
  );
  if (!uploadRes.ok) {
    const err = await uploadRes.text();
    throw new Error(`Upload Cloudinary thất bại (${uploadRes.status}): ${err}`);
  }
  const { secure_url } = await uploadRes.json();

  // 3. Lưu URL
  const putRes = await fetch(`${API_BASE}/tenant-contracts/${contractId}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ draftContractFileUrl: secure_url }),
  });
  if (!putRes.ok) {
    throw new Error(await readApiError(putRes));
  }
  return putRes.json();
}
```

---

## 6. Code mẫu — xem PDF (Web)

```typescript
export async function viewContractPdf(contractId: number, token: string) {
  const res = await fetch(
    `${API_BASE}/tenant-contracts/${contractId}/document/download`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!res.ok) {
    throw new Error(await readApiError(res));
  }

  const mime = (res.headers.get("Content-Type") ?? "application/pdf").split(";")[0];
  const blob = new Blob([await res.blob()], { type: mime });
  const url = URL.createObjectURL(blob);

  if (mime.includes("pdf")) {
    window.open(url, "_blank", "noopener,noreferrer");
  } else {
    // legacy DOCX
    const a = document.createElement("a");
    a.href = url;
    a.download = `contract-${contractId}.docx`;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
```

Mobile (Expo): `FileSystem.downloadAsync` → `Sharing.shareAsync` với `mimeType: "application/pdf"`.

---

## 7. Nội thất trên HĐ

BE **tự lấy hết** thiết bị ACTIVE của nhà/phòng — **không gửi** `selectedEquipmentIds`, **không** checkbox.

Chi tiết: [`FE-contract-handover-equipment.md`](./FE-contract-handover-equipment.md)

---

## 8. Migration DOCX → PDF

| | Trước | Sau |
|--|-------|-----|
| File xuất | `.docx` | `.pdf` |
| Cloudinary | thường `image/upload` | **`raw/upload`** |
| View | Office viewer | PDF viewer / iframe |
| HĐ cũ còn `.docx` | — | Vẫn xem được qua `/document/download` (đọc Content-Type) |

HĐ cũ muốn PDF: mở lại DRAFT → xuất lại → upload PDF mới → `PUT` URL mới.

---

## 9. Checklist FE

**Bắt buộc**

- [ ] `POST draft-document` nhận PDF, không expect DOCX
- [ ] Cloudinary: `raw/upload`, filename `.pdf`
- [ ] `PUT` dùng `secure_url` từ Cloudinary
- [ ] View: `GET document/download` + Bearer
- [ ] Parse lỗi từ `error` (422), log Network khi fail
- [ ] Bỏ checkbox nội thất

**Không làm**

- [ ] Hard-code `.docx` / MIME Word
- [ ] Mở `draftContractFileUrl` trực tiếp (thiếu JWT)
- [ ] Gửi `selectedEquipmentIds: []` (sẽ gắn 0 thiết bị nếu gửi nhầm)

---

## 10. Debug khi gặp lỗi

1. Network tab → request nào fail? (draft-document / cloudinary / PUT / download)
2. Copy response body → gửi BE nếu là 422/500
3. Với Cloudinary 400: kiểm tra endpoint `raw`, preset, file có extension `.pdf`
4. Với download 422 *"URL không hợp lệ"*: kiểm tra `draftContractFileUrl` trong DB/response có đúng URL Cloudinary không

---

## 11. FAQ

**Q: BE có lưu file trên server không?**  
A: Không. FE upload Cloudinary, BE chỉ lưu URL.

**Q: `pdfUrl` vs `documentUrl` vs `draftContractFileUrl`?**  
A: Cùng một URL đã lưu. Chỉ dùng để biết có file; xem qua `/document/download`.

**Q: Sau ACTIVE có file mới không?**  
A: Không. View luôn dùng file đã lưu lúc tạo nháp.

**Q: Liên hệ BE khi nào?**  
A: `draft-document` hoặc `download` trả **422/500** kèm message — gửi `contractId` + response body + screenshot Network.
