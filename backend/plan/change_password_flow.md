# Kế hoạch & Luồng Đổi/Quên Mật khẩu (Production-Ready Password Management Flow)

Tài liệu này mô tả chi tiết luồng nghiệp vụ thay đổi mật khẩu trên hệ thống Roamly, đảm bảo các tiêu chuẩn về bảo mật, UX/UI, và tính toàn vẹn dữ liệu.

---

## 1. Vấn đề của luồng cũ & Mục tiêu thiết kế mới

### Các vấn đề cần giải quyết:
- Chưa có API quên mật khẩu — user không thể tự khôi phục tài khoản nếu quên mật khẩu.
- Chưa có API đổi mật khẩu khi đang đăng nhập — user muốn đổi mật khẩu định kỳ hoặc khi bị lộ.
- Không có cơ chế xác thực OTP qua email cho flow quên mật khẩu.
- Không kiểm tra mật khẩu cũ trước khi cho phép đổi mật khẩu (dễ bị chiếm session).

### Mục tiêu mới:
- Xây dựng API quên mật khẩu (`POST /auth/forgot-password`) — gửi OTP qua email.
- Xây dựng API reset mật khẩu (`POST /auth/reset-password`) — verify OTP + đặt mật khẩu mới.
- Xây dựng API đổi mật khẩu (`POST /auth/change-password`) — yêu cầu đang đăng nhập, xác thực mật khẩu cũ.
- Áp dụng Rate Limiting cho forgot-password để tránh spam email.
- Tái sử dụng OTP Service đã có (tương tự luồng đăng ký).
- Đảm bảo mật khẩu mới đáp ứng yêu cầu độ mạnh (giống RegisterRequest).

---

## 2. Kiến trúc & Sơ đồ Luồng Đổi/Quên Mật khẩu (Flowchart)

### Giai đoạn 1: Quên mật khẩu — Gửi OTP (Forgot Password)

1. **Client** gửi `POST /api/v1/auth/forgot-password` với payload: `{ email }`.
2. **Rate Limiter (Redis):** Kiểm tra email gửi request. Nếu > 3 lần/giờ -> `429 Too Many Requests`.
3. **Controller Validation:**
    - Kiểm tra định dạng Email.
4. **Service (Check Database):**
    - Tìm user theo email (`UserRepository.findByEmail`).
    - **NẾU KHÔNG TỒN TẠI:** Trả về success luôn (không nói email không tồn tại) để tránh lộ thông tin — giống pattern `INVALID_CREDENTIALS` ở login.
    - **NẾU STATUS = PENDING:** Trả về success luôn (vì email chưa verify, nhưng không nói rõ).
    - **NẾU STATUS = BANNED:** Trả về success luôn.
5. **Tạo OTP (Redis):** Sinh mã OTP (6 số ngẫu nhiên). Lưu vào Redis với key `otp:forgot:{email}` và TTL 5 phút.
   - *Lưu ý:* Nếu đã có OTP cũ chưa hết hạn, ghi đè bằng OTP mới và reset TTL.
6. **Gửi Email (Async):** Gửi email chứa mã OTP đến địa chỉ của user.
7. **Phản hồi Client:** Trả về `200 OK` với message: "If the email is registered, you will receive an OTP code to reset your password." — message giống nhau cho mọi trường hợp.

### Giai đoạn 2: Reset mật khẩu (Reset Password)

1. **Client** gửi `POST /api/v1/auth/reset-password` với payload: `{ email, otpCode, newPassword, confirmNewPassword }`.
2. **Controller Validation:**
    - Kiểm tra định dạng Email.
    - Kiểm tra `newPassword` mạnh (regex tương tự RegisterRequest).
    - Kiểm tra `newPassword` khớp `confirmNewPassword`.
3. **Service (Check Redis & Update):**
    - Đọc Redis key `otp:forgot:{email}`.
    - **NẾU KHÔNG CÓ/HẾT HẠN:** Trả lỗi `OTP_EXPIRED_OR_INVALID` (400).
    - **NẾU KHÔNG KHỚP:** Trả lỗi `OTP_INVALID` (400).
    - **NẾU KHỚP:**
        - Xoá key OTP trong Redis.
        - Hash mật khẩu mới bằng `PasswordEncoder.encode(newPassword)`.
        - Cập nhật `password` trong DB.
        - Cập nhật `updatedAt`.
        - **Quan trọng:** Xoá tất cả refresh token của user khỏi Redis (`refresh:{email}`) — buộc mọi thiết bị đăng nhập lại.
4. **Phản hồi Client:** Trả về `200 OK` với message "Password has been reset successfully. Please login again."

### Giai đoạn 3: Đổi mật khẩu (Change Password) — Yêu cầu đăng nhập

1. **Client** gửi `POST /api/v1/auth/change-password` với `Authorization: Bearer <accessToken>` và payload: `{ oldPassword, newPassword, confirmNewPassword }`.
2. **JwtAuthFilter:** Xác thực access token như thường lệ.
3. **Controller Validation:**
    - Kiểm tra `oldPassword` không được để trống.
    - Kiểm tra `newPassword` mạnh (regex tương tự RegisterRequest).
    - Kiểm tra `newPassword` khớp `confirmNewPassword`.
    - Kiểm tra `oldPassword != newPassword` (tránh đặt lại mật khẩu cũ).
4. **Service:**
    - Lấy email từ `SecurityContextHolder` (đã được JwtAuthFilter xác thực).
    - Load user từ DB (`userRepository.findByEmail(email)`).
    - **NẾU USER KHÔNG TỒN TẠI:** Trả lỗi `UNAUTHENTICATED` (401).
    - Kiểm tra `oldPassword` có đúng không bằng `passwordEncoder.matches(oldPassword, user.getPassword())`.
    - **NẾU SAI MẬT KHẨU CŨ:** Trả lỗi `INVALID_OLD_PASSWORD` (400).
    - Hash mật khẩu mới bằng `PasswordEncoder.encode(newPassword)`.
    - Cập nhật `password` trong DB.
    - Cập nhật `updatedAt`.
    - **Tuỳ chọn:** Xoá tất cả refresh token của user khỏi Redis (buộc đăng nhập lại), hoặc giữ nguyên session hiện tại.
5. **Phản hồi Client:** Trả về `200 OK` với message "Password changed successfully." và access token mới (nếu muốn giữ session).

---

## 3. Chi tiết Cấu trúc Dữ liệu

### 3.1. DTOs

**ForgotPasswordRequest.java (Mới)**
```java
public class ForgotPasswordRequest {
    @NotBlank(message = "MISSING_EMAIL")
    @Email(message = "INVALID_EMAIL")
    private String email;
}
```

**ResetPasswordRequest.java (Mới)**
```java
public class ResetPasswordRequest {
    @NotBlank(message = "MISSING_EMAIL")
    @Email(message = "INVALID_EMAIL")
    private String email;

    @NotBlank(message = "MISSING_OTP")
    @Size(min = 6, max = 6, message = "INVALID_OTP_FORMAT")
    private String otpCode;

    @NotBlank(message = "MISSING_PASSWORD")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$", message = "WEAK_PASSWORD")
    private String newPassword;

    @NotBlank(message = "MISSING_CONFIRM_PASSWORD")
    private String confirmNewPassword;
}
```

**ChangePasswordRequest.java (Mới)**
```java
public class ChangePasswordRequest {
    @NotBlank(message = "MISSING_PASSWORD")
    private String oldPassword;

    @NotBlank(message = "MISSING_PASSWORD")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$", message = "WEAK_PASSWORD")
    private String newPassword;

    @NotBlank(message = "MISSING_CONFIRM_PASSWORD")
    private String confirmNewPassword;
}
```

### 3.2. ErrorCode (Bổ sung)

```java
// ==========================================
// PASSWORD MANAGEMENT ERRORS
// ==========================================
OTP_EXPIRED_OR_INVALID("OTP_EXPIRED_OR_INVALID", "OTP code has expired or is invalid", 400),
OTP_INVALID("OTP_INVALID", "Invalid OTP code", 400),
INVALID_OLD_PASSWORD("INVALID_OLD_PASSWORD", "Current password is incorrect", 400),
SAME_PASSWORD("SAME_PASSWORD", "New password must be different from current password", 400);
```

### 3.3. Redis Keys

| Key | Mục đích | TTL |
|---|---|---|
| `otp:forgot:{email}` | Lưu OTP cho forgot password | 5 phút |
| `refresh:{email}` | Lưu refresh token (đã có) — xoá khi reset/đổi mật khẩu | 30 ngày |

### 3.4. UrlConstant (Bổ sung)

```java
public static final String FORGOT_PASSWORD = "/auth/forgot-password";
public static final String RESET_PASSWORD = "/auth/reset-password";
public static final String CHANGE_PASSWORD = "/auth/change-password";
```

---

## 4. Security & Edge Cases (Cần lưu ý)

### Bảo mật

- **Không lộ thông tin email:** API forgot-password luôn trả message giống nhau dù email có tồn tại hay không — tránh bị lợi dụng để kiểm tra email đã đăng ký.
- **Rate Limit cho forgot-password:** Giới hạn 3 lần/giờ/email để tránh spam email.
- **OTP giống luồng register:** Tái sử dụng `OtpService` với prefix key riêng `otp:forgot:`.
- **Reset mật khẩu → xoá tất cả session:** Sau reset, xoá hết refresh token trong Redis. Buộc user đăng nhập lại trên mọi thiết bị — ngăn kẻ tấn công giữ session cũ.
- **Đổi mật khẩu → giữ session hay không?**
    - Option A (Khuyên dùng): Xoá refresh token, cấp access token mới — session hiện tại vẫn sống, các thiết bị khác bị đẩy ra.
    - Option B: Giữ nguyên session — chỉ đổi mật khẩu, không ảnh hưởng thiết bị khác.
- **Old password validation:** Bắt buộc kiểm tra mật khẩu cũ bằng `PasswordEncoder.matches()` — tránh bị lợi dụng nếu attacker chiếm được session (access token).
- **Không cho đặt lại mật khẩu cũ:** Validate `oldPassword != newPassword` ở Change Password.

### Edge Cases

- **User PENDING gửi forgot-password:** Vẫn gửi OTP (nhưng không reset được vì chưa active — có thể thêm check ở reset-password).
- **User BANNED:** Cho gửi forgot-password? Tuỳ chính sách — nên chặn ở reset-password (trả `ACCOUNT_BANNED`).
- **OTP bị ghi đè:** Nếu user yêu cầu forgot-password nhiều lần, OTP cũ bị ghi đè — cái cuối cùng mới có hiệu lực.
- **Concurrent request:** Nếu user gửi đồng thời 2 request reset-password, OTP chỉ dùng được 1 lần (xoá sau khi verify).
- **Race condition update password:** Dùng `@Transactional` + Optimistic Locking nếu cần, nhưng với password update đơn giản thì không quá critical.
- **Password history:** Nếu cần nâng cao, có thể lưu N mật khẩu gần nhất để không cho đặt lại.

### UX (Gợi ý cho FE)

- **Forgot password flow:**
    ```
    Bước 1: Nhập email → gửi OTP
    Bước 2: Nhập OTP + mật khẩu mới → reset
    Bước 3: Chuyển về màn hình login
    ```
- **Nút "Gửi lại OTP":** Countdown 60s trước khi cho gửi lại.
- **Hiển thị độ mạnh mật khẩu** realtime khi nhập.
