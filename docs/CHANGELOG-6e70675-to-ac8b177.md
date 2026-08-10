# Báo cáo thay đổi: `6e70675` → `ac8b177`

| Trường | Giá trị |
|--------|---------|
| **Từ (base)** | `6e7067568a0d68b076fcf575abac552ac07e5134` — *Merge branch 'feature/checkout' into dev* |
| **Đến (head)** | `ac8b17794ee47bc2f4aca86034df286a6f47e2f6` — *fix: bootstrap ObjectMapper in GoogleVisionProvider for Spring Boot 4* |
| **Khoảng thời gian** | 2026-08-09 (sau merge `feature/checkout` → trước/khi fix Spring Boot 4 ObjectMapper) |
| **Số commit** | 3 |
| **Tác giả** | ngoc son |
| **Quy mô** | 12 files, **+743 / −101** dòng |

---

## 1. Tóm tắt

Khoảng commit này tập trung **làm chắc module nhận diện ảnh (Vision)** và **ổn định seed dữ liệu mẫu khi deploy**:

1. **Tách provider Vision** (Google Cloud + model local ONNX) với chế độ `auto` fallback.
2. **Cấu hình JVM/Docker** phù hợp VPS (heap 1GB, ONNX runtime).
3. **SampleDataSeeder** không còn chặn app boot khi trùng `contract_code` / lỗi expand.
4. **Sửa inject ObjectMapper** trên Spring Boot 4 trong `GoogleVisionProvider`.
5. Gỡ endpoint xóa push token legacy trên user entity.

API công khai FE **không đổi**: vẫn `POST /api/v1/vision/labels`.

---

## 2. Danh sách commit (cũ → mới)

| # | SHA | Ngày | Message | Phạm vi chính |
|---|-----|------|---------|----------------|
| 1 | `6f374c9` | 2026-08-09 | update google vision provider | Vision multi-provider, Docker/pom/config, labels |
| 2 | `87a3c76` | 2026-08-09 | fix data seeder | SampleDataSeeder resilient + sync contract seq |
| 3 | `ac8b177` | 2026-08-09 | fix: bootstrap ObjectMapper… Spring Boot 4 | GoogleVisionProvider |

Commit base (không nằm *trong* range `base..head`, nhưng là điểm xuất phát):

- **`6e70675`** — Merge `feature/checkout` vào `dev` (checkout/settlement + push tokens, mentor fixes).

---

## 3. Thay đổi theo chủ đề

### 3.1. Vision: kiến trúc multi-provider

**Trước:** logic Google Vision gắn trực tiếp trong `VisionServiceImpl`.

**Sau:**

| Thành phần | Vai trò |
|------------|---------|
| `VisionProvider` | Interface strategy (`name`, `isAvailable`, `detect`) |
| `GoogleVisionProvider` | LABEL_DETECTION qua Google Cloud Vision API |
| `LocalVisionProvider` | MobileNetV3 ONNX trong JVM (fallback offline) |
| `ImageSource` | Nguồn ảnh: URL cho Google; lazy download + cache bytes cho local |
| `VisionHttpClientConfig` | Bean `HttpClient` dùng chung (timeout connect 10s) |
| `VisionServiceImpl` | Orchestrator: validate URL host, chọn thứ tự provider, rate-limit **chỉ** cho Google |

**Chế độ `vision.provider`:**

| Giá trị | Hành vi |
|---------|---------|
| `google` | Chỉ Google |
| `local` | Chỉ ONNX local |
| `auto` (default) | Google trước → lỗi / timeout / hết quota → local |

- Rate limit theo user/giờ **không áp** cho local (chỉ đếm khi gọi Google).
- Timeout Google mặc định **8s** (fallback local nhanh ở mode `auto`).

### 3.2. Config & phụ thuộc

**`application.yaml` — block `vision` mở rộng:**

- `vision.provider` ← `VISION_PROVIDER` (default `auto`)
- `vision.google.timeout-seconds` ← `VISION_GOOGLE_TIMEOUT_SECONDS` (default `8`)
- `vision.local.model-path` / `labels-path` / `min-score` / `max-results` / `intra-op-threads`
- Model default: `classpath:models/equipment-mobilenetv3.onnx`
- Labels default: `classpath:models/labels.txt`
- `intra-op-threads: 1` (VPS 2 core — chừa CPU cho web + PostgreSQL)

**`pom.xml`:**

- Thêm dependency `com.microsoft.onnxruntime:onnxruntime:1.20.0`

**`Dockerfile`:**

```text
java -Xmx1g -XX:MaxMetaspaceSize=256m -Dserver.port=${PORT} -jar app.jar
```

- Heap cố định 1GB trên VPS ~4GB; ONNX native memory nằm ngoài heap.

**`src/main/resources/models/labels.txt` (mới):**

- 21 lớp khớp train/export ONNX (và FE equipment photo):
  - 14 thiết bị (air conditioner, refrigerator, …)
  - 1 bối cảnh: `furniture`
  - 6 lớp chặn: hand, person, food, plant, animal, screenshot

### 3.3. SampleDataSeeder an toàn khi boot production

| Thay đổi | Mục đích |
|----------|----------|
| Bỏ `@Transactional` trên `run()` | Tránh rollback lớn / khóa boot |
| try/catch quanh `seedFreshDemo()` / `expandDemoContracts()` | Lỗi seed **warn + bỏ qua**, app vẫn start |
| `syncContractSeqFromDb()` | Đọc max `HD-MT-yyyy-xxxxx` trong DB trước khi sinh code — tránh **trùng unique `contract_code`** khi expand tenant demo trên server đã có HĐ thật |

### 3.4. Push token API

**`PushTokenController`:** xóa `DELETE /me/push-token` (legacy clear field `User.pushToken`).

Luồng đăng ký/hủy token qua các endpoint multi-token hiện có vẫn giữ.

### 3.5. Fix Spring Boot 4 — ObjectMapper

**Commit `ac8b177`:** Spring Boot 4 có thể **không expose** `ObjectMapper` bean như trước.

- `GoogleVisionProvider` tự `new ObjectMapper()` (cùng pattern `OcrServiceImpl` / `PayosServiceImpl`)
- Constructor chỉ còn inject `HttpClient visionHttpClient`

---

## 4. Bảng file thay đổi

| File | Trạng thái | Ghi chú ngắn |
|------|------------|--------------|
| `Dockerfile` | Modified | `-Xmx1g`, `MaxMetaspaceSize=256m` |
| `pom.xml` | Modified | ONNX Runtime 1.20.0 |
| `application.yaml` | Modified | Vision multi-provider + local |
| `models/labels.txt` | **Added** | 21 class labels |
| `vision/VisionProvider.java` | **Added** | Interface |
| `vision/GoogleVisionProvider.java` | **Added** (+fix SB4) | Google provider |
| `vision/LocalVisionProvider.java` | **Added** | ONNX inference |
| `vision/ImageSource.java` | **Added** | URL / bytes source |
| `vision/VisionHttpClientConfig.java` | **Added** | HttpClient bean |
| `service/impl/VisionServiceImpl.java` | Modified | Orchestrator + fallback |
| `config/SampleDataSeeder.java` | Modified | Resilient seed + contract seq |
| `controller/PushTokenController.java` | Modified | Xóa endpoint legacy |

---

## 5. Ảnh hưởng vận hành / deploy

1. **Env gợi ý**
   - `VISION_PROVIDER=auto|google|local`
   - `GOOGLE_VISION_API_KEY=...` (bắt buộc nếu dùng google/auto)
   - `VISION_GOOGLE_TIMEOUT_SECONDS` (default 8)
   - `VISION_LOCAL_MODEL_PATH` / `VISION_LOCAL_LABELS_PATH` nếu model không nằm classpath mặc định

2. **Model ONNX**  
   File `equipment-mobilenetv3.onnx` phải có trên classpath hoặc path cấu hình; nếu thiếu → local provider `isAvailable=false` (app vẫn chạy với Google nếu có key).

3. **Memory Docker**  
   Không tăng heap quá mức vì ONNX dùng native memory ngoài heap.

4. **Seed demo**  
   Production không còn fail start vì trùng `contract_code` khi expand tenant demo.

5. **FE**  
   Contract Vision labels API giữ nguyên; không bắt buộc đổi contract trừ khi FE vẫn gọi `DELETE /me/push-token` legacy.

---

## 6. Rủi ro / lưu ý review

| Rủi ro | Mức | Ghi chú |
|--------|-----|---------|
| Model ONNX chưa có trên image deploy | Trung bình | Fallback local im lặng tắt; chỉ Google (hoặc lỗi nếu không có key) |
| Labels / model lệch thứ tự train | Cao nếu train lại | `labels.txt` index 0..20 phải khớp export ONNX |
| Xóa `DELETE /me/push-token` | Thấp | Chỉ vấn đề nếu client cũ còn gọi |
| Seeder catch-all | Thấp | Lỗi seed khó phát hiện nếu không đọc log WARN |

---

## 7. Cách kiểm chứng nhanh

```bash
# Commit trong range
git log --oneline 6e7067568a0d68b076fcf575abac552ac07e5134..ac8b17794ee47bc2f4aca86034df286a6f47e2f6

# Diff tổng
git diff --stat 6e7067568a0d68b076fcf575abac552ac07e5134..ac8b17794ee47bc2f4aca86034df286a6f47e2f6
```

**Smoke test:**

1. Boot app với `VISION_PROVIDER=auto` + API key → gọi detect labels; shutdown Google / timeout → log fallback local (nếu model có).
2. Boot không có model ONNX → app start, Google vẫn work.
3. Boot DB đã có HĐ `HD-MT-*` + `tenant22` chưa `tenant50` → expand seed không crash, log warn nếu fail.
4. Confirm không còn route `DELETE .../me/push-token` (legacy).

---

## 8. Kết luận

Ba commit sau merge `feature/checkout` đưa hệ thống từ **Vision single-path Google** sang **Google + ONNX local fallback**, kèm **giới hạn tài nguyên VPS**, **seed idempotent an toàn production**, và **tương thích Spring Boot 4** cho `ObjectMapper`. Đây là gói thay đổi hướng deploy/ổn định, không đổi contract API chính với FE (ngoại trừ gỡ 1 endpoint push-token legacy).
