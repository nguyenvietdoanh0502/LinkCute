# BRD — Phố Khám Phá (Hanoi Mini Explorer)

> Quyết định dữ liệu 2026-07-18: implementation hiện tại dùng **Overture Places + OSM/Overpass** để tương thích Leaflet và lưu PostgreSQL. Google Places enrichment được deferred; rating/review/ảnh/giá là nullable cho đến khi có nguồn và quyền sử dụng phù hợp.

> Business Requirements Document · Version tài liệu: 1.0 · Ngày: 2026-06-11 · Mode: product
> Trạng thái: Draft · Người soạn: Team · Người duyệt: —
> Stakeholders cần ký duyệt: Product Owner, Tech Lead

---

## 1. Tổng quan

**Problem statement.**
Người dân Hà Nội muốn lên kế hoạch đi chơi cuối tuần (ăn uống, xem phim, tham quan) nhưng phải gom thông tin từ nhiều nguồn rời rạc — Google Maps để xem review, Facebook để hỏi bạn bè, blog để tìm gợi ý — không có một công cụ duy nhất cho phép lọc địa điểm theo khu vực, khung giờ, tầm giá và đọc review ngay tại chỗ rồi ghép chúng thành một lịch trình trong một buổi.

**Mục tiêu kinh doanh.**
Xây dựng một web-app cung cấp "Google Maps thu nhỏ" tập trung vào Hà Nội: người dùng có thể khám phá địa điểm ăn uống, giải trí, rạp chiếu phim trên bản đồ — đọc metadata + review thật — và lắp ghép các địa điểm đó thành một kế hoạch đi chơi có thời gian biểu cụ thể.

**Success metrics.**
_(Bỏ qua — đây là solution-first, không gắn KPI kinh doanh ở giai đoạn này.)_

---

## 2. Bối cảnh thị trường

- **Đối thủ chính**:
  - Google Maps — data cực mạnh, nhưng không có tính năng "lập kế hoạch buổi đi chơi" và không phân loại sâu theo ngữ cảnh Hà Nội.
  - Foody / Now — chỉ tập trung giao đồ ăn, không có map-first experience hay itinerary.
  - Klook / Tiqets — tour package, không phải self-serve planning.
  - TimeOut Hanoi — editorial, không cá nhân hoá, không lọc được.
- **Lợi thế cạnh tranh**: Tập trung hẹp vào Hà Nội, UX lên plan trong một luồng duy nhất, dữ liệu review kéo từ nguồn thật (Google Places API) thay vì tự build từ đầu.
- **TAM / SAM / SOM**: [cần xác minh]

---

## 3. Personas

| Persona | Đặc điểm | Nhu cầu chính | Ghi chú |
|---|---|---|---|
| Nhóm bạn cuối tuần | 20–28 tuổi, sống/làm việc tại Hà Nội, ít kế hoạch trước | Tìm nhanh chỗ ăn + chơi trong bán kính gần, khớp khung giờ rảnh | Persona chính |
| Cặp đôi hẹn hò | 22–32 tuổi, muốn "plan đẹp" | Gợi ý theo tầm giá, xem ảnh + đánh giá trước khi quyết | Persona phụ |
| Khách nội địa từ tỉnh khác | 25–40 tuổi, lần đầu hoặc thỉnh thoảng lên Hà Nội | Biết được ở đâu có gì, không quen đường | Persona phụ |

---

## 4. Phạm vi (Scope)

**Trong phạm vi (In scope).**
- Bản đồ Hà Nội hiển thị pin địa điểm theo danh mục (ăn uống, giải trí, rạp phim, cà phê, mua sắm).
- Xem metadata địa điểm: tên, địa chỉ, giờ mở cửa, tầm giá, rating, ảnh, trích dẫn review (kéo từ Google Places API / nguồn miễn phí).
- Tìm kiếm theo từ khoá, lọc theo danh mục, quận/huyện, khung giờ mở cửa, tầm giá.
- Tạo kế hoạch đi chơi trong ngày: thêm nhiều địa điểm, gán khung giờ, xem tổng ngân sách ước tính.
- Lưu kế hoạch (local storage hoặc tài khoản đơn giản — giả định).

**Ngoài phạm vi (Out of scope).**
- Đặt chỗ / booking / thanh toán trực tiếp trong app.
- Chỉ đường / điều hướng / phương thức di chuyển.
- Mở rộng ra ngoài Hà Nội (các tỉnh thành khác).
- Người dùng tự viết review trong app (chỉ aggregate từ nguồn ngoài).
- Tính năng mạng xã hội (follow, chia sẻ cộng đồng, feed).
- Khách sạn / lưu trú.
- Mô hình kiếm tiền (quảng cáo, commission, premium).
- App mobile native (iOS/Android) — chỉ web-app responsive.

---

## 5. Lộ trình theo Version

> Tổng timeline: 7 tuần · Team nhỏ

| Version | Tuần | Trọng tâm | Vấn đề giải quyết | Ghi chú |
|---|---|---|---|---|
| v1 POC | 1–2 | Bản đồ + dữ liệu địa điểm | Hiển thị được map Hà Nội có pin địa điểm, bấm vào xem metadata cơ bản | Dùng Overture Places + OpenStreetMap/Overpass |
| v2 MVP | 3–5 | Tìm kiếm, lọc, detail page | Người dùng tìm được địa điểm phù hợp theo nhu cầu cụ thể | Core user journey hoàn chỉnh |
| v3 Beta | 6–7 | Lên kế hoạch | Ghép địa điểm thành lịch trình có thời gian biểu | Itinerary builder đơn giản |

---

## 6. Business Requirements (BR)

> Quy ước: `BR-<module><nn>`. Mỗi BR atomic, có Priority (MoSCoW) và tiêu chí nghiệm thu sơ bộ.

---

### BR-100 · Bản đồ địa điểm (Map View)

| ID | Tên | Mô tả (nghiệp vụ) | Priority | Version | Tiêu chí nghiệm thu sơ bộ |
|---|---|---|---|---|---|
| BR-101 | Hiển thị bản đồ Hà Nội | Hệ thống hiển thị bản đồ tương tác tập trung vào địa bàn Hà Nội | Must | v1 | Bản đồ load được, zoom/pan mượt, default view là Hà Nội |
| BR-102 | Pin địa điểm trên bản đồ | Các địa điểm trong cơ sở dữ liệu được biểu diễn bằng pin/marker trên bản đồ | Must | v1 | Ít nhất 50 địa điểm hiển thị dưới dạng pin phân bố trên bản đồ |
| BR-103 | Phân loại pin theo danh mục | Pin địa điểm được phân biệt màu sắc/icon theo danh mục (ăn uống, cà phê, giải trí, rạp phim, mua sắm) | Should | v1 | 5 danh mục có icon/màu riêng biệt, phân biệt rõ trên bản đồ |
| BR-104 | Cluster pin khi zoom out | Khi zoom ra xa, các pin gần nhau được gom thành cluster để tránh rối mắt | Could | v2 | Zoom level thấp hiển thị số địa điểm trong cluster; zoom in thì tách ra |

---

### BR-200 · Chi tiết địa điểm (Place Detail & Metadata)

| ID | Tên | Mô tả (nghiệp vụ) | Priority | Version | Tiêu chí nghiệm thu sơ bộ |
|---|---|---|---|---|---|
| BR-201 | Xem thông tin cơ bản | Người dùng xem được tên, địa chỉ, danh mục, số điện thoại của địa điểm | Must | v1 | Card/panel hiển thị đủ 4 trường khi bấm vào pin |
| BR-202 | Xem giờ mở cửa | Người dùng biết địa điểm mở cửa những giờ nào trong tuần | Must | v1 | Hiển thị đúng giờ mở cửa từng ngày; đánh dấu rõ "đang mở" hay "đã đóng" |
| BR-203 | Xem tầm giá | Người dùng biết mức chi phí ước tính khi đến địa điểm | Must | v1 | Hiển thị tầm giá (ví dụ: 50k–150k/người) hoặc mức ký hiệu ₫/₫₫/₫₫₫ |
| BR-204 | Xem ảnh địa điểm | Người dùng xem được ít nhất 1–3 ảnh đại diện của địa điểm | Should | v1 | Ảnh load được, không bị lỗi; fallback là placeholder nếu không có ảnh |
| BR-205 | Xem điểm đánh giá tổng hợp | Người dùng thấy rating trung bình (sao) và số lượt đánh giá của địa điểm | Must | v1 | Hiển thị điểm x.x/5 và tổng số review; nguồn dữ liệu được ghi rõ (vd: "Google") |
| BR-206 | Xem trích dẫn review | Người dùng đọc được 3–5 review nổi bật (tóm tắt) của địa điểm | Should | v2 | Hiển thị tối thiểu 3 review, có tên người viết và ngày; không hiển thị review trống |
| BR-207 | Link ra Google Maps gốc | Người dùng có thể mở trang Google Maps gốc của địa điểm để xem đầy đủ | Should | v1 | Nút "Xem trên Google Maps" mở đúng URL địa điểm trong tab mới |

---

### BR-300 · Tìm kiếm & Lọc (Search & Filter)

| ID | Tên | Mô tả (nghiệp vụ) | Priority | Version | Tiêu chí nghiệm thu sơ bộ |
|---|---|---|---|---|---|
| BR-301 | Tìm kiếm theo từ khoá | Người dùng nhập tên địa điểm hoặc loại hình để tìm kiếm | Must | v2 | Gõ "bún bò" hoặc "phim" trả về kết quả liên quan trong vòng 2s |
| BR-302 | Lọc theo danh mục | Người dùng lọc bản đồ chỉ hiển thị loại địa điểm họ quan tâm | Must | v2 | Chọn "Rạp phim" → chỉ pin rạp phim còn hiển thị; bỏ filter thì quay về toàn bộ |
| BR-303 | Lọc theo quận/khu vực | Người dùng giới hạn kết quả trong một quận hoặc khu vực cụ thể (Hoàn Kiếm, Tây Hồ, Cầu Giấy…) | Should | v2 | Dropdown quận → bản đồ pan + zoom về khu vực đó và chỉ hiện địa điểm trong quận |
| BR-304 | Lọc theo tầm giá | Người dùng lọc địa điểm theo ngưỡng chi phí (dưới 100k, 100k–300k, trên 300k) | Should | v2 | Chọn tầm giá → chỉ hiển thị địa điểm phù hợp; không có dữ liệu giá thì ẩn địa điểm đó |
| BR-305 | Lọc "đang mở cửa" | Người dùng lọc chỉ xem địa điểm đang mở tại thời điểm hiện tại | Should | v2 | Toggle "Đang mở" → chỉ pin địa điểm có giờ mở cửa bao gồm giờ hiện tại |
| BR-306 | Kết hợp nhiều bộ lọc | Người dùng áp dụng đồng thời nhiều điều kiện lọc | Could | v2 | Lọc "Quán cà phê + Quận Hoàn Kiếm + Đang mở" → kết quả chính xác theo tất cả điều kiện |

---

### BR-400 · Lên kế hoạch chuyến đi (Itinerary Planning)

| ID | Tên | Mô tả (nghiệp vụ) | Priority | Version | Tiêu chí nghiệm thu sơ bộ |
|---|---|---|---|---|---|
| BR-401 | Tạo kế hoạch mới | Người dùng khởi tạo một kế hoạch đi chơi cho một ngày cụ thể | Must | v3 | Bấm "Tạo kế hoạch" → đặt tên + ngày → kế hoạch rỗng được tạo và hiển thị |
| BR-402 | Thêm địa điểm vào kế hoạch | Người dùng thêm địa điểm từ bản đồ/kết quả tìm kiếm vào kế hoạch đang tạo | Must | v3 | Bấm "Thêm vào kế hoạch" trên card địa điểm → địa điểm xuất hiện trong danh sách kế hoạch |
| BR-403 | Gán khung giờ cho địa điểm | Người dùng đặt giờ dự kiến đến và rời khỏi mỗi địa điểm trong kế hoạch | Should | v3 | Mỗi địa điểm trong kế hoạch có trường giờ bắt đầu/kết thúc, có thể chỉnh sửa |
| BR-404 | Sắp xếp lại thứ tự địa điểm | Người dùng thay đổi thứ tự các điểm đến trong kế hoạch | Should | v3 | Kéo thả hoặc dùng nút lên/xuống để sắp xếp lại danh sách địa điểm |
| BR-405 | Xem tổng ngân sách ước tính | Hệ thống hiển thị tổng chi phí ước tính cho toàn bộ kế hoạch dựa trên tầm giá mỗi địa điểm | Could | v3 | Kế hoạch có ít nhất 2 địa điểm có dữ liệu giá → hiển thị tổng ước tính với ghi chú "tầm giá" |
| BR-406 | Lưu kế hoạch | Kế hoạch được lưu lại để người dùng xem lại sau | Must | v3 | Refresh trang → kế hoạch vẫn còn (local storage hoặc account); không bị mất dữ liệu |
| BR-407 | Xem kế hoạch dạng timeline | Người dùng thấy các địa điểm trong kế hoạch dưới dạng timeline theo giờ trong ngày | Could | v3 | Hiển thị timeline dọc theo giờ, mỗi block là một địa điểm với thời gian tương ứng |

---

### BR-500 · Tích hợp dữ liệu (Data Integration)

| ID | Tên | Mô tả (nghiệp vụ) | Priority | Version | Tiêu chí nghiệm thu sơ bộ |
|---|---|---|---|---|---|
| BR-501 | Kéo dữ liệu địa điểm từ nguồn mở | Hệ thống nhập Overture Places và ghép OSM/Overpass cho địa điểm Hà Nội | Must | v1 | Có ít nhất 100 địa điểm với tên, tọa độ, danh mục; địa chỉ/contact/giờ mở cửa trả về khi nguồn có |
| BR-502 | Đồng bộ rating & review từ Google | Hệ thống lấy rating tổng hợp và trích dẫn review từ Google Places API (free tier) | Should | v1 | Rating hiển thị đúng khớp với Google Maps; review lấy được tối thiểu 3 bình luận/địa điểm |
| BR-503 | Fallback khi hết quota API | Khi API Google Places hết quota miễn phí trong ngày, hệ thống hiển thị dữ liệu cache thay vì lỗi | Should | v2 | Vượt quota → vẫn hiển thị dữ liệu từ lần cuối fetch, không crash, có thông báo "Dữ liệu có thể chưa cập nhật" |
| BR-504 | Cập nhật dữ liệu định kỳ | Metadata địa điểm được làm mới định kỳ để phản ánh thay đổi thực tế (giờ mở cửa, giá) | Could | v3 | Hệ thống có cơ chế refresh dữ liệu theo lịch (vd: weekly batch); timestamp "cập nhật lần cuối" hiển thị trên UI |

---

## 7. Ràng buộc, Giả định, Phụ thuộc

- **Constraints**:
  - Timeline cố định 7 tuần, team nhỏ (ưu tiên cắt scope thay vì kéo dài thời gian).
  - Ngân sách API: ưu tiên tối đa nguồn miễn phí — Google Places API free tier (~$200/tháng credit), OpenStreetMap/Overpass API (miễn phí hoàn toàn), Nominatim (miễn phí).
  - Web-app responsive, không native mobile.
- **Assumptions**:
  - Google Places API free tier đủ quota cho giai đoạn v1–v2 với lượng người dùng nhỏ _(chưa xác minh — cần tính lượng call/ngày)_.
  - Dữ liệu OpenStreetMap đủ độ phủ cho các địa điểm phổ biến tại Hà Nội _(giả định — có thể thiếu nhiều quán nhỏ)_.
  - Người dùng sử dụng trình duyệt hiện đại (Chrome/Safari/Firefox phiên bản mới).
  - Lưu kế hoạch ở v3 dùng localStorage; tài khoản người dùng là out of scope _(giả định — nếu cần đăng nhập phải tái đánh giá scope)_.
- **Dependencies**:
  - Google Places API (hoặc thay thế: Overpass API + OSM data).
  - Thư viện bản đồ: Leaflet.js (miễn phí, open-source) hoặc Google Maps JS API (free tier).
  - Nguồn dữ liệu seed ban đầu: OpenStreetMap Hanoi dump hoặc crawl thủ công một lần.

---

## 8. Rủi ro

| Rủi ro | Mức độ | Giảm thiểu |
|---|---|---|
| Google Places API hết quota free tier sớm hơn dự kiến | Cao | Cache kết quả aggressively; dùng Overpass API làm primary, Google là secondary; tính toán quota trước khi launch |
| Dữ liệu OSM thiếu nhiều địa điểm quan trọng tại Hà Nội | Trung bình | Bổ sung seed data thủ công cho top 50–100 địa điểm nổi tiếng; lên kế hoạch data enrichment sprint ở v2 |
| Timeline 7 tuần quá ngắn cho cả 3 version | Cao | Cut scope v3 xuống còn add-to-plan + save (bỏ timeline view, bỏ ngân sách nếu cần); v3 là "nice-to-have" |
| Review/ảnh từ Google Places API có thể không đủ phong phú cho địa điểm nhỏ | Trung bình | Fallback: hiển thị link Google Maps thay vì review trực tiếp; đặt kỳ vọng đúng từ đầu |
| UX lên plan phức tạp hơn dự kiến (BR-400) | Trung bình | Prototype itinerary builder sớm ở tuần 4; nếu khó thì đơn giản hoá xuống checklist thay vì drag-and-drop |

---

## Phụ lục — Traceability (điền dần)

| BR | Liên kết PRD (User Story) | Trạng thái |
|---|---|---|
| BR-101 | US-101.1 | Specced |
| BR-102 | US-102.1 | Specced |
| BR-103 | US-103.1 | Specced |
| BR-104 | US-104.1 | Specced |
| BR-201 | US-201.1 | Specced |
| BR-202 | US-202.1 | Specced |
| BR-203 | US-203.1 | Specced |
| BR-204 | US-204.1 | Specced |
| BR-205 | US-205.1 | Specced |
| BR-206 | US-206.1 | Specced |
| BR-207 | US-207.1 | Specced |
| BR-301 | US-301.1 | Specced |
| BR-302 | US-302.1 | Specced |
| BR-303 | US-303.1 | Specced |
| BR-304 | US-304.1 | Specced |
| BR-305 | US-305.1 | Specced |
| BR-306 | US-306.1 | Specced |
| BR-401 | US-401.1 | Specced |
| BR-402 | US-402.1 | Specced |
| BR-403 | US-403.1 | Specced |
| BR-404 | US-404.1 | Specced |
| BR-405 | US-405.1 | Specced |
| BR-406 | US-406.1 | Specced |
| BR-407 | US-407.1 | Specced |
| BR-501 | US-501.1 | Specced |
| BR-502 | US-502.1 | Specced |
| BR-503 | US-503.1 | Specced |
| BR-504 | US-504.1 | Specced |

> Bước tiếp theo: chạy `/write-prd` để bung mỗi BR thành User Story + Acceptance Criteria + Edge cases + Test hooks.
