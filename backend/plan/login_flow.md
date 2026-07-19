# Kế hoạch & Luồng Đăng nhập (Production-Ready Login Flow)

Tài liệu này mô tả chi tiết luồng nghiệp vụ đăng nhập trên hệ thống Conflict, đảm bảo các tiêu chuẩn về bảo mật, UX/UI, và tính toàn vẹn dữ liệu.

---

## 1. Vấn đề của luồng cũ & Mục tiêu thiết kế mới

### Các vấn đề cần giải quyết:
- Chưa có API đăng nhập (chỉ có đăng ký + OTP, sau đó trả token luôn).
- Không có cơ chế refresh token — token hết hạn là user phải đăng nhập lại.
- Không có rate limiting cho login (dễ bị brute-force).
- Không phân biệt lỗi "sai mật khẩu" vs "email không tồn tại" (bảo mật thông tin user).
- Tài khoản `PENDING` vẫn có thể login được (chưa verify OTP).

### Mục tiêu mới:
- Xây dựng API `POST /auth/login` chuẩn chỉnh.
- Áp dụng Rate Limiting (giới hạn số lần đăng nhập sai).
- Phân biệt lỗi thân thiện nhưng không lộ thông tin user.
- Chặn user `PENDING` / `BANNED` đăng nhập.
- Cấp `Access Token` + `Refresh Token` khi đăng nhập thành công.
- Thêm API `POST /auth/refresh-token` để refresh token hết hạn.
- Thêm API `POST /auth/logout` để thu hồi refresh token.

---

## 2. Kiến trúc & Sơ đồ Luồng Đăng nhập (Flowchart)

### Giai đoạn 1: Đăng nhập (Login)

1. **Client** gửi `POST /api/v1/auth/login` với payload: `{ email, password }`.
2. **Rate Limiter (Redis):** Kiểm tra IP gửi request. Nếu > 10 lần/15 phút -> `429 Too Many Requests`.
3. **Controller Validation:**
    - Kiểm tra định dạng Email.
    - Kiểm tra Password không được để trống.
4. **Service (Check Database & Status):**
    - Tìm user theo email (`UserRepository.findByEmail`).
    - **NẾU KHÔNG TỒN TẠI:** Trả lỗi `INVALID_CREDENTIALS` (401) — không nói rõ "email không tồn tại" để tránh lộ thông tin.
    - **NẾU STATUS = PENDING:** Trả lỗi `ACCOUNT_NOT_VERIFIED` (403) — "Vui lòng xác minh email trước khi đăng nhập".
    - **NẾU STATUS = BANNED:** Trả lỗi `ACCOUNT_BANNED` (403) — "Tài khoản đã bị khóa".
5. **Xác thực mật khẩu:**
    - Dùng `AuthenticationManager.authenticate()` với `UsernamePasswordAuthenticationToken`.
    - `DaoAuthenticationProvider` tự động gọi `PasswordEncoder.matches()` để so sánh password đã hash.
    - **NẾU SAI PASSWORD:** Tăng biến đếm số lần thất bại trong Redis (`failed:login:{email}`). Nếu > 5 lần -> tạm khóa 15 phút. Trả lỗi `INVALID_CREDENTIALS` (401).
6. **Xoá Redis key đếm sai:** Nếu đăng nhập thành công, xoá key `failed:login:{email}` (reset lại bộ đếm).
7. **Cấp phát Token (JwtProvider):**
    - Tạo `Access Token` (24h).
    - Tạo `Refresh Token` (30 ngày).
    - Lưu `Refresh Token` vào Redis với key `refresh:{userId}` (hỗ trợ logout về sau).
8. **Phản hồi Client:** Trả về `200 OK` kèm `LoginResponse`:
    ```json
    {
      "user": { "id", "email", "fullName", "avatarUrl", "pinCode" },
      "accessToken": "eyJ...",
      "refreshToken": "eyJ...",
      "expiresIn": 86400
    }
    ```

### Giai đoạn 2: Refresh Token

1. **Client** gửi `POST /api/v1/auth/refresh-token` với payload: `{ refreshToken }`.
2. **Service:**
    - Verify `refreshToken` bằng `JwtProvider`.
    - Trích xuất `userId` / `email` từ token.
    - Kiểm tra Redis key `refresh:{userId}` còn tồn tại và khớp không.
    - **NẾU KHÔNG KHỚP / HẾT HẠN:** Trả lỗi `INVALID_REFRESH_TOKEN` (401).
3. **Cấp lại token mới:**
    - Tạo `Access Token` mới.
    - Tạo `Refresh Token` mới.
    - Cập nhật Redis key `refresh:{userId}`.
4. **Phản hồi Client:** Trả về `200 OK` kèm cặp token mới.

### Giai đoạn 3: Đăng xuất (Logout)

1. **Client** gửi `POST /api/v1/auth/logout` với `Authorization: Bearer <accessToken>` và body `{ refreshToken }`.
2. **Service:**
    - Trích xuất `email` / `userId` từ access token (qua `JwtProvider`).
    - Xoá Redis key `refresh:{userId}`.
    - (Tuỳ chọn) Thêm access token vào Redis blacklist cho đến khi nó hết hạn.
3. **Phản hồi Client:** Trả về `200 OK` với message "Đăng xuất thành công".

---

## 3. Chi tiết Cấu trúc Dữ liệu

### 3.1. DTOs

**LoginRequest.java (Mới)**
```java
public class LoginRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;
}
```

**RefreshTokenRequest.java (Mới)**
```java
public class RefreshTokenRequest {
    @NotBlank
    private String refreshToken;
}
```

**LogoutRequest.java (Mới)**
```java
public class LogoutRequest {
    @NotBlank
    private String refreshToken;
}
```

**LoginResponse.java** (Có thể tái sử dụng `AuthResponse.java` đã có sẵn, chỉ cần đổi tên hoặc dùng chung):
```java
@Data
@Builder
public class LoginResponse {
    private UserDTO user;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
}
```

### 3.2. ErrorCode (Bổ sung)

```java
INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Email or password is incorrect"),
ACCOUNT_NOT_VERIFIED("ACCOUNT_NOT_VERIFIED", "Please verify your email before logging in"),
ACCOUNT_BANNED("ACCOUNT_BANNED", "Your account has been banned"),
INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "Invalid or expired refresh token"),
TOO_MANY_LOGIN_ATTEMPTS("TOO_MANY_LOGIN_ATTEMPTS", "Too many login attempts. Please try again in 15 minutes");
```

### 3.3. Redis Keys

| Key | Mục đích | TTL |
|---|---|---|
| `failed:login:{email}` | Đếm số lần đăng nhập sai | 15 phút (tự động reset) |
| `refresh:{userId}` | Lưu refresh token hợp lệ | 30 ngày |
| `blacklist:token:{jti}` | Blacklist access token đã logout | Đến khi token hết hạn |

---

## 4. Kế hoạch Triển khai (Implementation Steps)

| Task | Tên công việc | Mô tả chi tiết | Ước lượng |
|---|---|---|---|
| 1 | **Tạo LoginRequest DTO** | Tạo `LoginRequest` với validation `@NotBlank @Email` cho email, `@NotBlank` cho password. | 0.5 giờ |
| 2 | **Thêm ErrorCode** | Thêm `INVALID_CREDENTIALS`, `ACCOUNT_NOT_VERIFIED`, `ACCOUNT_BANNED`, `INVALID_REFRESH_TOKEN`, `TOO_MANY_LOGIN_ATTEMPTS`. | 0.5 giờ |
| 3 | **Thêm UrlConstant** | Thêm `LOGIN`, `REFRESH_TOKEN`, `LOGOUT` vào `UrlConstant.Auth`. | 0.25 giờ |
| 4 | **Viết login trong AuthService** | Sử dụng `AuthenticationManager` có sẵn. Kiểm tra status (PENDING/BANNED). Xử lý rate limit sai mật khẩu. Cấp token. | 2 giờ |
| 5 | **Viết refresh + logout trong AuthService** | `refreshToken()` verify token cũ -> cấp mới -> update Redis. `logout()` xoá refresh token trong Redis. | 1.5 giờ |
| 6 | **Thêm endpoints trong AuthController** | Thêm `POST /auth/login`, `POST /auth/refresh-token`, `POST /auth/logout`. | 0.5 giờ |
| 7 | **Cập nhật UrlConstant** | Thêm các hằng số URL mới. | 0.25 giờ |
| 8 | **Unit & Integration Test** | Viết Test kiểm tra: login thành công, sai mật khẩu, account PENDING không login được, account BANNED không login được, refresh token hết hạn, logout thành công. | 2.5 giờ |

---

## 5. Security & Edge Cases (Cần lưu ý)

- **Brute-force Protection:** Dùng Redis đếm số lần thất bại theo email + IP. Sau 5 lần sai, block 15 phút. Giảm thiểu brute-force qua API.
- **Không lộ thông tin:** Luôn trả `INVALID_CREDENTIALS` cho cả "email không tồn tại" và "sai mật khẩu" — không cho biết cái nào sai.
- **Refresh Token Rotation:** Mỗi lần refresh, cấp refresh token mới và xoá token cũ. Nếu token cũ được dùng lại -> có thể token đã bị đánh cắp -> thu hồi tất cả token của user.
- **Logout:** Xoá refresh token khỏi Redis, thêm access token vào blacklist (Redis). Nếu không blacklist, access token vẫn dùng được đến khi hết hạn (dễ chấp nhận).
- **Token lưu ở đâu?:** Client nên lưu access token trong memory (biến JS), refresh token trong httpOnly cookie (nếu web) hoặc secure storage (nếu mobile). **Không lưu trong localStorage** vì dễ bị XSS.
- **User PENDING:** Không cho login, trả message "Vui lòng kiểm tra email để xác minh tài khoản". Có thể thêm nút "Gửi lại OTP".
- **User BANNED:** Không cho login, trả message "Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ".
- **Concurrent login cùng tài khoản:** Mỗi lần login mới -> ghi đè refresh token cũ trong Redis. Có thể cho phép nhiều session song song nếu cần (dùng set Redis thay vì single key).
