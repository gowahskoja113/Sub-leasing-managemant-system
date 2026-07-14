# Hợp đồng PDF — Hướng dẫn triển khai FE

**Breaking change:** BE không còn trả **DOCX**. `POST .../draft-document` và (file mới) download là **PDF** — nội dung giữ từ template `tenant-apartment-draft-template.docx`.

| Trước | Sau |
|-------|-----|
| `DRAFT-{code}.docx` | `DRAFT-{code}.pdf` |
| `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `application/pdf` |
| Preview kém trên browser | Browser / iframe preview PDF được |

**Auth các API dưới:** JWT `ROLE_MANAGER` / `ROLE_ADMIN` (trừ View Contract: thêm `ROLE_TENANT` cho HĐ của mình).

---

## 1. Luồng tổng quat

```
[Admin/Manager — tạo nháp]
        │
        ▼
POST /tenant-contracts/{id}/draft-document
        │  Response: binary PDF
        ▼
FE upload PDF → Cloudinary  (resource_type: raw hoặc auto, filename .pdf)
        │
        ▼
PUT /tenant-contracts/{id}  { "draftContractFileUrl": "https://..." }
        │
        ▼
contractFileAvailable = true

[User bấm View Contract]
        │
        ▼
GET /tenant-contracts/{id}/document/download
        │  Response: binary PDF (file cũ .docx vẫn tải được)
        ▼
Web: blob + window.open / <iframe>
Mobile: lưu .pdf + mở PDF viewer / share
```

> **Không** mở `draftContractFileUrl` (Cloudinary) trực tiếp trên client — luôn tải qua BE `/document/download` kèm JWT.

---

## 2. Xuất file nháp (tạo PDF)

### 2.1. API

```http
POST /api/v1/tenant-contracts/{contractId}/draft-document
Authorization: Bearer {managerToken}
```

| | |
|--|--|
| **Điều kiện** | HĐ `status = DRAFT` |
| **Response 200** | Body binary PDF |
| **Content-Type** | `application/pdf` |
| **Content-Disposition** | `attachment; filename="DRAFT-{contractCode}.pdf"` |

Lỗi thường gặp:

| HTTP | Ý nghĩa | FE |
|------|---------|-----|
| **422** | Không phải DRAFT / lỗi render PDF | Toast message từ BE |
| **404** | Không có HĐ | Redirect / toast |
| **401/403** | Token / quyền | Login lại / không đủ quyền |

### 2.2. Code mẫu — lấy PDF rồi upload Cloudinary

```typescript
const API_BASE = "/api/v1";

export async function renderDraftContractPdf(
  contractId: number,
  token: string,
): Promise<{ blob: Blob; filename: string }> {
  const res = await fetch(
    `${API_BASE}/tenant-contracts/${contractId}/draft-document`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    },
  );

  if (!res.ok) {
    throw new Error((await res.text()) || `HTTP ${res.status}`);
  }

  const blob = await res.blob();
  // Ép MIME PDF (một số browser trả octet-stream)
  const pdfBlob =
    blob.type === "application/pdf"
      ? blob
      : new Blob([blob], { type: "application/pdf" });

  const disposition = res.headers.get("Content-Disposition") ?? "";
  const match = disposition.match(/filename="?([^"]+)"?/);
  const filename = match?.[1] ?? `DRAFT-${contractId}.pdf`;

  return { blob: pdfBlob, filename };
}

/** Upload lên Cloudinary — giữ extension .pdf */
export async function uploadContractPdfToCloudinary(
  blob: Blob,
  filename: string,
  cloudName: string,
  uploadPreset: string,
): Promise<string> {
  const form = new FormData();
  form.append("file", blob, filename); // phải có .pdf
  form.append("upload_preset", uploadPreset);
  // PDF không phải image → dùng raw (hoặc auto tùy preset)
  form.append("resource_type", "raw");

  const res = await fetch(
    `https://api.cloudinary.com/v1_1/${cloudName}/raw/upload`,
    { method: "POST", body: form },
  );

  if (!res.ok) {
    throw new Error("Upload Cloudinary thất bại");
  }

  const data = await res.json();
  // secure_url sẽ dạng .../DRAFT-TC-xxx.pdf
  return data.secure_url as string;
}

export async function saveDraftContractFileUrl(
  contractId: number,
  draftContractFileUrl: string,
  token: string,
) {
  const res = await fetch(`${API_BASE}/tenant-contracts/${contractId}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ draftContractFileUrl }),
  });

  if (!res.ok) {
    throw new Error((await res.text()) || `HTTP ${res.status}`);
  }
  return res.json();
}

/** Handler nút "Xuất / Tạo hợp đồng nháp" */
export async function handleGenerateDraftDocument(
  contractId: number,
  token: string,
  cloud: { cloudName: string; uploadPreset: string },
) {
  const { blob, filename } = await renderDraftContractPdf(contractId, token);
  const url = await uploadContractPdfToCloudinary(
    blob,
    filename,
    cloud.cloudName,
    cloud.uploadPreset,
  );
  return saveDraftContractFileUrl(contractId, url, token);
}
```

### 2.3. Checklist tạo file

- [ ] Gọi `POST .../draft-document` khi HĐ còn `DRAFT`
- [ ] Upload Cloudinary với **filename `.pdf`** + `resource_type=raw` (hoặc `auto`)
- [ ] `PUT` lại `draftContractFileUrl`
- [ ] UI: sau khi xong, `contractFileAvailable === true` → bật **View Contract**
- [ ] Đổi label / icon: “Xuất PDF” thay “Xuất Word/DOCX” nếu UI đang ghi DOCX

---

## 3. View Contract (xem / tải PDF)

Tài liệu View cũ: [`FE-view-contract.md`](./FE-view-contract.md) — phần MIME/DOCX đã **lỗi thời**; làm theo mục này.

### 3.1. Điều kiện hiện nút

```typescript
function canViewContract(contract: {
  contractFileAvailable?: boolean;
}): boolean {
  return contract.contractFileAvailable === true;
}
```

Field từ `GET /api/v1/tenant-contracts/{id}`:

```json
{
  "id": 42,
  "contractCode": "TC-2026-00042",
  "contractFileAvailable": true,
  "draftContractFileUrl": "https://res.cloudinary.com/.../DRAFT-TC-2026-00042.pdf",
  "documentUrl": "https://res.cloudinary.com/.../DRAFT-TC-2026-00042.pdf",
  "pdfUrl": "https://res.cloudinary.com/.../DRAFT-TC-2026-00042.pdf"
}
```

> `documentUrl` / `pdfUrl` / `draftContractFileUrl` cùng URL đã lưu. **Chỉ** dùng để biết có file — xem file qua `/document/download`.

### 3.2. API download

```http
GET /api/v1/tenant-contracts/{contractId}/document/download
Authorization: Bearer {accessToken}
```

| | File mới (PDF) | File cũ còn trên Cloudinary (DOCX) |
|--|----------------|-------------------------------------|
| **Content-Type** | `application/pdf` | `application/...wordprocessingml.document` |
| **Content-Disposition** | `inline; filename="{code}.pdf"` | `inline; filename="{code}.docx"` |
| **Phân quyền** | ADMIN / MANAGER / TENANT (HĐ của mình) | giống trái |

BE tự nhận MIME theo đuôi URL / magic bytes — FE đọc `Content-Type` response, **đừng hard-code DOCX**.

### 3.3. Web (React) — preview PDF

```typescript
export async function downloadContractFile(
  contractId: number,
  token: string,
): Promise<{ blob: Blob; filename: string; mimeType: string }> {
  const res = await fetch(
    `${API_BASE}/tenant-contracts/${contractId}/document/download`,
    { headers: { Authorization: `Bearer ${token}` } },
  );

  if (!res.ok) {
    throw new Error((await res.text()) || `HTTP ${res.status}`);
  }

  const mimeType = res.headers.get("Content-Type") ?? "application/pdf";
  const raw = await res.blob();
  const blob = new Blob([raw], { type: mimeType.split(";")[0].trim() });

  const disposition = res.headers.get("Content-Disposition") ?? "";
  const match = disposition.match(/filename="?([^"]+)"?/);
  const filename =
    match?.[1] ??
    `contract-${contractId}.${mimeType.includes("pdf") ? "pdf" : "docx"}`;

  return { blob, filename, mimeType };
}

async function handleViewContract(contractId: number, token: string) {
  const { blob, filename, mimeType } = await downloadContractFile(
    contractId,
    token,
  );
  const url = URL.createObjectURL(blob);

  if (mimeType.includes("pdf")) {
    // Preview PDF trong tab mới
    window.open(url, "_blank", "noopener,noreferrer");
  } else {
    // Legacy DOCX: download hoặc mở app
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
  }

  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
```

Gợi ý UI preview in-page:

```tsx
{/* sau khi có blob URL PDF */}
<iframe title="Hợp đồng" src={pdfBlobUrl} className="h-[80vh] w-full" />
```

### 3.4. Mobile (Expo) — PDF

```typescript
import * as FileSystem from "expo-file-system";
import * as Sharing from "expo-sharing";
import * as IntentLauncher from "expo-intent-launcher"; // Android mở PDF (tuỳ chọn)
import { Platform } from "react-native";

async function viewContractMobile(
  contractId: number,
  contractCode: string,
  token: string,
) {
  const url = `${API_BASE}/tenant-contracts/${contractId}/document/download`;
  const localUri = `${FileSystem.cacheDirectory}${contractCode}.pdf`;

  const result = await FileSystem.downloadAsync(url, localUri, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (result.status !== 200) {
    throw new Error("Không tải được hợp đồng");
  }

  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(result.uri, {
      mimeType: "application/pdf",
      UTI: "com.adobe.pdf", // iOS
      dialogTitle: "Xem hợp đồng thuê",
    });
    return;
  }

  if (Platform.OS === "android") {
    const contentUri = await FileSystem.getContentUriAsync(result.uri);
    await IntentLauncher.startActivityAsync("android.intent.action.VIEW", {
      data: contentUri,
      flags: 1,
      type: "application/pdf",
    });
  }
}
```

---

## 4. Lỗi thường gặp

| HTTP | Nguyên nhân | FE |
|------|-------------|-----|
| **422** download | Chưa `PUT draftContractFileUrl` | *"Chưa có file hợp đồng — Admin cần xuất PDF và lưu trước."* |
| **422** draft-document | HĐ không còn `DRAFT` | Chỉ hiện nút Xuất khi `status === "DRAFT"` |
| **403** | Không quyền | Toast quyền |
| **401** | Token hết hạn | Login lại |

Message BE khi chưa có file:

```
Hợp đồng chưa có file — gọi POST .../draft-document, upload Cloudinary, rồi PUT draftContractFileUrl
```

---

## 5. Migration / tương thích

| Trường hợp | FE làm gì |
|------------|-----------|
| HĐ mới sau deploy | Upload **`.pdf`**, view như PDF |
| HĐ cũ đã có `.docx` trên Cloudinary | `/document/download` vẫn trả DOCX — đọc `Content-Type`, đừng assume PDF |
| UI đang hard-code `.docx` / Office MIME | Đổi sang PDF + fallback theo header |

Không bắt buộc migrate file cũ sang PDF. Muốn thống nhất PDF: Admin mở HĐ `DRAFT` còn sửa được → xuất lại → upload PDF mới → `PUT` URL mới.

---

## 6. Checklist FE

**Tạo file**

- [ ] `POST .../draft-document` → lưu blob PDF
- [ ] Cloudinary upload `.pdf` (`raw`/`auto`)
- [ ] `PUT { draftContractFileUrl }`
- [ ] Bỏ text/UI “DOCX / Word” nếu còn

**Xem file**

- [ ] Nút View khi `contractFileAvailable === true`
- [ ] `GET .../document/download` + Bearer
- [ ] Web: preview PDF (tab / iframe)
- [ ] Mobile: mime `application/pdf`
- [ ] Không mở URL Cloudinary trực tiếp
- [ ] Fallback DOCX cũ theo `Content-Type`

**Test**

- [ ] DRAFT: Xuất PDF → upload → View preview được tiếng Việt
- [ ] PENDING / ACTIVE: View vẫn mở file đã lưu
- [ ] Tenant chỉ xem HĐ của mình
- [ ] HĐ cũ `.docx` vẫn tải được (nếu còn)

---

## 7. Test nhanh (curl)

```bash
# 1. Xuất PDF nháp
curl -X POST -H "Authorization: Bearer {TOKEN}" \
  -o DRAFT-TC.pdf \
  http://localhost:8080/api/v1/tenant-contracts/42/draft-document

# 2. Sau khi PUT draftContractFileUrl — tải xem
curl -H "Authorization: Bearer {TOKEN}" \
  -o contract.pdf \
  http://localhost:8080/api/v1/tenant-contracts/42/document/download

file DRAFT-TC.pdf   # kỳ vọng: PDF document
```

Mở PDF bằng Chrome/Edge để kiểm tra tiếng Việt (Times New Roman / font hệ thống trên server BE).

---

## 8. FAQ

**BE có lưu file lên máy chủ không?**  
Không trên luồng chính. FE nhận bytes → Cloudinary → `PUT` URL.

**Có cần đổi payload `PUT` không?**  
Không — vẫn field `draftContractFileUrl`, chỉ đổi nội dung file sang PDF.

**`pdfUrl` trong response?**  
Alias cùng URL file đã lưu (trước đây từng trỏ DOCX). Có thể dùng thay `documentUrl`.

**Xuất lại khi đã PENDING/ACTIVE?**  
`POST .../draft-document` chỉ cho `DRAFT`. View dùng file đã lưu.
