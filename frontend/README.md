# LinkCute Frontend Demo

Ứng dụng ReactJS/Vite minh họa cách sử dụng backend LinkCute đã deploy tại `https://linkcute.duckdns.org`.

## Chạy trên máy local

Yêu cầu Node.js 20.19+ hoặc 22.12+.

```powershell
cd E:\LinkCute\frontend
npm install
npm run dev
```

Sau đó mở địa chỉ Vite in ra terminal, mặc định là `http://localhost:5173`.

Trong môi trường development, trình duyệt gửi request tới `/api/...`. Vite proxy các request này sang `https://linkcute.duckdns.org`, vì vậy backend không cần cho phép CORS từ `localhost`.

## Biến môi trường

Tạo `.env` từ `.env.example` nếu muốn frontend gọi thẳng một API origin khác:

```env
VITE_API_BASE_URL=https://linkcute.duckdns.org
```

Để trống biến trên khi chạy local qua Vite proxy. Khi deploy frontend và backend khác origin, cần điền URL backend, đồng thời thêm origin của frontend vào `APP_CORS_ALLOWED_ORIGINS` trên backend. Không thêm dấu `/` ở cuối URL.

### Bản đồ Amazon Location Service

Tạo API key trong AWS Console → Amazon Location Service → API keys. Chỉ cấp quyền đọc Maps (`geo-maps:Get*`) và đặt client restrictions cho domain frontend. Sau đó thêm vào `frontend/.env`:

```env
VITE_AWS_LOCATION_API_KEY=v1.public.your-key
VITE_AWS_LOCATION_REGION=ap-southeast-1
VITE_AWS_MAP_STYLE=Standard
VITE_AWS_MAP_COLOR_SCHEME=Light
```

Khởi động lại `npm run dev` sau khi thay đổi `.env`. Frontend dùng Amazon Location Maps v2 qua MapLibre GL và đặt tối đa 100 kết quả từ backend lên bản đồ. Không cần Places API vì backend đã cung cấp `lat/lng`.

API key này là public read-only key dành cho trình duyệt, không phải IAM access key. Tuyệt đối không đưa `AWS_ACCESS_KEY_ID` hoặc `AWS_SECRET_ACCESS_KEY` vào frontend. Nên giới hạn referrer cho `http://localhost:5173/*` khi phát triển và domain frontend thật khi production; không commit file `.env`.

## Build production

```powershell
npm run build
npm run preview
```

Thư mục kết quả là `dist/`. Có thể deploy thư mục này lên Cloudflare Pages, Vercel, Netlify, S3/CloudFront hoặc phục vụ qua Nginx trên EC2.

## API đang được dùng

- `GET /api/v1/places`: tìm kiếm, lọc theo danh mục, quận/huyện, trạng thái mở cửa và phân trang.
- `GET /api/v1/places/{id}`: xem thông tin, ảnh, giờ mở cửa và đánh giá của một địa điểm.
- `GET /api/v1/categories`, `GET /api/v1/districts`: tạo bộ lọc động từ dữ liệu backend.
- Nhóm `/api/v1/auth`: đăng ký, xác thực OTP, đăng nhập, refresh token, đăng xuất, quên/đặt lại/đổi mật khẩu.

API client nằm tại `src/api/client.js`. Nó đọc cấu trúc response chuẩn của backend, chuyển lỗi thành `ApiError`, tự gửi access token và tự refresh một lần khi endpoint được bảo vệ trả về HTTP 401.

## Lưu ý bảo mật

Demo lưu access token và refresh token trong `localStorage` để luồng hoạt động dễ quan sát. Với sản phẩm thực tế, nên ưu tiên refresh token trong cookie `HttpOnly`, `Secure`, `SameSite` do backend thiết lập; kết hợp CSP nghiêm ngặt và chống XSS.
