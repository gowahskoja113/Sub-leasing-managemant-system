# Rental calculation flow modification
_Exported on 6/8/2026 at 23:52:45 GMT+7 from Cursor (3.6.31)_

---

**User**

đọc file md trong docs, bạn hãy giúp tui sửa lại luồng như sau: trong luồng inbound thì việc cải tạo nó sẽ cho là optional, nếu có cải tạo thì nhập, không cải tạo thì thôi. phần nhập hợp đồng thì chắc chỉ nhập số tiền đã thuê với chủ sở hữu gốc (kiểu 2 tỷ/2 năm) không cần deposit. rồi dựa vào số tiền đó cộng với số tiền equipment nếu phải chi thêm + thêm tiền cải tạo nếu có thì nó sẽ ra tổng rồi tính khấu hao mỗi tháng theo thời gian trong hợp đồng như ví dụ trên thì là 2 tỷ + equipemt + cải tạo(nếu có) chia cho 24 tháng thì mình sẽ ra được giá tiền dự kiến cho thuê (chưa tính lời, còn nếu tình lời thì sẽ cho 10% trên kết quả đó). còn cách tính của căn nhà bỏ giữa chừng và sửa tiếp để cho thuê lại thì cộng dồn lên như cũ

---

**Cursor**

Đang đọc file hướng dẫn trong `docs` để nắm luồng hiện tại, rồi cập nhật theo yêu cầu của bạn.


Đang xem backend tính khấu hao và cấu trúc hợp đồng inbound để cập nhật tài liệu cho khớp.


Đang cập nhật tài liệu: gộp luồng inbound, đổi công thức tính giá và giữ luồng tái đầu tư như cũ.


Đã cập nhật `docs/property-onboarding-frontend-guide.md` theo luồng bạn mô tả. Tóm tắt thay đổi chính:

## Luồng Inbound (gộp thành 1 luồng)

- **Cải tạo optional**: toggle "Có cải tạo?" — có thì nhập `cost`, không thì bỏ qua step, không block wizard.
- **Hợp đồng inbound**: chỉ nhập **tổng tiền thuê cả kỳ** (`totalRentAmount`, vd: 2 tỷ / 2 năm) + `startDate`/`endDate`. **Không nhập cọc** trả chủ gốc.

## Công thức tính giá mới (Luồng A)

```
totalInvestment = totalRentAmount + equipment(PURCHASED) + renovation (0 nếu không có)
monthlyBreakEven = totalInvestment / số tháng HĐ

suggestedMinPrice          = monthlyBreakEven     → giá hoàn vốn/tháng (chưa lời)
suggestedPriceWithProfit   = monthlyBreakEven × 1.10  → gợi ý có lời 10%
```

Ví dụ 2 tỷ + 200tr equipment + 300tr cải tạo / 24 tháng ≈ **104,2 triệu/tháng** hoàn vốn, **~114,6 triệu/tháng** nếu tính lời 10%.

## Luồng tái đầu tư (Luồng B — giữ như cũ)

Căn **bỏ giữa chừng** hoặc **cải tạo thêm để cho thuê lại**: vốn mới **cộng dồn** với phần chưa hoàn, chia cho số tháng HĐ còn lại — giữ logic cũ (có `remainingInvestment`, chi phí vận hành nếu có).

## Lưu ý backend

BE hiện vẫn dùng `baseRentPrice` + `depositAmount` và công thức cũ (`baseRent + khấu hao + operating cost`). Doc đã ghi rõ **target nghiệp vụ** vs **API hiện tại** để FE thiết kế UI đúng hướng, và có bảng trong mục 11 về những gì BE cần migrate (`totalRentAmount`, `suggestedPriceWithProfit`, công thức mới).

Bạn có muốn mình cập nhật luôn backend (`DepreciationServiceImpl`, `InboundContract`, DTO) cho khớp doc không?

---

**User**

đổi code backend theo những gì đã phân tích trên luôn

---

**Cursor**

Đang rà soát toàn bộ backend liên quan inbound contract và khấu hao để cập nhật theo tài liệu.


Đang cập nhật entity, DTO, service và logic tính khấu hao.


---

**User**

xin json và url test

---

**Cursor**

Đang đọc các controller và cấu hình auth để lấy URL và JSON mẫu test chính xác.


Dưới đây là bộ URL + JSON để test luồng onboarding mới trên **Postman / Swagger** (`http://localhost:8080/swagger-ui.html`).

**Header chung** (mọi API trừ auth):

```
Authorization: Bearer <token>
Content-Type: application/json
```

---

## 0. Login lấy token

**POST** `http://localhost:8080/api/v1/auth/login`

```json
{
  "username": "admin",
  "password": "123456"
}
```

Response → lấy `token`.

---

## Kịch bản A — Nhà nguyên căn (có equipment + cải tạo)

Ví dụ tính toán:
- Thuê HĐ: **2 tỷ / 24 tháng**
- Equipment: **200 triệu**
- Cải tạo: **300 triệu**
- `totalInvestment` = 2.500.000.000
- `suggestedMinPrice` (hoàn vốn) ≈ **104.166.667**/tháng
- `suggestedPriceWithProfit` (+10%) ≈ **114.583.334**/tháng

### 1. Tạo Property

**POST** `http://localhost:8080/api/v1/properties`

```json
{
  "propertyName": "Nhà 123 Lê Lợi",
  "address": "123 Lê Lợi, Q1",
  "descriptions": "Nhà nguyên căn test",
  "zoneId": "YOUR_ZONE_UUID",
  "wholeHouse": true,
  "areaSize": 80.0,
  "managedBy": 1,
  "imageUrls": []
}
```

→ Lưu `propertyId` (vd: `1`)

---

### 2. Thêm thiết bị

**POST** `http://localhost:8080/api/v1/properties/1/equipments`

```json
{
  "name": "Máy lạnh",
  "source": "PURCHASED",
  "purchasePrice": 8000000
}
```

```json
{
  "name": "Tủ lạnh",
  "source": "PURCHASED",
  "purchasePrice": 5000000
}
```

*(Không gửi `roomId` với nhà nguyên căn)*

---

### 3. Thêm cải tạo *(optional — bỏ qua step này nếu không cải tạo)*

**POST** `http://localhost:8080/api/v1/properties/1/renovations`

```json
{
  "description": "Sơn lại tường, sửa WC",
  "cost": 300000000,
  "completed": true
}
```

---

### 4. Ký Inbound Contract *(mới — chỉ tổng tiền thuê, không cọc)*

**POST** `http://localhost:8080/api/v1/properties/1/inbound-contract`

```json
{
  "contractCode": "INB-2026-001",
  "ownerName": "Nguyễn Văn A",
  "totalRentAmount": 2000000000,
  "startDate": "2026-01-01",
  "endDate": "2028-01-01",
  "contractScanUrl": "https://example.com/contract.pdf"
}
```

**GET** `http://localhost:8080/api/v1/properties/1/inbound-contract`

---

### 5. Tính khấu hao

**POST** `http://localhost:8080/api/v1/properties/1/depreciation/calculate`

```json
{}
```

*(body rỗng `{}` hoặc không gửi body đều được)*

**GET** `http://localhost:8080/api/v1/properties/1/depreciation`

Response kỳ vọng (nhà nguyên căn):

```json
{
  "propertyId": 1,
  "pricingScope": "WHOLE_HOUSE",
  "wholeHouseResult": {
    "totalRentAmount": 2000000000,
    "totalEquipmentCost": 13000000,
    "totalRenovationCost": 300000000,
    "totalInvestment": 2313000000,
    "contractMonths": 24,
    "monthlyBreakEven": 96375000,
    "suggestedMinPrice": 96375000,
    "suggestedPriceWithProfit": 106012500
  }
}
```

---

### 6. Confirm giá

**POST** `http://localhost:8080/api/v1/properties/1/activation/confirm`

```json
{
  "propertyPrice": 110000000,
  "propertyDeposit": 220000000
}
```

- `propertyPrice` ≥ `suggestedMinPrice` (hoàn vốn)
- `propertyDeposit` là **cọc cho khách thuê**, không liên quan HĐ inbound

---

## Kịch bản B — Nhà chia phòng (không cải tạo)

### 1. Tạo Property

**POST** `http://localhost:8080/api/v1/properties`

```json
{
  "propertyName": "Chung cư mini ABC",
  "address": "456 Nguyễn Trãi",
  "descriptions": "Nhà chia phòng test",
  "zoneId": "YOUR_ZONE_UUID",
  "wholeHouse": false,
  "areaSize": 100.0,
  "totalRooms": 2,
  "managedBy": 1,
  "imageUrls": []
}
```

→ `propertyId` = `2`

---

### 2. Thêm phòng

**POST** `http://localhost:8080/api/v1/properties/2/rooms`

```json
{
  "roomNumber": "P101",
  "area": 25.0,
  "propertyType": "INDIVIDUAL_ROOM",
  "maxOccupants": 2
}
```

```json
{
  "roomNumber": "P102",
  "area": 30.0,
  "propertyType": "INDIVIDUAL_ROOM",
  "maxOccupants": 2
}
```

**GET** `http://localhost:8080/api/v1/properties/2/rooms`

---

### 3. Thiết bị chung tòa

**POST** `http://localhost:8080/api/v1/properties/2/equipments`

```json
{
  "name": "Camera an ninh",
  "source": "PURCHASED",
  "purchasePrice": 10000000
}
```

Thiết bị riêng phòng P101 (`roomId` lấy từ GET rooms):

```json
{
  "name": "Máy lạnh P101",
  "source": "PURCHASED",
  "purchasePrice": 8000000,
  "roomId": 1
}
```

---

### 4. Inbound Contract

**POST** `http://localhost:8080/api/v1/properties/2/inbound-contract`

```json
{
  "contractCode": "INB-2026-002",
  "ownerName": "Trần Thị B",
  "totalRentAmount": 1200000000,
  "startDate": "2026-01-01",
  "endDate": "2028-01-01"
}
```

---

### 5. Tính khấu hao

**POST** `http://localhost:8080/api/v1/properties/2/depreciation/calculate`

```json
{}
```

**GET** `http://localhost:8080/api/v1/properties/2/depreciation`

Response kỳ vọng:

```json
{
  "propertyId": 2,
  "pricingScope": "ROOM",
  "roomResults": [
    {
      "roomId": 1,
      "roomNumber": "P101",
      "totalRentAmount": 545454545.45,
      "monthlyBreakEven": 23484848.48,
      "suggestedMinPrice": 23484848.48,
      "suggestedPriceWithProfit": 25833333.33
    },
    {
      "roomId": 2,
      "roomNumber": "P102",
      "suggestedMinPrice": 28181818.18,
      "suggestedPriceWithProfit": 31000000.00
    }
  ]
}
```

*(Số liệu phụ thuộc diện tích phân bổ — P102 rộng hơn nên giá cao hơn)*

---

### 6. Confirm giá từng phòng

**POST** `http://localhost:8080/api/v1/properties/2/activation/confirm`

```json
{
  "roomPrices": [
    { "roomId": 1, "price": 26000000, "deposit": 26000000 },
    { "roomId": 2, "price": 32000000, "deposit": 32000000 }
  ]
}
```

---

## API GET tham khảo

| Mục đích | URL |
|----------|-----|
| Chi tiết property | `GET /api/v1/properties/{id}` |
| Danh sách phòng | `GET /api/v1/properties/{id}/rooms` |
| Thiết bị | `GET /api/v1/properties/{id}/equipments` |
| Cải tạo | `GET /api/v1/properties/{id}/renovations` |
| HĐ inbound | `GET /api/v1/properties/{id}/inbound-contract` |
| Khấu hao | `GET /api/v1/properties/{id}/depreciation` |

---

## Lỗi thường gặp khi test

| Lỗi | Nguyên nhân |
|-----|-------------|
| 401 | Thiếu / sai JWT |
| "Phải ký hợp đồng inbound trước..." | Chưa gọi bước 4 |
| "Thiết bị PURCHASED phải có purchasePrice..." | Equipment thiếu giá |
| "Cải tạo phải có cost..." | Có renovation nhưng chưa nhập `cost` |
| "Giá thuê xác nhận ... thấp hơn giá tối thiểu..." | `propertyPrice` / `price` < `suggestedMinPrice` |

---

## Curl mẫu nhanh (nhà nguyên căn)

```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"123456\"}"

# Tính khấu hao (thay TOKEN và propertyId)
curl -X POST http://localhost:8080/api/v1/properties/1/depreciation/calculate \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d "{}"
```

Thay `YOUR_ZONE_UUID` và `managedBy` bằng ID thật trong DB của bạn. Nếu cần, mình có thể gửi thêm collection Postman import được.

---

**User**

{
    "error": "Bad Request",
    "message": "could not execute statement [ERROR: null value in column \"base_rent\" of relation \"depreciation_results\" violates not-null constraint\n  Detail: Failing row contains (2, null, 2026-06-08 23:46:19.050243, 24, 84000004.17, null, null, 84000004.17, 16000100.00, 2016000100.00, 0.00, 4, null, 92400004.59, 2000000000.00).] [insert into depreciation_results (calculated_at,contract_months,inbound_contract_id,monthly_depreciation,room_id,suggested_min_price,suggested_price_with_profit,total_equipment_cost,total_investment,total_renovation_cost,total_rent_amount) values (?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into depreciation_results (calculated_at,contract_months,inbound_contract_id,monthly_depreciation,room_id,suggested_min_price,suggested_price_with_profit,total_equipment_cost,total_investment,total_renovation_cost,total_rent_amount) values (?,?,?,?,?,?,?,?,?,?,?)]; constraint [base_rent]",
    "status": 400
} tôi chạy http://localhost:8080/api/v1/properties/1/depreciation/calculate thì nó quăng ra lỗi đó

---

**Cursor**

Cột cũ `base_rent` vẫn còn NOT NULL trong DB nhưng entity mới không ghi vào — đang kiểm tra schema và thêm migration để xử lý.


DB vẫn giữ cột cũ `base_rent` (NOT NULL) trong khi entity mới không map — thêm migration tự chạy khi start app để drop các cột legacy.


Nguyên nhân: Hibernate `ddl-auto: update` **thêm cột mới** (`total_rent_amount`, `suggested_price_with_profit`) nhưng **không xóa cột cũ** `base_rent`, `original_deposit`, `monthly_operating_cost`. Cột `base_rent` vẫn `NOT NULL` nên INSERT bị fail.

## Cách xử lý

### Cách 1 — Restart app (khuyến nghị)

Đã thêm `DatabaseSchemaMigration` — tự drop các cột legacy khi app khởi động:

- `depreciation_results`: `base_rent`, `original_deposit`, `monthly_operating_cost`
- `inbound_contracts`: `base_rent_price`, `deposit_amount`

**Restart Spring Boot**, rồi gọi lại:

```
POST http://localhost:8080/api/v1/properties/1/depreciation/calculate
{}
```

Log startup sẽ có dòng kiểu: `Dropped legacy column depreciation_results.base_rent`

---

### Cách 2 — Chạy SQL thủ công ngay (không cần restart)

Trong pgAdmin / DBeaver:

```sql
ALTER TABLE depreciation_results DROP COLUMN IF EXISTS base_rent;
ALTER TABLE depreciation_results DROP COLUMN IF EXISTS original_deposit;
ALTER TABLE depreciation_results DROP COLUMN IF EXISTS monthly_operating_cost;

ALTER TABLE inbound_contracts DROP COLUMN IF EXISTS base_rent_price;
ALTER TABLE inbound_contracts DROP COLUMN IF EXISTS deposit_amount;
```

File này cũng nằm tại `src/main/resources/db/manual-migration-v2.sql`.

---

Sau khi drop xong, response kỳ vọng với data của bạn (2 tỷ / 24 tháng + equipment ~16tr):

```json
{
  "propertyId": 1,
  "pricingScope": "WHOLE_HOUSE",
  "wholeHouseResult": {
    "totalRentAmount": 2000000000,
    "totalEquipmentCost": 16000100,
    "totalRenovationCost": 0,
    "totalInvestment": 2016000100,
    "contractMonths": 24,
    "monthlyBreakEven": 84000004.17,
    "suggestedMinPrice": 84000004.17,
    "suggestedPriceWithProfit": 92400004.59
  }
}
```

