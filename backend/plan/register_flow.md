# Kế hoạch & Luồng Đăng ký Tài khoản (Production-Ready Registration Flow)

Tài liệu này mô tả chi tiết luồng nghiệp vụ đăng ký tài khoản mới trên hệ thống Roamly, đảm bảo các tiêu chuẩn về bảo mật, UX/UI, và tính toàn vẹn dữ liệu.

---

## 1. Vấn đề của luồng cũ & Mục tiêu thiết kế mới

### Các vấn đề cần giải quyết:
- Không chặn được bot spam API tạo tài khoản ảo (gây cạn kiệt ngân sách AI).
- Trả JWT Token ngay lập tức dù email có thể không tồn tại/thuộc về người khác.
- Lỗi trùng lặp mã định danh PIN Code do chỉ sinh random mà không check database.
- Lỗi 500 do Race Condition khi 2 user đăng ký cùng 1 email cùng lúc.
- Mật khẩu chưa đủ độ khó an toàn.

### Mục tiêu mới:
- Áp dụng xác thực hai bước qua Email (OTP).
- Có cơ chế Rate Limiting (giới hạn request).
- Quản lý trạng thái tài khoản (`PENDING`, `ACTIVE`, `BANNED`).
- Đảm bảo tính nhất quán dữ liệu (Data Integrity).

---

## 2. Kiến trúc & Sơ đồ Luồng Đăng ký (Flowchart)

### Giai đoạn 1: Gửi Yêu cầu Đăng ký (Initiate Registration)

1. **Client** gửi `POST /api/v1/auth/register` với payload: `{ email, password, confirmPassword, fullName }`.
2. **Rate Limiter (Redis):** Kiểm tra IP gửi request. Nếu > 5 lần/giờ -> `429 Too Many Requests`.
3. **Controller Validation:**
    - Kiểm tra định dạng Email.
    - Kiểm tra Mật khẩu mạnh (ít nhất 8 ký tự, có chữ hoa, chữ thường, số, ký tự đặc biệt).
    - Kiểm tra `password` khớp `confirmPassword`.
4. **Service (Check Database):**
    - Kiểm tra Email đã tồn tại chưa (`UserRepository.findByEmail`).
    - **NẾU TỒN TẠI & STATUS = ACTIVE:** Báo lỗi `USER_EXISTED` (400).
    - **NẾU TỒN TẠI & STATUS = PENDING:** Cập nhật lại mật khẩu mới (nếu có đổi) và gửi lại mã OTP mới.
    - **NẾU CHƯA TỒN TẠI:** Tạo bản ghi `User` mới với `status = PENDING`.
5. **Xử lý mã PIN (PIN Code):**
    - Tạo mã PIN ngẫu nhiên (vd: `RML-1A2B3C`).
    - Dùng vòng lặp `while(userRepository.existsByPinCode(pin))` (tối đa 5 lần) để đảm bảo không bị trùng.
6. **Lưu Database:** Lưu thông tin User (mật khẩu đã băm bcrypt).
7. **Tạo OTP (Redis):** Sinh mã OTP (6 số ngẫu nhiên). Lưu vào Redis với key `otp:register:{email}` và TTL (thời gian sống) là 5 phút.
8. **Gửi Email (Async):** Push event/Gửi email chứa mã OTP đến địa chỉ của user.
9. **Phản hồi Client:** Trả về `202 Accepted` hoặc `200 OK` với message: "Vui lòng kiểm tra email để nhận mã OTP". *(Lưu ý: Không trả về JWT Token).*

### Giai đoạn 2: Xác minh OTP (Verify OTP)

1. **Client** gửi `POST /api/v1/auth/verify-otp` với payload: `{ email, otpCode }`.
2. **Service (Check Redis):**
    - Đọc Redis key `otp:register:{email}`.
    - **NẾU KHÔNG CÓ/HẾT HẠN:** Trả lỗi `OTP_EXPIRED_OR_INVALID` (400).
    - **NẾU KHÔNG KHỚP:** Trả lỗi `OTP_INVALID` (400).
3. **Cập nhật User:**
    - Nếu khớp: Đổi `status` của User trong DB từ `PENDING` sang `ACTIVE`.
    - Xóa key OTP trong Redis.
4. **Cấp phát Token (JwtProvider):**
    - Tạo `Access Token` và `Refresh Token`.
5. **Phản hồi Client:** Trả về `200 OK` kèm `AuthResponse` (gồm Tokens và User info).

---

## 3. Chi tiết Cấu trúc Dữ liệu

### 3.1. User Entity (Cập nhật)

Bổ sung trường `status` dạng Enum:
```java
public enum AccountStatus {
    PENDING,  // Chờ xác minh email
    ACTIVE,   // Đã xác minh, hoạt động bình thường
    BANNED    // Bị khóa
}
```

Bổ sung trường avatar mặc định khi khởi tạo:
```java
// Nếu user không có avatar, dùng API tạo avatar dựa trên tên
this.avatarUrl = "https://ui-avatars.com/api/?name=" + URLEncoder.encode(this.fullName, "UTF-8") + "&background=random";
```

### 3.2. DTOs

**RegisterRequest.java (Cập nhật Validation)**
```java
@Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$", message = "WEAK_PASSWORD")
private String password;
```

**VerifyOtpRequest.java (Mới)**
```java
public class VerifyOtpRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 6, max = 6)
    private String otpCode;
}
```

---

## 4. Kế hoạch Triển khai (Implementation Steps)

| Task | Tên công việc | Mô tả chi tiết | Ước lượng |
|---|---|---|---|
| 1 | **Update Entity & DB Schema** | Cập nhật `User` entity, thêm `AccountStatus`. Thêm default Avatar. Sửa lại logic sinh PIN (bỏ khỏi `@PrePersist`). | 1 giờ |
| 2 | **Cấu hình Redis cho OTP** | Tích hợp Spring Data Redis. Viết `OtpService` để sinh, lưu (kèm TTL 5p), và xác thực OTP. | 1.5 giờ |
| 3 | **Cấu hình Rate Limiting** | Sử dụng Bucket4j hoặc thiết lập Redis Rate Limit đơn giản cho endpoint `/register` theo IP. | 1 giờ |
| 4 | **Tích hợp Email Sender** | Sử dụng Spring Boot Mail. Cấu hình SMTP (Gmail/SendGrid). Viết `MailService` gửi template HTML chứa mã OTP (gọi qua `@Async`). | 2 giờ |
| 5 | **Sửa AuthService & Controller** | Tách luồng `/register` (trả về Message, ko trả JWT). Viết mới API `/verify-otp` (nhận OTP -> Cập nhật trạng thái -> trả JWT). | 2.5 giờ |
| 6 | **Global Exception Handler** | Handle `DataIntegrityViolationException` để báo lỗi trùng PIN/Email thân thiện hơn. Handle lỗi Rate Limit. | 0.5 giờ |
| 7 | **Unit & Integration Test** | Viết Test kiểm tra luồng Register thành công, sai OTP, quá hạn OTP, trùng email, rate limit. | 2.5 giờ |

---

## 5. Security & Edge Cases (Cần lưu ý)
- **Gửi lại OTP (Resend OTP):** Cần thiết kế API `/resend-otp` có Rate Limit chặt chẽ (vd: chỉ được yêu cầu gửi lại sau mỗi 60 giây, tối đa 3 lần/ngày) để tránh bị lợi dụng dùng làm công cụ spam email người khác.
- **Dọn dẹp rác (Cronjob):** Viết một task chạy ngầm (Scheduled Task) dọn dẹp các tài khoản `PENDING` quá 24h mà không được xác minh để tránh rác Database.
- **Race Condition DB:** Khi lưu User, nếu bắt được exception `DataIntegrityViolationException` ở ControllerAdvice, cần check xem đó là do trùng `email` hay trùng `pin_code` để trả message tương ứng.
