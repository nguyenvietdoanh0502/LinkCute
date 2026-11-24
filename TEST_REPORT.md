# Kết quả kiểm thử LinkCute — 2026-09-05

## Kết luận

Bộ test hiện có đạt **346/346** (88 frontend + 258 backend), không có test bỏ qua; frontend build thành công. Kiểm tra bổ sung hook theme bằng React StrictMode phát hiện **2 lỗi** trong 7 tình huống (5 đạt, 2 lỗi). Vì chưa kiểm tra được trình duyệt và dịch vụ thực, chưa thể kết luận mọi chức năng hoạt động tốt từ đầu đến cuối.

Đợt này chỉ kiểm thử và ghi nhận kết quả, chưa sửa code ứng dụng.

## Lệnh và môi trường

| Phạm vi | Lệnh / phương pháp | Kết quả |
| --- | --- | --- |
| Frontend, thư mục `frontend/` | `npm.cmd test` | 88 đạt, 0 lỗi, 0 bỏ qua |
| Frontend, thư mục `frontend/` | `npm.cmd run build` | Thành công; cảnh báo chunk bản đồ lớn hơn 500 kB |
| Backend, thư mục `backend/` | `.\mvnw.cmd test` | BUILD SUCCESS; 258 đạt, 0 lỗi, 0 bỏ qua |
| Theme | Script Node qua stdin, import hook thật, `react-test-renderer`, React StrictMode, giả lập `window`/`document`/storage/matchMedia | 5 đạt, 2 lỗi; độc lập với 346 test có sẵn |
| Giao diện thực tế | Kết nối Browser và kiểm tra danh sách trình duyệt | Không có trình duyệt kết nối; chưa chạy |

- Frontend chạy bằng Node.js 24.14.0. PowerShell chặn `npm.ps1`, nên dùng `npm.cmd` mà không đổi execution policy.
- Lần chạy Maven trong sandbox bị chặn kết nối Maven Central (`Permission denied: getsockopt`); lần chạy ngoài sandbox thành công. JVM chạy test được log xác nhận là Java 21.0.10; chưa xác minh runtime JDK 17.
- Backend dùng profile `test`, H2; Flyway tự chạy bị tắt theo cấu hình test. Các migration test có sẵn vẫn được thực thi trong suite.
- Cảnh báo `react-test-renderer` deprecated và Mockito tự gắn Java agent không làm test thất bại.

## Phạm vi bộ test hiện có

Frontend bao gồm cookie/session/refresh/logout, hồ sơ và avatar, lịch trình cục bộ, chia sẻ lịch trình, model bạn bè, chat và STOMP, dữ liệu vị trí, cuộc gọi và cleanup tài nguyên. Các kiểm tra UI dùng renderer hoặc đọc wiring/source; các kết nối, media và một số API được giả lập.

| Nhóm backend | Số test đạt |
| --- | ---: |
| Security/JWT/STOMP/session | 32 |
| Khởi tạo ứng dụng | 1 |
| Auth/OTP/refresh/rate limit | 41 |
| Gọi thoại/signaling | 21 |
| Chat | 31 |
| Bạn bè | 33 |
| Địa điểm/tìm kiếm/bộ lọc/bản đồ/import | 40 |
| Kế hoạch/chia sẻ/lời mời | 22 |
| User/hồ sơ/avatar/đăng ký | 37 |
| **Tổng** | **258** |

Số liệu lấy từ kết quả Maven và các thuộc tính tổng kết trong `backend/target/surefire-reports/TEST-*.xml`. Đây là số test, không phải phần trăm coverage.

## Lỗi phát hiện

### 1. Bấm chuyển sang giao diện sáng lần đầu không đổi màu khi đang theo hệ thống tối

- Vị trí: `frontend/src/hooks/useTheme.js:38`; nút gọi hook ở `frontend/src/App.jsx:601`.
- Điều kiện: chưa lưu `linkcute.theme` (hoặc lưu `system`), hệ điều hành ưu tiên dark.
- Tái hiện: khởi tạo hook, xác nhận `preference = system`, `theme = dark`; gọi `toggle()` một lần.
- Mong đợi: `theme = light`, phù hợp label “Chuyển sang giao diện sáng”.
- Thực tế: `theme = dark`, `preference` chuyển thành `dark`; phải bấm lần nữa mới sáng.
- Nguyên nhân: `toggle()` đảo theo giá trị preference `system` thay vì theme đang hiển thị.
- Mức ảnh hưởng: thao tác chuyển theme sai ở lần đầu trong điều kiện trên.

### 2. Hook theme không khởi tạo được khi getter localStorage bị chặn

- Vị trí: `frontend/src/hooks/useTheme.js:12`; việc đọc storage tương tự còn có ở dòng 31 và 40.
- Điều kiện: trình duyệt/môi trường nhúng từ chối truy cập thuộc tính `window.localStorage` bằng `SecurityError`.
- Tái hiện: giả lập getter `localStorage` ném `DOMException`, mount component gọi `useTheme()`.
- Mong đợi: khởi tạo theme theo hệ thống và tiếp tục dùng giao diện trong phiên.
- Thực tế: mount ném `SecurityError: Storage denied`. Vì App gọi hook trực tiếp, lỗi này có thể ngăn App render trong môi trường đó.
- Nguyên nhân: đọc `window.localStorage` xảy ra trước khi đi vào `try/catch` của `getStoredThemePreference()`.
- Mức ảnh hưởng: cao trong môi trường chặn storage; chưa xác minh trên trình duyệt thực.

### Các tình huống theme đã đạt

1. Preference sáng đã lưu → bấm đổi tối; đồng bộ storage, `data-theme`, `colorScheme`, meta theme-color.
2. Preference tối đã lưu → bấm đổi sáng.
3. Theo hệ thống sáng → bấm đổi tối.
4. Theo dõi thay đổi hệ thống; ngừng listener khi chọn thủ công; đăng ký lại khi chọn `system`; cleanup khi unmount trong StrictMode.
5. Preference lưu không hợp lệ → quay về hệ thống.

### Script tái hiện hai lỗi bằng PowerShell

Chạy tại `frontend/`. Script dùng dependency hiện có, chỉ giả lập môi trường trong tiến trình Node; không sửa dữ liệu trình duyệt. Kết quả hiện tại: hai dòng `FAIL`, exit code 1.

```powershell
@'
import assert from 'node:assert/strict'
import React, { act } from 'react'
import TestRenderer from 'react-test-renderer'
import { useTheme } from './src/hooks/useTheme.js'

globalThis.IS_REACT_ACT_ENVIRONMENT = true
let latest
function Harness() { latest = useTheme(); return null }
for (const blocked of [false, true]) {
  let renderer
  globalThis.window = {
    matchMedia: () => ({ matches: true, addEventListener() {}, removeEventListener() {} }),
  }
  Object.defineProperty(window, 'localStorage', {
    get() {
      if (blocked) throw new DOMException('Storage denied', 'SecurityError')
      return { getItem: () => null, setItem() {} }
    },
  })
  globalThis.document = {
    documentElement: { style: {}, setAttribute() {} },
    querySelector: () => null,
  }
  const name = blocked ? 'mount with storage denied' : 'system dark toggles light'
  try {
    act(() => {
      renderer = TestRenderer.create(
        React.createElement(React.StrictMode, null, React.createElement(Harness)),
      )
    })
    assert.equal(latest.theme, 'dark')
    if (!blocked) {
      act(() => latest.toggle())
      assert.equal(latest.theme, 'light')
    }
    console.log('PASS', name)
  } catch (error) {
    console.log('FAIL', name, error.message)
    process.exitCode = 1
  } finally {
    if (renderer) act(() => renderer.unmount())
    delete globalThis.window
    delete globalThis.document
  }
}
'@ | node --input-type=module
```

## Chưa được xác minh

- Giao diện, thao tác bàn phím, desktop/mobile và light/dark trên trình duyệt thực: không có Browser kết nối.
- Đăng ký/đăng nhập và nhận email OTP thật, refresh cookie trong trình duyệt, upload avatar lên Cloudinary thật.
- Chat/gửi vị trí/chia sẻ lịch trình giữa hai tài khoản và gọi thoại WebRTC giữa hai thiết bị; quyền microphone/geolocation, ICE/TURN và reconnect trên mạng thật.
- Tải tile/style Amazon Location Maps và tương tác bản đồ thực tế.
- PostgreSQL, Redis, cấu hình triển khai thực tế và migration trên PostgreSQL. H2 và mock không chứng minh các dịch vụ này hoạt động.

Không thao tác ghi dữ liệu lên backend đang deploy. Việc tiếp tục kiểm thử từ đầu đến cuối cần trình duyệt kết nối và môi trường/tài khoản test riêng, với cả REST và WebSocket trỏ đúng môi trường đó.
