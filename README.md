# SLMS2026 — Sub-Leasing Management System (Backend API)

Backend cho hệ thống **quản lý cho thuê lại nhà/phòng (sub-leasing)**. Công ty thuê nhà nguyên căn từ chủ nhà, cải tạo, rồi cho khách thuê lại theo phòng hoặc nguyên căn. Hệ thống quản lý toàn bộ vòng đời: nhận nhà, cải tạo, ký hợp đồng với khách, thu tiền hàng tháng, bảo trì và trả phòng.

Đồ án Capstone — SEP490.

---

## Mục lục

- [Tính năng chính](#tính-năng-chính)
- [Vai trò người dùng](#vai-trò-người-dùng)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Cài đặt và chạy local](#cài-đặt-và-chạy-local)
- [Triển khai bằng Docker (VPS)](#triển-khai-bằng-docker-vps)
- [Biến môi trường](#biến-môi-trường)
- [Tài khoản demo](#tài-khoản-demo)
- [Tài liệu API](#tài-liệu-api)
- [Tài liệu thiết kế](#tài-liệu-thiết-kế)
- [Xử lý sự cố thường gặp](#xử-lý-sự-cố-thường-gặp)

---

## Tính năng chính

| Nhóm | Mô tả |
|---|---|
| Nhận nhà và cải tạo | Tạo nhà nháp, khai báo phòng, thiết bị, hạng mục cải tạo; gửi chủ đầu tư (Owner) duyệt giá; phân công quản lý vận hành. Hỗ trợ import hàng loạt bằng Excel và ảnh bằng file ZIP. |
| Hợp đồng thuê | Tạo hợp đồng nháp, xuất file DOCX/PDF từ template, Owner duyệt giá, thu cọc qua PayOS, kích hoạt hợp đồng bằng OTP hai phía (Twilio), gia hạn, chấm dứt. |
| Điện nước và hoá đơn | Ghi chỉ số đồng hồ (có OCR đọc ảnh), khoá chỉ số, mở khoá bằng passcode admin, phát hành hoá đơn điện nước, tiền nhà, dịch vụ; khách thanh toán qua PayOS; khiếu nại hoá đơn. |
| Bảo trì | Khách tạo yêu cầu, quản lý xác nhận có mặt bằng QR thiết bị, chẩn đoán lỗi (có hỗ trợ nhận diện ảnh bằng Google Vision / Gemini / ONNX local), sửa chữa, lập hoá đơn, lịch sử thiết bị. |
| Trả phòng | Khách gửi yêu cầu trả phòng, quản lý duyệt, kiểm tra hư hỏng, quyết toán, hoàn cọc, hoàn tất. |
| Tài chính và báo cáo | Dashboard cho Owner, chi phí, hợp đồng thuê gốc với chủ nhà, khấu hao, dòng tiền. |
| Realtime và thông báo | WebSocket/STOMP tại `/ws`, trung tâm thông báo, push token cho mobile. |
| Job tự động | Phát hành hoá đơn tiền nhà, nhắc nợ, đánh dấu quá hạn, huỷ hợp đồng no-show, hết hạn hợp đồng. |

## Vai trò người dùng

| Role | Mô tả |
|---|---|
| `ROLE_ADMIN` | Quản trị hệ thống, cấu hình giá và billing, duyệt khiếu nại, cấp passcode mở khoá chỉ số. |
| `ROLE_OWNER` | Chủ đầu tư (Host): duyệt giá nhà/hợp đồng, xem tài chính, hoàn cọc. |
| `ROLE_MANAGER` | Quản lý vận hành theo khu vực: nhận nhà, onboard khách, ghi chỉ số, xử lý bảo trì, trả phòng. |
| `ROLE_TENANT` | Khách thuê: xem hợp đồng, thanh toán hoá đơn, gửi yêu cầu bảo trì và trả phòng. |
| `ROLE_USER` | Người dùng công khai: xem nhà trống, đăng ký xem phòng. |

## Công nghệ sử dụng

- **Java 21**, **Spring Boot 4** (Web, Data JPA, Security, Validation, WebSocket)
- **PostgreSQL** (Hibernate `ddl-auto: update` tự tạo/cập nhật bảng)
- **JWT** (jjwt) cho xác thực
- **MapStruct**, **Lombok**
- **springdoc-openapi** (Swagger UI)
- **Apache POI** + **XDocReport**: đọc Excel import, sinh hợp đồng DOCX → PDF
- **ZXing**: sinh mã QR thiết bị
- **ONNX Runtime**: nhận diện thiết bị bằng model local (fallback)
- Dịch vụ ngoài: **PayOS** (thanh toán), **Twilio Verify** (OTP), **OCR.space** (đọc chỉ số), **Google Cloud Vision** và **Gemini** (phân tích ảnh), **Cloudinary** (FE upload ảnh/file, BE chỉ nhận URL)
- **Docker** để build và deploy

## Cấu trúc thư mục

```
slms2026/
├── src/main/java/com/sep490/slms2026/
│   ├── config/        # Security, CORS, WebSocket, OpenAPI, DataSeeder, migration
│   ├── controller/    # REST controllers (/api/v1/...)
│   ├── service/       # Business logic (+ impl/, pricing/)
│   ├── repository/    # Spring Data JPA
│   ├── entity/        # JPA entities
│   ├── dto/           # Request/Response DTOs
│   ├── mapper/        # MapStruct mappers
│   ├── security/      # JWT filter, UserDetailsService
│   ├── imports/       # Import Excel hàng loạt
│   ├── vision/        # Google Vision / ONNX local
│   ├── enums/ event/ exception/ util/ constant/
├── src/main/resources/
│   ├── application.yaml
│   ├── db/            # schema.sql, seed.sql, script migration thủ công
│   ├── models/        # labels.txt (+ model ONNX nếu có)
│   └── templates/contract/  # Template hợp đồng DOCX
├── scripts/           # Script SQL seed/cleanup demo, script sinh file Excel
├── docs/              # Spec, ERD, sơ đồ UML/sequence, file Excel import mẫu
├── postman/           # Postman collection
├── Dockerfile
└── pom.xml
```

---

## Cài đặt và chạy local

### 1. Yêu cầu

| Phần mềm | Phiên bản |
|---|---|
| JDK | 21 |
| PostgreSQL | 14 trở lên (hoặc chạy bằng Docker) |
| Git | bất kỳ |
| Maven | không bắt buộc, project có sẵn Maven Wrapper (`mvnw`) |

### 2. Clone source

```bash
git clone https://github.com/gowahskoja113/Sub-leasing-managemant-system.git
cd Sub-leasing-managemant-system
git checkout dev
```

### 3. Tạo database PostgreSQL

**Cách A — dùng Docker (khuyên dùng):**

```bash
docker run -d --name slms-postgres \
  -e POSTGRES_DB=slms2026 \
  -e POSTGRES_USER=slms \
  -e POSTGRES_PASSWORD=slms123 \
  -p 5432:5432 \
  postgres:16
```

**Cách B — PostgreSQL cài sẵn trên máy:**

```sql
CREATE DATABASE slms2026;
CREATE USER slms WITH PASSWORD 'slms123';
GRANT ALL PRIVILEGES ON DATABASE slms2026 TO slms;
ALTER DATABASE slms2026 OWNER TO slms;
```

Không cần chạy `schema.sql` thủ công: khi app khởi động, Hibernate tự tạo bảng và `DataSeeder` tự tạo khu vực, danh mục thiết bị, danh mục cải tạo và tài khoản demo.

### 4. Tạo file `.env`

Tạo file `.env` ở thư mục gốc project (cùng cấp `pom.xml`). App tự đọc file này khi khởi động. File đã nằm trong `.gitignore`, **không commit file này**.

Cấu hình tối thiểu để chạy được:

```env
DB_URL=jdbc:postgresql://localhost:5432/slms2026
DB_USERNAME=slms
DB_PASSWORD=slms123

# Tối thiểu 32 ký tự (HMAC-SHA256)
JWT_SECRET=doi-chuoi-nay-thanh-mot-chuoi-bi-mat-dai-it-nhat-32-ky-tu
```

Các tính năng thanh toán, OTP, OCR, nhận diện ảnh cần thêm key tương ứng, xem bảng [Biến môi trường](#biến-môi-trường). Thiếu các key này app vẫn chạy, chỉ các tính năng đó báo lỗi khi gọi.

### 5. Chạy ứng dụng

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
./mvnw spring-boot:run
```

Hoặc build ra file JAR rồi chạy:

```bash
./mvnw -DskipTests package
java -jar target/slms2026-0.0.1-SNAPSHOT.jar
```

Mở IntelliJ IDEA cũng được: import project dạng Maven, chọn JDK 21, chạy class `Slms2026Application`.

### 6. Kiểm tra

- API: `http://localhost:8080/api/v1/...`
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Thử đăng nhập:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin01","password":"123456"}'
```

Response trả về JWT, gửi kèm header `Authorization: Bearer <token>` cho các request tiếp theo.

### 7. (Tuỳ chọn) Nạp dữ liệu demo

Thư mục `scripts/` có các script SQL nạp dữ liệu demo đầy đủ (nhà, phòng, hợp đồng, hoá đơn...). Chạy **sau khi app đã khởi động ít nhất một lần** (để bảng đã được tạo):

```bash
psql -h localhost -U slms -d slms2026 -f scripts/capstone-defense-demo-seed.sql
```

Gỡ dữ liệu demo:

```bash
psql -h localhost -U slms -d slms2026 -f scripts/capstone-defense-demo-cleanup.sql
```

---

## Triển khai bằng Docker (VPS)

`Dockerfile` build 2 giai đoạn: Maven build JAR, sau đó chạy bằng JRE 21. Heap giới hạn `-Xmx1g`, timezone `Asia/Ho_Chi_Minh`.

### Lần đầu

```bash
# Network chung cho API và database
docker network create slms-net

# PostgreSQL
docker run -d \
  --name slms-postgres \
  --restart unless-stopped \
  --network slms-net \
  -e POSTGRES_DB=slms2026 \
  -e POSTGRES_USER=slms \
  -e POSTGRES_PASSWORD=<mat-khau-manh> \
  -v slms-pgdata:/var/lib/postgresql/data \
  postgres:16

# Clone source
cd /root
git clone https://github.com/gowahskoja113/Sub-leasing-managemant-system.git slms2026
```

Tạo file env cho container (đặt ngoài thư mục source để không bị ghi đè khi pull):

```bash
nano /root/slms-api.env
```

Nội dung tối thiểu (lưu ý `DB_URL` dùng tên container `slms-postgres` thay cho `localhost`):

```env
DB_URL=jdbc:postgresql://slms-postgres:5432/slms2026
DB_USERNAME=slms
DB_PASSWORD=<mat-khau-manh>
JWT_SECRET=<chuoi-bi-mat-it-nhat-32-ky-tu>
APP_PUBLIC_BASE_URL=https://api.ten-mien-cua-ban.com
PAYOS_AMOUNT_DIVISOR=1
```

Trong nano: `Ctrl + O` rồi `Enter` để lưu, `Ctrl + X` để thoát.

Mỗi dòng có dạng `KEY=value`, không có khoảng trắng quanh dấu `=` và không bọc giá trị trong dấu nháy (`--env-file` giữ nguyên dấu nháy như một phần giá trị).

### Pull code mới và rebuild

```bash
cd /root/slms2026

git fetch origin
git checkout dev
git pull origin dev
git log -1 --oneline

docker build -t slms-api:latest .

docker stop slms-api
docker rm slms-api

docker run -d \
  --name slms-api \
  --restart unless-stopped \
  --network slms-net \
  -p 127.0.0.1:8080:8080 \
  --env-file /root/slms-api.env \
  slms-api:latest

docker ps --filter name=slms-api
docker logs -f --tail 80 slms-api
```

Lưu ý:

- Port chỉ mở cho `127.0.0.1`, nên cần reverse proxy (Nginx/Caddy) phía trước để public ra HTTPS. Nhớ bật hỗ trợ WebSocket upgrade cho đường dẫn `/ws`.
- Sửa `/root/slms-api.env` xong phải **xoá và chạy lại container** (`docker stop` → `docker rm` → `docker run`). `docker restart` không nạp lại env. Không cần build lại image.
- Kiểm tra container đã nhận biến chưa: `docker exec slms-api printenv | grep TEN_BIEN`.
- Thoát xem log bằng `Ctrl + C`, container vẫn chạy.
- Nếu frontend chạy ở domain khác `localhost` hoặc `*.vercel.app`, thêm domain đó vào `app.cors.allowed-origin-patterns` trong `application.yaml`.

---

## Biến môi trường

Tất cả biến được đọc từ `.env` (khi chạy local) hoặc `--env-file` (khi chạy Docker).

### Bắt buộc

| Biến | Mô tả |
|---|---|
| `DB_URL` | JDBC URL PostgreSQL, ví dụ `jdbc:postgresql://localhost:5432/slms2026` |
| `DB_USERNAME` | User database |
| `DB_PASSWORD` | Mật khẩu database |
| `JWT_SECRET` | Khoá ký JWT, tối thiểu 32 ký tự |

### Tuỳ chọn

| Biến | Mặc định | Mô tả |
|---|---|---|
| `PORT` | `8080` | Port HTTP |
| `JWT_EXPIRATION` | `864000000` | Thời hạn token (ms), mặc định 10 ngày |
| `APP_PUBLIC_BASE_URL` | `http://localhost:8080` | URL public của API, dùng sinh link ảnh |
| `PROPERTY_IMAGES_DIR` | `uploads/properties` | Thư mục lưu ảnh nhà upload trực tiếp |
| `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | trống | Key PayOS, lấy ở dashboard [payos.vn](https://payos.vn) |
| `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` | `slms://payment-success`, `slms://payment-cancel` | URL quay về sau thanh toán |
| `PAYOS_AMOUNT_DIVISOR` | `1000` | Chia số tiền gửi PayOS để test sandbox. **Production đặt `1`** |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_VERIFY_SERVICE_SID` | trống | Gửi OTP qua Twilio Verify |
| `TWILIO_OTP_EXPIRY_MINUTES`, `TWILIO_OTP_MAX_ATTEMPTS` | `5`, `5` | Hạn và số lần thử OTP |
| `OCR_SPACE_API_KEY` | `helloworld` (key demo, giới hạn) | Key [OCR.space](https://ocr.space/ocrapi) đọc chỉ số đồng hồ |
| `VISION_PROVIDER` | `auto` | `google`, `local` hoặc `auto` (Google trước, lỗi thì dùng local) |
| `GOOGLE_VISION_API_KEY` | trống | Key Google Cloud Vision |
| `VISION_GOOGLE_TIMEOUT_SECONDS` | `8` | Timeout gọi Google Vision |
| `VISION_LOCAL_MODEL_PATH` | `classpath:models/equipment-mobilenetv3.onnx` | Model ONNX local. Không có file thì chế độ local tự tắt |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | trống, `gemini-2.0-flash` | Gemini mô tả hiện trạng phòng từ ảnh |
| `MANAGER_OVERRIDE_PASSCODE_TTL_MINUTES`, `MANAGER_OVERRIDE_TTL_MINUTES` | `10`, `15` | Thời hạn passcode mở khoá chỉ số |
| `BILLING_STARTUP_SWEEP` | `true` | Chạy bù job billing khi app khởi động |
| `JPA_SHOW_SQL` | `false` | In câu SQL ra log khi debug |

---

## Tài khoản demo

`DataSeeder` tự tạo các tài khoản sau khi app khởi động lần đầu. Mật khẩu chung: **`123456`**.

| Username | Role | Số điện thoại |
|---|---|---|
| `admin01`, `admin02` | Admin | `0901000001`, `0901000002` |
| `owner` | Owner | `0902000001` |
| `manager01`, `manager02` | Manager | `0903000001`, `0903000002` |
| `user` | User | `0905000001` |

Tài khoản khách thuê (Tenant) được tạo khi Manager onboard khách và khách kích hoạt bằng OTP, hoặc có sẵn nếu đã nạp script demo trong `scripts/`.

**Đổi mật khẩu các tài khoản này trước khi đưa lên môi trường thật.**

---

## Tài liệu API

- **Swagger UI**: `/swagger-ui.html`
- **OpenAPI JSON**: `/v3/api-docs`
- **Postman**: import file `postman/Sub-leasing managemant system.postman_collection.json`
- **WebSocket**: endpoint STOMP `/ws`, gửi JWT khi CONNECT

## Tài liệu thiết kế

| Đường dẫn | Nội dung |
|---|---|
| `docs/uml/erd/` | ERD tổng và theo module (PlantUML) |
| `docs/uml/main-flows/` | Class, sequence, state diagram cho các luồng chính (F1–F6) |
| `docs/uml/SLMS-class-diagram-flows-uml/` | Sơ đồ luồng dạng ảnh PNG |
| `docs/sequence/` | Sequence diagram chi tiết cho chỉ số và hoá đơn |
| `docs/*-spec.md` | Đặc tả bảo trì, mã nhà, realtime socket... |
| `docs/SLMS2026_import_*.xlsx` | File Excel mẫu để import hàng loạt |

---

## Xử lý sự cố thường gặp

| Lỗi | Cách xử lý |
|---|---|
| `Could not resolve placeholder 'DB_URL'` | Chưa có file `.env` ở thư mục gốc, hoặc chạy app từ thư mục khác. Chạy lệnh từ thư mục chứa `pom.xml`. |
| `Connection refused` tới PostgreSQL | Kiểm tra database đang chạy, đúng host/port. Trong Docker phải dùng tên container (`slms-postgres`) chứ không dùng `localhost`. |
| `WeakKeyException` khi đăng nhập | `JWT_SECRET` ngắn hơn 32 ký tự. |
| Frontend bị lỗi CORS | Thêm domain frontend vào `app.cors.allowed-origin-patterns` trong `application.yaml`. |
| Số tiền trên PayOS nhỏ hơn 1000 lần | `PAYOS_AMOUNT_DIVISOR` đang để mặc định `1000` (chế độ sandbox). Đặt `1` cho production. |
| Sửa env trên VPS nhưng app không nhận | Phải xoá và tạo lại container, `docker restart` không đủ. |
| Hết RAM trên VPS | Image đặt sẵn `-Xmx1g`. Với VPS nhỏ hơn 2 GB có thể giảm heap trong `ENTRYPOINT` của `Dockerfile`. |
