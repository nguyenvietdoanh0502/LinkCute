# LinkCute

> Trạng thái: đang phát triển

**LinkCute** là ứng dụng web giúp người dùng khám phá địa điểm và cùng nhau lên kế hoạch đi chơi tại Hà Nội. Thay vì phải chuyển qua lại giữa bản đồ, ứng dụng nhắn tin và ghi chú, người dùng có thể tìm địa điểm, ghép thành lịch trình, mời bạn bè và trao đổi ngay trong một luồng trải nghiệm.

## Bài toán sản phẩm

Một buổi đi chơi thường bắt đầu bằng những câu hỏi: đi đâu, lúc mấy giờ, có mở cửa không và ai sẽ tham gia? Thông tin để trả lời các câu hỏi này đang nằm rải rác ở nhiều nơi, khiến việc thống nhất kế hoạch mất thời gian và dễ bỏ sót.

LinkCute tập trung giải quyết ba việc chính:

- Khám phá địa điểm phù hợp tại Hà Nội theo nhu cầu và khu vực.
- Sắp xếp nhiều địa điểm thành một lịch trình dễ theo dõi.
- Kết nối bạn bè để mời, trao đổi và thống nhất kế hoạch.

## Người dùng mục tiêu

- Nhóm bạn muốn lên kế hoạch cho cuối tuần hoặc một buổi tụ tập.
- Cặp đôi muốn chuẩn bị lịch hẹn theo thời gian và sở thích.
- Người mới đến Hà Nội cần khám phá địa điểm theo khu vực.

## Trải nghiệm cốt lõi

1. Mở bản đồ Hà Nội và khám phá các địa điểm nổi bật.
2. Tìm kiếm hoặc lọc theo danh mục, quận/huyện và trạng thái mở cửa.
3. Xem thông tin chi tiết rồi thêm địa điểm phù hợp vào kế hoạch.
4. Đặt ngày, thời gian và thứ tự cho từng điểm dừng.
5. Mời bạn bè tham gia, trao đổi qua tin nhắn và chia sẻ vị trí khi cần.
6. Lưu kế hoạch để xem lại hoặc tiếp tục chỉnh sửa sau.

## Tính năng hiện có

### Khám phá địa điểm

- Hiển thị địa điểm tại Hà Nội trên danh sách và bản đồ tương tác.
- Tìm kiếm, phân trang và lọc theo danh mục, quận/huyện, trạng thái mở cửa.
- Xem chi tiết địa điểm, tọa độ, ảnh, giờ mở cửa và đánh giá khi dữ liệu có sẵn.
- Hiển thị vị trí hiện tại của người dùng sau khi được cấp quyền.

### Lên kế hoạch

- Tạo nhiều kế hoạch và lưu cục bộ ngay cả khi chưa đăng nhập.
- Thêm, xóa, sắp xếp địa điểm và thiết lập thời gian dự kiến.
- Xuất bản kế hoạch, mời bạn bè và quản lý thành viên.
- Đồng bộ thay đổi của chủ kế hoạch; người được mời hiện xem kế hoạch ở chế độ chỉ đọc.

### Tài khoản và kết nối

- Đăng ký bằng OTP, đăng nhập, làm mới phiên và khôi phục mật khẩu.
- Cập nhật hồ sơ và ảnh đại diện.
- Tìm người dùng bằng mã thành viên, gửi và xử lý lời mời kết bạn.
- Nhắn tin riêng theo thời gian thực qua WebSocket/STOMP.
- Gửi một ảnh chụp tọa độ hiện tại trong cuộc trò chuyện; ứng dụng không theo dõi vị trí trực tiếp.

## Phạm vi hiện tại

**Trong phạm vi:** ứng dụng web responsive, dữ liệu địa điểm tại Hà Nội, khám phá địa điểm, xây dựng lịch trình và phối hợp với bạn bè.

**Chưa nằm trong phạm vi:** đặt chỗ hoặc thanh toán, chỉ đường từng chặng, ứng dụng mobile native và dữ liệu ngoài Hà Nội.

## Công nghệ

| Thành phần | Công nghệ chính |
| --- | --- |
| Frontend | React 19, Vite 8, MapLibre GL, Amazon Location Service |
| Backend | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Dữ liệu | PostgreSQL 15, Flyway, Redis 7, Overture Places và OpenStreetMap |
| Thời gian thực | WebSocket, STOMP |
| Lưu trữ ảnh | Cloudinary |
| Hạ tầng local | Docker Compose |

## Cấu trúc dự án

```text
LinkCute/
├── frontend/       # Ứng dụng React/Vite
├── backend/        # REST API, WebSocket và nghiệp vụ Spring Boot
│   ├── plan/       # BRD, PRD và các tài liệu luồng nghiệp vụ
│   └── scripts/    # Khởi tạo DB, seed và đồng bộ dữ liệu
└── README.md       # Tài liệu tổng quan sản phẩm
```

## Định hướng tiếp theo

- Cải thiện độ phủ và chất lượng dữ liệu địa điểm, ảnh, đánh giá và khoảng giá.
- Ước tính ngân sách và thời gian di chuyển giữa các điểm dừng.
- Hỗ trợ cùng chỉnh sửa, bình chọn và chốt kế hoạch theo nhóm.
- Hoàn thiện thông báo, bảo mật phiên đăng nhập và khả năng quan sát hệ thống.
- Chuẩn hóa tài liệu triển khai và quy trình phát hành.
- Sử dụng AI để tự động gợi ý lộ trình đi chơi hợp lý.

