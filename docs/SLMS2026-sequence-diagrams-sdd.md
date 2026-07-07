# Sequence Diagram — Report 4 (Software Design Document)

Tài liệu bổ sung cho **Report 4 – Software Design Document**, mô tả các luồng nghiệp vụ trong §3 bằng **sequence diagram dễ đọc**: hành động diễn đạt bằng ngôn ngữ nghiệp vụ, **không** ghi URL API.

**Phạm vi sprint hiện tại:** §3.1 – §3.4 (4 luồng chính).

---

## Quy ước chung

| Thành phần | Ý nghĩa |
|------------|---------|
| **Admin** | Quản trị hệ thống (`ROLE_ADMIN`) |
| **Host** | Chủ nhà (`ROLE_OWNER`) |
| **Manager** | Quản lý vận hành (`ROLE_MANAGER`) |
| **Khách thuê** | Người thuê nhà (`ROLE_TENANT`) |
| **Ứng dụng Web** | Cổng quản trị React (Admin / Host) |
| **Ứng dụng Mobile** | App React Native (Manager / Khách thuê) |
| **Hệ thống** | Backend xử lý nghiệp vụ & lưu trữ |
| **Cơ sở dữ liệu** | Database quan hệ |
| **PayOS** | Cổng thanh toán / QR |
| **Cloudinary** | Lưu trữ ảnh & file |
| **Dịch vụ OCR** | Đọc chỉ số đồng hồ / hóa đơn từ ảnh |
| **Thông báo** | Push notification tới điện thoại |

---

## 3.1 Tiếp khách (Tenant Onboarding)

### 3.1.2 Tạo hợp đồng nháp & gán Manager

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Web as Ứng dụng Web
    participant OCR as Dịch vụ OCR (file HĐ)
    participant Hệ thống
    participant Cloudinary
    participant DB as Cơ sở dữ liệu
    participant Thông báo

    Note over Admin,Thông báo: Chuẩn bị thông tin khách & căn nhà

    Admin->>Web: Mở màn hình tiếp khách mới
    Web->>Hệ thống: Lấy danh sách nhà đang cho thuê
    Hệ thống->>DB: Truy vấn nhà & phòng còn trống
    DB-->>Hệ thống: Danh sách nhà / phòng
    Hệ thống-->>Web: Hiển thị nhà & phòng khả dụng

    Admin->>Web: Chọn nhà, phòng (nếu thuê phòng)
    Admin->>Web: Nhập số điện thoại khách
    Web->>Hệ thống: Tra cứu khách theo SĐT
    Hệ thống->>DB: Tìm hồ sơ khách
    Hệ thống-->>Web: Thông tin khách (nếu đã có) hoặc form trống

    opt Upload file hợp đồng cũ (PDF/DOCX)
        Admin->>Web: Tải lên file hợp đồng
        Web->>OCR: Trích xuất tên, CCCD, ngày, giá thuê…
        OCR-->>Web: Điền sẵn vào form
    end

    Admin->>Web: Nhập / chỉnh sửa thông tin HĐ (giá, cọc, ngày vào…)
    Admin->>Web: Bấm "Tạo hợp đồng nháp"

    Web->>Hệ thống: Gửi thông tin tạo HĐ nháp
    Hệ thống->>Hệ thống: Kiểm tra phòng trống, ngày hợp lệ
    Hệ thống->>DB: Lưu HĐ trạng thái Nháp (chưa có tài khoản khách)
    DB-->>Hệ thống: Mã hợp đồng
    Hệ thống-->>Web: HĐ nháp đã tạo
    Web-->>Admin: Hiển thị chi tiết HĐ nháp

    Admin->>Web: Chọn Manager đón khách & ngày tiếp khách dự kiến
    Web->>Hệ thống: Gán Manager cho HĐ
    Hệ thống->>DB: Cập nhật Manager & ngày tiếp khách
    Hệ thống->>Thông báo: Gửi thông báo "Được giao khách mới"
    Thông báo-->>Manager: Push notification
    Hệ thống-->>Web: Xác nhận gán Manager
    Web-->>Admin: Hoàn tất — Manager sẽ tiếp khách trên app
```

### 3.1.3 Ghi nhận hiện trạng phòng & chỉ số đồng hồ ban đầu

> *Thiết kế mục tiêu — chưa triển khai đầy đủ trên `ResumeContractScreen`.*

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant Mobile as Ứng dụng Mobile
    participant Cloudinary
    participant OCR as Dịch vụ OCR
    participant Hệ thống
    participant DB as Cơ sở dữ liệu

    Note over Manager,DB: Manager tiếp khách tại phòng — ghi nhận bàn giao

    Manager->>Mobile: Mở hợp đồng được giao
    Mobile->>Hệ thống: Lấy chi tiết HĐ nháp
    Hệ thống->>DB: Đọc thông tin HĐ
    Hệ thống-->>Mobile: Chi tiết HĐ & phòng

    Manager->>Mobile: Chụp ảnh hiện trạng phòng (trước khi vào ở)
    Mobile->>Cloudinary: Tải ảnh lên
    Cloudinary-->>Mobile: Đường dẫn ảnh

    Manager->>Mobile: Chụp ảnh đồng hồ điện & nước
    Mobile->>Cloudinary: Tải ảnh đồng hồ lên
    Cloudinary-->>Mobile: Đường dẫn ảnh

    Mobile->>OCR: Gửi ảnh đồng hồ để đọc số
    OCR-->>Mobile: Chỉ số điện & nước ban đầu

    Manager->>Mobile: Nhập thành viên trong hộ (nếu có)
    Manager->>Mobile: Bấm "Lưu thông tin bàn giao"

    Mobile->>Hệ thống: Cập nhật HĐ nháp (ảnh phòng, chỉ số, hộ gia đình)
    Hệ thống->>DB: Lưu ảnh hiện trạng & chỉ số ban đầu
    Hệ thống-->>Mobile: Xác nhận đã lưu
    Mobile-->>Manager: Sẵn sàng bước thu cọc & kích hoạt
```

### 3.1.4 Thu cọc, xác nhận OTP & kích hoạt hợp đồng

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    actor Khách as Khách thuê
    participant Mobile as Ứng dụng Mobile
    participant Hệ thống
    participant PayOS
    participant OTP as Dịch vụ OTP (SMS)
    participant DB as Cơ sở dữ liệu

    Note over Manager,DB: Thu tiền cọc

    Manager->>Mobile: Bấm "Thu cọc"
    Mobile->>Hệ thống: Yêu cầu tạo thanh toán cọc
    Hệ thống->>Hệ thống: Chuyển HĐ sang trạng thái Chờ tiếp khách
    Hệ thống->>PayOS: Tạo link / mã QR thanh toán
    PayOS-->>Hệ thống: QR & link thanh toán
    Hệ thống->>DB: Lưu thông tin thanh toán (đang chờ)
    Hệ thống-->>Mobile: Hiển thị QR
    Mobile-->>Khách: Quét QR & thanh toán trên PayOS

    alt PayOS báo thanh toán thành công (tự động)
        PayOS->>Hệ thống: Xác nhận đã nhận tiền
        Hệ thống->>DB: Đánh dấu đã thu cọc
        Hệ thống->>Hệ thống: Tạo file hợp đồng Word (bản nháp)
    else Manager kiểm tra thủ công
        Manager->>Mobile: Bấm "Kiểm tra thanh toán"
        Mobile->>Hệ thống: Đồng bộ trạng thái với PayOS
        Hệ thống->>PayOS: Hỏi trạng thái giao dịch
        PayOS-->>Hệ thống: Đã thanh toán
        Hệ thống->>DB: Đánh dấu đã thu cọc
        Hệ thống->>Hệ thống: Tạo file hợp đồng Word (bản nháp)
    end

    Hệ thống-->>Mobile: Cọc đã thu — có thể tải HĐ Word
    Mobile-->>Manager: Hiển thị file HĐ nháp

    Note over Manager,DB: Kích hoạt hợp đồng bằng OTP

    Manager->>Mobile: Bấm "Gửi mã xác nhận"
    Mobile->>Hệ thống: Yêu cầu gửi OTP
    Hệ thống->>OTP: Gửi SMS mã OTP tới SĐT khách
    OTP-->>Khách: Nhận SMS mã OTP
    Khách-->>Manager: Đọc mã OTP

    Manager->>Mobile: Nhập mã OTP & bấm "Kích hoạt HĐ"
    Mobile->>Hệ thống: Xác nhận OTP & kích hoạt
    Hệ thống->>OTP: Kiểm tra mã OTP
    OTP-->>Hệ thống: Hợp lệ

    Hệ thống->>DB: Tạo tài khoản khách thuê (lần đầu)
    Hệ thống->>DB: Chuyển HĐ sang Đang hiệu lực
    Hệ thống->>DB: Đánh dấu phòng Đã cho thuê
    Hệ thống->>Hệ thống: Tạo file hợp đồng Word (bản chính thức)

    Hệ thống-->>Mobile: HĐ đã kích hoạt + tên đăng nhập khách
    Mobile-->>Manager: Thông báo hoàn tất — giao tài khoản cho khách
```

---

## 3.2 Thu phí hàng tháng & Thanh toán

### 3.2.2 Manager lập hóa đơn điện / nước

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant Mobile as Ứng dụng Mobile
    participant OCR as Dịch vụ OCR
    participant Hệ thống
    participant DB as Cơ sở dữ liệu
    participant Thông báo
    actor Khách as Khách thuê

    Manager->>Mobile: Mở màn hình chốt điện / nước
    Mobile->>Hệ thống: Lấy danh sách phòng đang cho thuê
    Hệ thống->>DB: Truy vấn phòng & HĐ active
    Hệ thống-->>Mobile: Danh sách phòng

    Manager->>Mobile: Chọn phòng & loại hóa đơn (điện hoặc nước)

    opt Chụp ảnh đồng hồ
        Manager->>Mobile: Chụp ảnh đồng hồ hiện tại
        Mobile->>OCR: Đọc chỉ số mới từ ảnh
        OCR-->>Mobile: Chỉ số mới
    end

    Manager->>Mobile: Nhập chỉ số cũ, chỉ số mới, đơn giá
    Mobile->>Mobile: Tính số tiêu thụ & thành tiền
    Manager->>Mobile: Bấm "Tạo hóa đơn"

    Mobile->>Hệ thống: Gửi thông tin hóa đơn tiện ích
    Hệ thống->>Hệ thống: Kiểm tra chỉ số hợp lệ, tính tiền
    Hệ thống->>DB: Lưu hóa đơn (trạng thái Chờ thanh toán)
    Hệ thống->>Thông báo: Gửi thông báo hóa đơn mới
    Thông báo-->>Khách: "Bạn có hóa đơn điện/nước mới"
    Hệ thống-->>Mobile: Xác nhận đã tạo hóa đơn
    Mobile-->>Manager: Hiển thị hóa đơn vừa lập
```

### 3.2.3 Manager lập hóa đơn tiền thuê

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant Mobile as Ứng dụng Mobile
    participant Hệ thống
    participant DB as Cơ sở dữ liệu
    participant Thông báo
    actor Khách as Khách thuê

    Manager->>Mobile: Mở màn hình lập hóa đơn tiền thuê
    Mobile->>Hệ thống: Lấy danh sách HĐ đang hiệu lực
    Hệ thống->>DB: Truy vấn HĐ & giá thuê
    Hệ thống-->>Mobile: Danh sách phòng / HĐ

    Manager->>Mobile: Chọn phòng, kỳ thu (tháng/năm)
    Mobile->>Mobile: Gợi ý số tiền theo HĐ
    Manager->>Mobile: Xác nhận & bấm "Tạo hóa đơn thuê"

    Mobile->>Hệ thống: Gửi thông tin hóa đơn tiền thuê
    Hệ thống->>Hệ thống: Kiểm tra chưa lập trùng kỳ
    Hệ thống->>DB: Lưu hóa đơn thuê (Chờ thanh toán)
    Hệ thống->>Thông báo: Gửi thông báo hóa đơn mới
    Thông báo-->>Khách: "Bạn có hóa đơn tiền thuê tháng X"
    Hệ thống-->>Mobile: Xác nhận
    Mobile-->>Manager: Hóa đơn đã gửi tới khách
```

### 3.2.4 Khách thanh toán qua PayOS

```mermaid
sequenceDiagram
    autonumber
    actor Khách as Khách thuê
    participant Mobile as Ứng dụng Mobile
    participant Hệ thống
    participant PayOS
    participant DB as Cơ sở dữ liệu

    Khách->>Mobile: Mở danh sách hóa đơn
    Mobile->>Hệ thống: Lấy hóa đơn của tôi
    Hệ thống->>DB: Truy vấn hóa đơn chưa thanh toán
    Hệ thống-->>Mobile: Danh sách hóa đơn

    Khách->>Mobile: Chọn hóa đơn & bấm "Thanh toán"
    Mobile->>Hệ thống: Yêu cầu tạo thanh toán
    Hệ thống->>PayOS: Tạo link / QR thanh toán
    PayOS-->>Hệ thống: QR & link
    Hệ thống->>DB: Gắn mã đơn PayOS vào hóa đơn
    Hệ thống-->>Mobile: Hiển thị QR / mở trang PayOS

    Khách->>PayOS: Thanh toán (chuyển khoản / ví…)

    alt PayOS báo thành công (webhook)
        PayOS->>Hệ thống: Xác nhận đã nhận tiền
        Hệ thống->>DB: Đánh dấu hóa đơn Đã thanh toán
    else App kiểm tra lại (dự phòng)
        Khách->>Mobile: Bấm "Kiểm tra thanh toán"
        Mobile->>Hệ thống: Hỏi trạng thái giao dịch
        Hệ thống->>PayOS: Đồng bộ trạng thái
        PayOS-->>Hệ thống: Đã thanh toán
        Hệ thống->>DB: Đánh dấu hóa đơn Đã thanh toán
    end

    Hệ thống-->>Mobile: Thanh toán thành công
    Mobile-->>Khách: Hiển thị hóa đơn đã thanh toán
```

### 3.2.5 Manager xác minh thanh toán thủ công / ghi nhận tiền mặt

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant Mobile as Ứng dụng Mobile
    participant Hệ thống
    participant DB as Cơ sở dữ liệu

    Note over Manager,DB: Trường hợp khách chuyển khoản — Manager đối soát

    Manager->>Mobile: Mở danh sách thanh toán chờ xác minh
    Mobile->>Hệ thống: Lấy giao dịch chờ duyệt
    Hệ thống->>DB: Truy vấn thanh toán Chờ xác minh
    Hệ thống-->>Mobile: Danh sách giao dịch

    Manager->>Mobile: Xem biên lai / ảnh chuyển khoản

    alt Xác nhận hợp lệ
        Manager->>Mobile: Bấm "Duyệt thanh toán"
        Mobile->>Hệ thống: Xác nhận giao dịch
        Hệ thống->>DB: Đánh dấu thanh toán Đã xác minh
        Hệ thống->>DB: Cập nhật hóa đơn → Đã thanh toán
    else Từ chối
        Manager->>Mobile: Bấm "Từ chối"
        Mobile->>Hệ thống: Từ chối giao dịch
        Hệ thống->>DB: Đánh dấu thanh toán Bị từ chối
    end

    Note over Manager,DB: Trường hợp khách trả tiền mặt tại chỗ

    Manager->>Mobile: Mở hóa đơn & bấm "Đã thu tiền mặt"
    Mobile->>Hệ thống: Ghi nhận thanh toán tiền mặt
    Hệ thống->>DB: Đánh dấu hóa đơn Đã thanh toán (tiền mặt)
    Hệ thống-->>Mobile: Xác nhận
    Mobile-->>Manager: Hóa đơn đã đóng
```

---

## 3.3 Bảo trì thiết bị

### 3.3.2 Gửi yêu cầu, lên lịch & hoàn tất sửa chữa

```mermaid
sequenceDiagram
    autonumber
    actor Khách as Khách thuê
    actor Manager
    participant Mobile as Ứng dụng Mobile
    participant Cloudinary
    participant Hệ thống
    participant DB as Cơ sở dữ liệu
    participant Thông báo

    Note over Khách,Thông báo: Khách báo sự cố

    Khách->>Mobile: Mở màn hình báo hỏng hóc
    Khách->>Mobile: Mô tả sự cố, chọn thiết bị (nếu có)
    Khách->>Mobile: Chụp ảnh hiện trạng
    Mobile->>Cloudinary: Tải ảnh lên
    Cloudinary-->>Mobile: Đường dẫn ảnh
    Khách->>Mobile: Gửi yêu cầu sửa chữa

    Mobile->>Hệ thống: Tạo phiếu bảo trì mới
    Hệ thống->>DB: Lưu yêu cầu (trạng thái Chờ xử lý)
    Hệ thống->>Thông báo: Thông báo Manager có yêu cầu mới
    Thông báo-->>Manager: Push notification
    Hệ thống-->>Mobile: Mã phiếu bảo trì
    Mobile-->>Khách: "Đã gửi — đang chờ xử lý"

    Note over Manager,Thông báo: Manager tiếp nhận & sửa chữa

    Manager->>Mobile: Mở danh sách yêu cầu bảo trì
    Mobile->>Hệ thống: Lấy phiếu chờ / đang xử lý
    Hệ thống-->>Mobile: Danh sách phiếu

    Manager->>Mobile: Chọn phiếu & bấm "Bắt đầu xử lý"
    Mobile->>Hệ thống: Cập nhật trạng thái Đang sửa
    Hệ thống->>DB: Lưu trạng thái Đang xử lý
    Hệ thống->>Thông báo: Thông báo khách đang được sửa
    Thông báo-->>Khách: "Yêu cầu đang được xử lý"

    Manager->>Mobile: Chụp ảnh trước / sau sửa chữa
    Mobile->>Cloudinary: Tải ảnh lên
    Manager->>Mobile: Nhập chi phí sửa chữa (nếu có)
    Manager->>Mobile: Bấm "Hoàn tất sửa chữa"

    Mobile->>Hệ thống: Đóng phiếu bảo trì
    Hệ thống->>DB: Cập nhật trạng thái Đã xử lý xong
    Hệ thống->>DB: Ghi chi phí vào sổ chi phí nhà
    Hệ thống->>DB: Thêm dòng lịch sử bảo trì thiết bị
    Hệ thống->>Thông báo: Thông báo khách đã sửa xong
    Thông báo-->>Khách: "Yêu cầu đã hoàn tất"
    Hệ thống-->>Mobile: Xác nhận
    Mobile-->>Manager: Phiếu đã đóng
```

### 3.3.3 Cập nhật vòng đời thiết bị

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant Web as Ứng dụng Web
    participant Mobile as Ứng dụng Mobile
    participant Hệ thống
    participant DB as Cơ sở dữ liệu

    Note over Manager,DB: Sau khi hoàn tất phiếu bảo trì liên quan thiết bị

    Hệ thống->>Hệ thống: Tự cập nhật tình trạng thiết bị<br/>(Tốt / Hỏng / Hỏng nặng)

    Manager->>Web: Mở chi tiết thiết bị
    Web->>Hệ thống: Lấy thông tin & lịch sử bảo trì
    Hệ thống->>DB: Đọc thiết bị + lịch sử sửa chữa
    Hệ thống-->>Web: Trạng thái hiện tại & nhật ký sửa chữa
    Web-->>Manager: Xem đầy đủ lịch sử theo từng lần sửa

    opt Manager chỉnh tay trạng thái vận hành
        Manager->>Web: Bật / tắt thiết bị (ngừng sử dụng tạm)
        Web->>Hệ thống: Cập nhật trạng thái vận hành
        Hệ thống->>DB: Lưu Active / Tạm ngưng
        Hệ thống-->>Web: Xác nhận
    end
```

---

## 3.4 Nhập nhà & Kích hoạt lên hệ thống

### 3.4.2 Nhập hàng loạt qua Excel ("Khởi tạo nhà" + "Cấu hình khai thác")

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Web as Ứng dụng Web
    participant Cloudinary
    participant Hệ thống
    participant DB as Cơ sở dữ liệu
    participant Thông báo
    actor Host

    Note over Admin,Host: Bước 1 — Khởi tạo nhà (hợp đồng thuê gốc + phòng + thiết bị bàn giao)

    Admin->>Web: Tải lên file Excel hợp đồng thuê
    Web->>Hệ thống: Gửi file — chạy thử (kiểm tra lỗi)
    Hệ thống->>Hệ thống: Đọc & validate từng dòng
    Hệ thống-->>Web: Báo cáo lỗi / cảnh báo (nếu có)

    Admin->>Web: Xác nhận nhập thật
    Web->>Hệ thống: Nhập dữ liệu chính thức
    Hệ thống->>DB: Tạo nhà (Nháp), phòng, thiết bị bàn giao
    Hệ thống-->>Web: Danh sách nhà đã tạo

    Admin->>Web: Tải lên file ZIP ảnh nhà
    Web->>Cloudinary: Lưu ảnh
    Web->>Hệ thống: Gắn ảnh vào từng nhà
    Hệ thống->>DB: Cập nhật ảnh nhà

    Note over Admin,Host: Bước 2 — Cấu hình khai thác (chi phí cải tạo + thiết bị mua thêm)

    Admin->>Web: Tải lên file Excel cải tạo
    Web->>Hệ thống: Nhập chi phí cải tạo (khớp mã HĐ thuê)
    Hệ thống->>DB: Lưu từng hạng mục cải tạo & thiết bị mua
    Hệ thống->>Hệ thống: Tính toán giá khai thác sơ bộ
    Hệ thống->>DB: Chuyển nhà sang Chờ Host duyệt giá
    Hệ thống->>Thông báo: Thông báo Host có nhà cần duyệt
    Thông báo-->>Host: Push / email thông báo
    Hệ thống-->>Web: Nhập xong — chờ Host
    Web-->>Admin: Hiển thị trạng thái Chờ duyệt giá
```

### 3.4.3 Host duyệt giá & Admin kích hoạt nhà

```mermaid
sequenceDiagram
    autonumber
    actor Host
    actor Admin
    participant Web as Ứng dụng Web
    participant Hệ thống
    participant DB as Cơ sở dữ liệu
    participant Thông báo

    Host->>Web: Mở hồ sơ nhà chờ duyệt
    Web->>Hệ thống: Lấy tóm tắt nhập liệu (HĐ, cải tạo, thiết bị)
    Hệ thống->>DB: Đọc toàn bộ dữ liệu onboarding
    Hệ thống-->>Web: Bảng tổng hợp chi phí & giá đề xuất

    Host->>Web: Chọn cách tính giá (xuôi / ngược)
    Web->>Hệ thống: Yêu cầu tính lại giá thuê
    Hệ thống->>Hệ thống: Tính giá phòng / giá nguyên căn
    Hệ thống-->>Web: Bảng giá chi tiết

    Host->>Web: Xem xét & bấm "Đồng ý giá"
    Web->>Hệ thống: Host xác nhận giá khai thác
    Hệ thống->>DB: Lưu quyết định Host
    Hệ thống->>Thông báo: Thông báo Admin có thể kích hoạt
    Thông báo-->>Admin: "Host đã duyệt giá"

    Admin->>Web: Mở nhà đã được Host duyệt
    Admin->>Web: Bấm "Kích hoạt lên hệ thống"
    Web->>Hệ thống: Xác nhận kích hoạt
    Hệ thống->>DB: Nhà → Đang hoạt động
    Hệ thống->>DB: Các phòng → Sẵn sàng cho thuê
    Hệ thống-->>Web: Kích hoạt thành công
    Web-->>Admin: Nhà đã sẵn sàng tiếp khách & lập hóa đơn
```

### 3.4.4 Cải tạo bổ sung trên nhà đang hoạt động

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    actor Host
    participant Web as Ứng dụng Web
    participant Hệ thống
    participant DB as Cơ sở dữ liệu
    participant Thông báo

    Note over Admin,Host: Nhà đang ở trạng thái Đang hoạt động — cần sửa / mua thêm đồ

    Admin->>Web: Chọn nhà & bấm "Mở đợt cải tạo mới"
    Web->>Hệ thống: Bắt đầu phiên cải tạo bổ sung
    Hệ thống->>DB: Ghi nhận đợt cải tạo (nhà tạm Chờ cải tạo)
    Hệ thống-->>Web: Phiên cải tạo đã mở

    Admin->>Web: Tải file Excel cải tạo bổ sung
    Web->>Hệ thống: Nhập chi phí & thiết bị mới
    Hệ thống->>DB: Thêm hạng mục vào phiên hiện tại

    Admin->>Web: Bấm "Hoàn tất cải tạo"
    Web->>Hệ thống: Đóng phiên & tính lại giá
    Hệ thống->>Hệ thống: Cộng chi phí mới vào cơ sở tính giá
    Hệ thống->>DB: Chuyển nhà → Chờ Host duyệt lại giá
    Hệ thống->>Thông báo: Thông báo Host duyệt lại
    Thông báo-->>Host: "Nhà X có cải tạo bổ sung — cần duyệt giá"

    Host->>Web: Xem giá mới & xác nhận (giống §3.4.3)
    Web->>Hệ thống: Host đồng ý giá mới
    Hệ thống->>DB: Cập nhật giá & trạng thái

    Admin->>Web: Kích hoạt lại sau khi Host duyệt
    Web->>Hệ thống: Xác nhận
    Hệ thống->>DB: Nhà → Đang hoạt động (giá mới)
    Hệ thống-->>Web: Hoàn tất
    Web-->>Admin: Nhà tiếp tục khai thác với giá đã cập nhật
```

---

## Gợi ý chèn vào Report 4

Thay các placeholder trong SDD:

| Mục SDD | File diagram (export PNG/SVG từ Mermaid) |
|---------|------------------------------------------|
| §3.1.2 | `3.1.2-sequence-draft-assign.png` |
| §3.1.3 | `3.1.3-sequence-room-condition.png` |
| §3.1.4 | `3.1.4-sequence-deposit-otp-activate.png` |
| §3.2.2 | `3.2.2-sequence-utility-invoice.png` |
| §3.2.3 | `3.2.3-sequence-rent-invoice.png` |
| §3.2.4 | `3.2.4-sequence-tenant-payos.png` |
| §3.2.5 | `3.2.5-sequence-manager-verify-payment.png` |
| §3.3.2 | `3.3.2-sequence-maintenance.png` |
| §3.3.3 | `3.3.3-sequence-equipment-lifecycle.png` |
| §3.4.2 | `3.4.2-sequence-bulk-import.png` |
| §3.4.3 | `3.4.3-sequence-host-activation.png` |
| §3.4.4 | `3.4.4-sequence-renovation-supplement.png` |

**Cách export:** dán từng khối `mermaid` vào [mermaid.live](https://mermaid.live) hoặc extension Mermaid trong VS Code / draw.io → Export PNG.

---

## Tham chiếu kỹ thuật (ẩn khỏi diagram)

Chi tiết endpoint API & class diagram nằm ở:

- [`tenant-onboarding-sequence-diagrams.md`](./tenant-onboarding-sequence-diagrams.md) — onboarding (có API map)
- [`FE-BE-tenant-onboarding-flow.md`](./FE-BE-tenant-onboarding-flow.md) — luồng FE/BE onboarding
