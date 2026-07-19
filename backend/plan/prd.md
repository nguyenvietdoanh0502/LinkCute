# PRD — Phố Khám Phá (Hanoi Mini Explorer)

> Product Requirements Document · Version tài liệu: 1.0 · Ngày: 2026-06-11 · Mode: product
> Nguồn BRD: docs/brd.md · Phạm vi PRD này: **toàn bộ 27 BR (v1 + v2 + v3)**
> Tech stack: frontend dự kiến **Next.js (App Router) + Leaflet.js**; backend hiện tại **Spring Boot + JPA + PostgreSQL + Redis**.
> Nguồn dữ liệu đã chốt ngày 2026-07-18: **Overture Places làm nền, OSM/Overpass bổ sung `osmId`, contact và `opening_hours`**.
> Rating/review/ảnh/giá không có trong hai nguồn mở này; các yêu cầu Google bên dưới được giữ để truy vết sản phẩm nhưng **deferred**, không bulk-cache hoặc hiển thị Google Places trên Leaflet trong implementation hiện tại.

---

## 1. Tổng quan & Liên kết BRD

**Mục tiêu sản phẩm.**
Web-app "Google Maps thu nhỏ" cho Hà Nội: người dùng khám phá địa điểm ăn uống, giải trí, rạp phim trên bản đồ tương tác; đọc metadata + rating + review thật (kéo từ Google Places, nền dữ liệu từ OpenStreetMap); lọc theo danh mục/quận/giá/giờ mở; rồi ghép các địa điểm thành một kế hoạch đi chơi có thời gian biểu trong một luồng duy nhất.

**Phạm vi PRD này phủ các BR:** BR-101→104, BR-201→207, BR-301→306, BR-401→407, BR-501→504 (toàn bộ 27 BR).

**Quy ước truy vết:** mỗi User Story có ID `US-<BR>.<n>` và truy về đúng một BR. Bảng đối chiếu đầy đủ ở §8.

---

## 2. User Journey

| Bước | Hành động | BR liên quan |
|---|---|---|
| 1 | Mở web-app → thấy bản đồ Hà Nội với pin địa điểm phân loại theo danh mục | BR-101, BR-102, BR-103 |
| 2 | Bấm vào một pin → xem panel chi tiết (tên, địa chỉ, giờ, giá, rating, ảnh, review) | BR-201→207 |
| 3 | Tìm kiếm theo từ khoá hoặc áp bộ lọc (danh mục, quận, giá, đang mở) | BR-301→306 |
| 4 | Bấm "Thêm vào kế hoạch" trên địa điểm ưng ý | BR-402 |
| 5 | Mở kế hoạch → gán giờ, sắp xếp thứ tự, xem tổng ngân sách + timeline | BR-401, BR-403→405, BR-407 |
| 6 | Lưu kế hoạch → quay lại xem sau | BR-406 |
| (nền) | Hệ thống import Overture, match/enrich từ OSM, upsert/archive và refresh bằng script | BR-501→504 |

---

## 3. Functional Requirements (User Stories)

> Mỗi US đủ 5 mục L3: Mục tiêu / Input / Output / Edge / Test. AC viết Given/When/Then.

---

### Từ BR-101 · Hiển thị bản đồ Hà Nội

#### US-101.1 — Xem bản đồ Hà Nội tương tác
*Là `nhóm bạn cuối tuần`, tôi muốn `mở app và thấy ngay bản đồ Hà Nội` để `bắt đầu khám phá mà không phải cấu hình gì`.*

- **Mục tiêu**: Bản đồ load mặc định center vào Hà Nội, cho phép zoom/pan mượt.
- **Input**: Truy cập trang chủ `/`; không cần đăng nhập.
- **Output**: Bản đồ Leaflet hiển thị tile OSM, default center ~ Hồ Gươm (21.0285, 105.8542), zoom level 13.
- **Edge**: Tile server OSM lỗi/chậm → hiển thị skeleton + thông báo "Đang tải bản đồ…", retry sau 3s; không màn hình trắng.
- **Test**: TC-101.1 — render map container, assert center & zoom mặc định; mock tile fail → assert fallback UI.

**Acceptance Criteria**
- **AC-1** — Given người dùng mở `/`, When trang load xong, Then bản đồ hiển thị center Hà Nội ở zoom 13 trong < 3s.
- **AC-2** — Given bản đồ đã hiển thị, When người dùng kéo/zoom, Then thao tác mượt (không giật, ≥ 30fps cảm nhận) và không vượt khỏi bounding box Hà Nội quá xa.

---

### Từ BR-102 · Pin địa điểm trên bản đồ

#### US-102.1 — Hiển thị pin địa điểm
*Là `người dùng`, tôi muốn `thấy các địa điểm dưới dạng pin trên bản đồ` để `biết quanh đây có gì`.*

- **Mục tiêu**: Render marker cho các địa điểm trong viewport hiện tại.
- **Input**: Viewport bounds (bbox) hiện tại của bản đồ → query API địa điểm.
- **Output**: ≥ 50 pin phân bố trên bản đồ; bấm pin mở chi tiết (US-201.1).
- **Edge**: Viewport có > 500 địa điểm → chỉ load tối đa N (vd 200) theo bbox, không render toàn bộ DB; rỗng → "Không có địa điểm trong khu vực này".
- **Test**: TC-102.1 — seed 50 place, assert ≥ 50 marker DOM; pan tới vùng rỗng → assert empty message.

**Acceptance Criteria**
- **AC-1** — Given DB có ≥ 50 địa điểm tại Hà Nội, When bản đồ load ở default view, Then các pin trong viewport hiển thị đúng tọa độ.
- **AC-2** — Given người dùng pan sang khu vực mới, When viewport thay đổi, Then pin được nạp lại theo bbox mới (debounce ≤ 500ms).

---

### Từ BR-103 · Phân loại pin theo danh mục

#### US-103.1 — Pin phân biệt theo danh mục
*Là `người dùng`, tôi muốn `pin có màu/icon riêng theo loại địa điểm` để `nhìn là biết đâu là quán ăn, đâu là rạp phim`.*

- **Mục tiêu**: 5 danh mục (ăn uống, cà phê, giải trí, rạp phim, mua sắm) có icon/màu riêng.
- **Input**: Trường `category` của mỗi địa điểm.
- **Output**: Marker render đúng icon/màu theo category; có chú thích (legend).
- **Edge**: Địa điểm thiếu category hoặc category lạ → dùng icon "khác" mặc định, không crash.
- **Test**: TC-103.1 — seed mỗi category 1 place, assert icon class/màu tương ứng; place category=null → assert default icon.

**Acceptance Criteria**
- **AC-1** — Given 5 danh mục, When bản đồ render pin, Then mỗi danh mục có icon/màu phân biệt rõ ràng.
- **AC-2** — Given một địa điểm không có category hợp lệ, When render, Then dùng icon mặc định, không lỗi.

---

### Từ BR-104 · Cluster pin khi zoom out

#### US-104.1 — Gom pin thành cluster
*Là `người dùng`, tôi muốn `các pin gần nhau gộp lại khi zoom xa` để `bản đồ không bị rối`.*

- **Mục tiêu**: Dùng marker clustering (Leaflet.markercluster) gom pin ở zoom thấp.
- **Input**: Tập marker hiện tại + zoom level.
- **Output**: Zoom out → cluster hiển thị số đếm; bấm cluster hoặc zoom in → tách ra.
- **Edge**: Hai địa điểm trùng tọa độ → cluster vẫn đếm đúng số, bấm vào tách spiderfy.
- **Test**: TC-104.1 — seed 100 place dày đặc, zoom level 11 → assert có cluster với count; zoom 16 → assert pin riêng lẻ.

**Acceptance Criteria**
- **AC-1** — Given nhiều pin gần nhau, When zoom out tới level thấp, Then chúng gom thành cluster hiển thị số lượng.
- **AC-2** — Given một cluster, When bấm vào, Then bản đồ zoom in và tách các pin thành viên.

---

### Từ BR-201 · Xem thông tin cơ bản

#### US-201.1 — Xem panel chi tiết địa điểm
*Là `người dùng`, tôi muốn `bấm vào pin để xem thông tin cơ bản` để `biết đó là chỗ gì, ở đâu`.*

- **Mục tiêu**: Mở panel/card hiển thị tên, địa chỉ, danh mục, số điện thoại.
- **Input**: `placeId` từ pin được bấm.
- **Output**: Panel bên (desktop) / bottom sheet (mobile) với 4 trường cơ bản + nút đóng.
- **Edge**: Thiếu số điện thoại → ẩn dòng đó thay vì hiển thị "null"; placeId không tồn tại → toast "Không tìm thấy địa điểm".
- **Test**: TC-201.1 — click pin → assert panel render đủ trường có dữ liệu; place thiếu phone → assert dòng phone ẩn.

**Acceptance Criteria**
- **AC-1** — Given người dùng bấm một pin, When panel mở, Then hiển thị tên, địa chỉ, danh mục, SĐT (nếu có).
- **AC-2** — Given một trường bị thiếu dữ liệu, When render panel, Then trường đó được ẩn, không hiển thị giá trị rỗng/null.

---

### Từ BR-202 · Xem giờ mở cửa

#### US-202.1 — Xem giờ mở cửa & trạng thái mở/đóng
*Là `người dùng`, tôi muốn `biết địa điểm mở cửa giờ nào và hiện đang mở không` để `khỏi đến lúc đóng cửa`.*

- **Mục tiêu**: Hiển thị giờ mở cửa theo từng ngày + badge "Đang mở/Đã đóng" theo giờ hiện tại.
- **Input**: `openingHours` (chuẩn hoá từ OSM `opening_hours` / Google), thời gian hiện tại (Asia/Ho_Chi_Minh).
- **Output**: Bảng giờ 7 ngày; badge trạng thái màu (xanh=mở, xám=đóng).
- **Edge**: Không có dữ liệu giờ → hiển thị "Chưa có thông tin giờ mở cửa", không tính trạng thái; giờ qua nửa đêm (vd 18:00–02:00) phải tính đúng.
- **Test**: TC-202.1 — place mở 08:00–22:00, mock now=10:00 → badge "Đang mở"; now=23:00 → "Đã đóng"; place giờ qua đêm → assert đúng.

**Acceptance Criteria**
- **AC-1** — Given địa điểm có giờ mở cửa, When xem chi tiết, Then hiển thị giờ từng ngày và badge trạng thái theo giờ VN hiện tại.
- **AC-2** — Given địa điểm không có dữ liệu giờ, When xem chi tiết, Then hiển thị "Chưa có thông tin", không hiển thị badge sai.

---

### Từ BR-203 · Xem tầm giá

#### US-203.1 — Xem tầm giá
*Là `cặp đôi hẹn hò`, tôi muốn `biết tầm giá của địa điểm` để `chọn chỗ hợp ngân sách`.*

- **Mục tiêu**: Hiển thị tầm giá dạng khoảng (vd 50k–150k/người) hoặc ký hiệu ₫/₫₫/₫₫₫.
- **Input**: `priceLevel` (0–4 từ Google) hoặc `priceRange` (min–max, nếu seed thủ công).
- **Output**: Chip tầm giá trong panel chi tiết.
- **Edge**: Không có dữ liệu giá → "Chưa cập nhật giá", và địa điểm này sẽ bị ẩn khi lọc theo giá (xem US-304.1).
- **Test**: TC-203.1 — priceLevel=2 → assert "₫₫"; priceRange 50k–150k → assert hiển thị khoảng; null → assert "Chưa cập nhật giá".

**Acceptance Criteria**
- **AC-1** — Given địa điểm có tầm giá, When xem chi tiết, Then hiển thị khoảng giá hoặc ký hiệu mức giá rõ ràng.
- **AC-2** — Given không có dữ liệu giá, When xem chi tiết, Then hiển thị "Chưa cập nhật giá".

---

### Từ BR-204 · Xem ảnh địa điểm

#### US-204.1 — Xem ảnh đại diện
*Là `người dùng`, tôi muốn `xem vài ảnh của địa điểm` để `hình dung không gian trước khi đi`.*

- **Mục tiêu**: Hiển thị 1–3 ảnh đại diện.
- **Input**: `photos[]` (Google Places photo reference hoặc URL đã cache).
- **Output**: Carousel/grid 1–3 ảnh; lazy-load.
- **Edge**: Không có ảnh → placeholder theo danh mục (vd icon dao nĩa cho quán ăn); ảnh lỗi → ẩn ảnh đó.
- **Test**: TC-204.1 — place có 3 photo → assert 3 img; place 0 photo → assert placeholder; mock img onerror → assert ảnh bị ẩn.

**Acceptance Criteria**
- **AC-1** — Given địa điểm có ảnh, When xem chi tiết, Then hiển thị 1–3 ảnh, lazy-load.
- **AC-2** — Given không có ảnh, When xem chi tiết, Then hiển thị placeholder theo danh mục.

---

### Từ BR-205 · Xem điểm đánh giá tổng hợp

#### US-205.1 — Xem rating tổng hợp
*Là `người dùng`, tôi muốn `thấy điểm đánh giá trung bình và số lượt` để `đánh giá nhanh độ tin cậy`.*

- **Mục tiêu**: Hiển thị rating x.x/5 + tổng số review + ghi rõ nguồn.
- **Input**: `rating`, `userRatingsTotal`, `ratingSource` (vd "Google").
- **Output**: Hàng sao + "4.3/5 · 1.2k đánh giá · Nguồn: Google".
- **Edge**: Chưa có rating → "Chưa có đánh giá"; số lượt = 0 → ẩn phần count.
- **Test**: TC-205.1 — rating=4.3, total=1200 → assert "4.3/5" + "1,2k"; rating=null → assert "Chưa có đánh giá".

**Acceptance Criteria**
- **AC-1** — Given địa điểm có rating, When xem chi tiết, Then hiển thị điểm /5, số lượt, và nguồn dữ liệu.
- **AC-2** — Given không có rating, When xem chi tiết, Then hiển thị "Chưa có đánh giá".

---

### Từ BR-206 · Xem trích dẫn review

#### US-206.1 — Đọc review nổi bật
*Là `người dùng`, tôi muốn `đọc vài review thật của địa điểm` để `có dẫn chứng trước khi quyết định`.*

- **Mục tiêu**: Hiển thị 3–5 review (tên người viết, sao, ngày, nội dung tóm tắt).
- **Input**: `reviews[]` từ Google Places (đã cache trong DB).
- **Output**: Danh sách review; mỗi item có tên, sao, ngày tương đối ("2 tháng trước"), text.
- **Edge**: Review trống nội dung → bỏ qua; < 3 review có sẵn → hiển thị số có; 0 review → ẩn section, gợi ý "Xem trên Google Maps" (US-207.1).
- **Test**: TC-206.1 — place có 5 review → assert hiển thị ≤5, không có review rỗng; place 0 review → assert section ẩn.

**Acceptance Criteria**
- **AC-1** — Given địa điểm có ≥ 3 review, When xem chi tiết, Then hiển thị 3–5 review với tên, sao, ngày, nội dung.
- **AC-2** — Given review có nội dung rỗng, When render, Then review đó bị loại khỏi danh sách.

---

### Từ BR-207 · Link ra Google Maps gốc

#### US-207.1 — Mở Google Maps gốc
*Là `người dùng`, tôi muốn `mở trang Google Maps của địa điểm` để `xem đầy đủ hơn khi cần`.*

- **Mục tiêu**: Nút "Xem trên Google Maps" mở URL gốc tab mới.
- **Input**: `googlePlaceId` hoặc tọa độ + tên.
- **Output**: Link `https://www.google.com/maps/place/?q=place_id:<id>` mở tab mới (`rel=noopener`).
- **Edge**: Không có `googlePlaceId` → fallback URL theo lat/lng + tên; cả hai thiếu → ẩn nút.
- **Test**: TC-207.1 — place có placeId → assert href đúng + target=_blank; không có → assert fallback href; thiếu cả hai → assert nút ẩn.

**Acceptance Criteria**
- **AC-1** — Given địa điểm có Google Place ID, When bấm "Xem trên Google Maps", Then mở đúng trang địa điểm trong tab mới.
- **AC-2** — Given không có Place ID nhưng có tọa độ, When bấm, Then mở Google Maps theo tọa độ + tên.

---

### Từ BR-301 · Tìm kiếm theo từ khoá

#### US-301.1 — Tìm địa điểm theo từ khoá
*Là `người dùng`, tôi muốn `gõ tên món/loại hình để tìm` để `nhanh ra chỗ mình cần`.*

- **Mục tiêu**: Ô tìm kiếm trả kết quả theo tên + danh mục + (tuỳ chọn) địa chỉ.
- **Input**: Chuỗi query (≥ 1 ký tự); debounce input.
- **Output**: Danh sách kết quả (list + highlight pin tương ứng trên bản đồ) trong ≤ 2s.
- **Edge**: Query rỗng → giữ nguyên bản đồ; không khớp → "Không tìm thấy, thử từ khoá khác"; dấu tiếng Việt → tìm được cả có/không dấu ("bun bo" ≈ "bún bò").
- **Test**: TC-301.1 — query "bún bò" → assert kết quả chứa địa điểm liên quan; query "xyz123" → assert empty state; query không dấu → assert vẫn khớp.

**Acceptance Criteria**
- **AC-1** — Given DB có địa điểm khớp, When gõ "bún bò", Then trả kết quả liên quan trong ≤ 2s và highlight pin.
- **AC-2** — Given gõ không dấu "pho", When tìm, Then vẫn khớp địa điểm "phở".

---

### Từ BR-302 · Lọc theo danh mục

#### US-302.1 — Lọc theo danh mục
*Là `người dùng`, tôi muốn `chỉ hiện loại địa điểm mình quan tâm` để `bớt nhiễu`.*

- **Mục tiêu**: Chip/checkbox danh mục lọc pin trên bản đồ + list.
- **Input**: Tập category được chọn.
- **Output**: Chỉ pin thuộc category đã chọn hiển thị; bỏ chọn → về toàn bộ.
- **Edge**: Chọn category không có địa điểm trong viewport → empty state; chọn nhiều category → hợp (OR).
- **Test**: TC-302.1 — chọn "Rạp phim" → assert chỉ pin rạp phim; bỏ chọn → assert full lại.

**Acceptance Criteria**
- **AC-1** — Given chọn danh mục "Rạp phim", When áp filter, Then chỉ pin rạp phim hiển thị.
- **AC-2** — Given bỏ toàn bộ filter danh mục, When áp dụng, Then tất cả pin hiển thị lại.

---

### Từ BR-303 · Lọc theo quận/khu vực

#### US-303.1 — Lọc theo quận
*Là `khách nội địa`, tôi muốn `giới hạn theo quận` để `tập trung khu vực mình ở`.*

- **Mục tiêu**: Dropdown quận (Hoàn Kiếm, Tây Hồ, Cầu Giấy…) → bản đồ pan/zoom về quận + lọc.
- **Input**: `district` được chọn.
- **Output**: Bản đồ fit bounds quận đó; chỉ hiển thị địa điểm thuộc quận.
- **Edge**: Quận chưa có dữ liệu → empty + giữ map ở quận đó; địa điểm thiếu trường district → loại khỏi kết quả khi lọc.
- **Test**: TC-303.1 — chọn "Hoàn Kiếm" → assert map bounds + chỉ place district=Hoàn Kiếm.

**Acceptance Criteria**
- **AC-1** — Given chọn quận "Hoàn Kiếm", When áp dụng, Then bản đồ fit về quận đó và chỉ hiển thị địa điểm trong quận.
- **AC-2** — Given quận không có địa điểm nào, When áp dụng, Then hiển thị empty state, không lỗi.

---

### Từ BR-304 · Lọc theo tầm giá

#### US-304.1 — Lọc theo tầm giá
*Là `cặp đôi hẹn hò`, tôi muốn `lọc theo ngưỡng chi phí` để `chọn chỗ vừa túi tiền`.*

- **Mục tiêu**: Lọc theo nhóm giá (dưới 100k / 100k–300k / trên 300k).
- **Input**: Ngưỡng giá chọn + `priceLevel`/`priceRange` của địa điểm.
- **Output**: Chỉ địa điểm trong ngưỡng hiển thị.
- **Edge**: Địa điểm không có dữ liệu giá → bị ẩn khi filter giá bật (và ghi chú "X địa điểm bị ẩn do thiếu giá").
- **Test**: TC-304.1 — filter "dưới 100k" → assert chỉ place phù hợp; place null giá → assert bị ẩn + đếm đúng số ẩn.

**Acceptance Criteria**
- **AC-1** — Given chọn "dưới 100k", When áp dụng, Then chỉ địa điểm có giá phù hợp hiển thị.
- **AC-2** — Given địa điểm thiếu dữ liệu giá, When filter giá bật, Then địa điểm đó bị ẩn và hệ thống thông báo số lượng bị ẩn.

---

### Từ BR-305 · Lọc "đang mở cửa"

#### US-305.1 — Lọc địa điểm đang mở
*Là `người dùng`, tôi muốn `chỉ xem chỗ đang mở` để `đi được luôn`.*

- **Mục tiêu**: Toggle "Đang mở" lọc theo giờ hiện tại (Asia/Ho_Chi_Minh).
- **Input**: Toggle state + `openingHours` + now.
- **Output**: Chỉ địa điểm đang mở hiển thị.
- **Edge**: Địa điểm không có dữ liệu giờ → bị ẩn khi toggle bật; giờ qua nửa đêm tính đúng.
- **Test**: TC-305.1 — mock now=21:00, toggle on → assert chỉ place mở lúc 21:00; place thiếu giờ → assert ẩn.

**Acceptance Criteria**
- **AC-1** — Given bật toggle "Đang mở", When áp dụng, Then chỉ địa điểm có giờ bao gồm giờ hiện tại hiển thị.
- **AC-2** — Given địa điểm thiếu dữ liệu giờ, When toggle bật, Then địa điểm đó bị ẩn.

---

### Từ BR-306 · Kết hợp nhiều bộ lọc

#### US-306.1 — Áp đồng thời nhiều bộ lọc
*Là `người dùng`, tôi muốn `kết hợp danh mục + quận + giá + đang mở` để `ra đúng chỗ mình cần`.*

- **Mục tiêu**: Áp đồng thời nhiều filter (AND giữa các loại filter, OR trong cùng loại).
- **Input**: State tổng hợp các filter + query tìm kiếm.
- **Output**: Kết quả khớp tất cả điều kiện; hiển thị chip "đang lọc" + nút "Xoá tất cả".
- **Edge**: Tổ hợp filter ra rỗng → empty state + gợi ý nới lỏng filter; URL phản ánh filter (shareable, tuỳ chọn).
- **Test**: TC-306.1 — "Cà phê + Hoàn Kiếm + đang mở" → assert kết quả thoả cả 3; tổ hợp rỗng → assert empty + nút xoá filter.

**Acceptance Criteria**
- **AC-1** — Given chọn danh mục + quận + đang mở, When áp dụng, Then kết quả thoả tất cả điều kiện.
- **AC-2** — Given tổ hợp filter không ra kết quả, When áp dụng, Then hiển thị empty state với gợi ý xoá bớt filter.

---

### Từ BR-401 · Tạo kế hoạch mới

#### US-401.1 — Tạo kế hoạch đi chơi
*Là `nhóm bạn cuối tuần`, tôi muốn `tạo một kế hoạch cho một ngày` để `bắt đầu gom địa điểm`.*

- **Mục tiêu**: Khởi tạo plan rỗng với tên + ngày.
- **Input**: Tên kế hoạch, ngày (date picker).
- **Output**: Plan rỗng được tạo và mở ở panel kế hoạch.
- **Edge**: Tên rỗng → tự đặt "Kế hoạch <ngày>"; ngày quá khứ → cảnh báo nhưng vẫn cho tạo.
- **Test**: TC-401.1 — tạo plan với tên + ngày → assert plan tồn tại & hiển thị; tên rỗng → assert tên mặc định.

**Acceptance Criteria**
- **AC-1** — Given người dùng bấm "Tạo kế hoạch" và nhập tên + ngày, When xác nhận, Then plan rỗng được tạo và hiển thị.
- **AC-2** — Given để trống tên, When tạo, Then hệ thống tự đặt tên mặc định theo ngày.

---

### Từ BR-402 · Thêm địa điểm vào kế hoạch

#### US-402.1 — Thêm địa điểm vào kế hoạch
*Là `người dùng`, tôi muốn `thêm địa điểm đang xem vào kế hoạch` để `gom dần lịch trình`.*

- **Mục tiêu**: Nút "Thêm vào kế hoạch" trên card/panel địa điểm.
- **Input**: `placeId` + plan đang active.
- **Output**: Địa điểm xuất hiện cuối danh sách plan; toast xác nhận.
- **Edge**: Chưa có plan active → prompt tạo plan trước (hoặc tạo nhanh); thêm trùng địa điểm → cảnh báo "đã có trong kế hoạch", không thêm 2 lần.
- **Test**: TC-402.1 — add place vào plan → assert có trong list; add lại → assert không nhân đôi.

**Acceptance Criteria**
- **AC-1** — Given có plan active, When bấm "Thêm vào kế hoạch", Then địa điểm được thêm vào danh sách plan.
- **AC-2** — Given địa điểm đã có trong plan, When thêm lại, Then hệ thống cảnh báo và không thêm trùng.

---

### Từ BR-403 · Gán khung giờ cho địa điểm

#### US-403.1 — Gán giờ đến/rời cho địa điểm
*Là `người dùng`, tôi muốn `đặt giờ đến và rời mỗi điểm` để `lịch trình có thời gian biểu`.*

- **Mục tiêu**: Mỗi item plan có trường giờ bắt đầu/kết thúc chỉnh sửa được.
- **Input**: `startTime`, `endTime` cho mỗi `PlanItem`.
- **Output**: Giờ lưu vào item; hiển thị trên list & timeline.
- **Edge**: endTime < startTime → cảnh báo, không lưu; trùng giờ giữa 2 điểm → cảnh báo overlap nhưng vẫn cho lưu.
- **Test**: TC-403.1 — set 10:00–11:30 → assert lưu; set end < start → assert validation lỗi; 2 item overlap → assert cảnh báo.

**Acceptance Criteria**
- **AC-1** — Given một địa điểm trong plan, When đặt giờ đến/rời hợp lệ, Then giờ được lưu và hiển thị.
- **AC-2** — Given giờ kết thúc trước giờ bắt đầu, When lưu, Then hệ thống báo lỗi và không lưu.

---

### Từ BR-404 · Sắp xếp lại thứ tự địa điểm

#### US-404.1 — Sắp xếp lại điểm đến
*Là `người dùng`, tôi muốn `đổi thứ tự các điểm` để `lịch trình hợp lý hơn`.*

- **Mục tiêu**: Kéo-thả (hoặc nút lên/xuống) để đổi thứ tự item.
- **Input**: Hành động reorder + `orderIndex`.
- **Output**: Thứ tự mới được lưu và phản ánh ở list + timeline.
- **Edge**: Kéo ra ngoài vùng → huỷ thao tác, giữ thứ tự cũ; chỉ 1 item → không có gì để sắp.
- **Test**: TC-404.1 — reorder 3 item từ [A,B,C] thành [B,A,C] → assert orderIndex cập nhật & persist.

**Acceptance Criteria**
- **AC-1** — Given plan có ≥ 2 địa điểm, When kéo-thả/đổi thứ tự, Then thứ tự mới được lưu.
- **AC-2** — Given thao tác kéo bị huỷ, When thả ngoài vùng, Then thứ tự cũ được giữ nguyên.

---

### Từ BR-405 · Xem tổng ngân sách ước tính

#### US-405.1 — Xem tổng ngân sách kế hoạch
*Là `nhóm bạn cuối tuần`, tôi muốn `thấy tổng chi phí ước tính` để `cân đối ngân sách chung`.*

- **Mục tiêu**: Tính tổng tầm giá các địa điểm trong plan có dữ liệu giá.
- **Input**: `priceRange`/`priceLevel` của các item.
- **Output**: Tổng dạng khoảng (vd "≈ 250k–600k/người") + ghi chú "ước tính, chỉ tính địa điểm có giá".
- **Edge**: Không item nào có giá → ẩn tổng hoặc "Chưa đủ dữ liệu giá"; một số item thiếu giá → tính phần có + ghi chú số bị bỏ.
- **Test**: TC-405.1 — 2 item có range → assert tổng min/max đúng; 0 item có giá → assert ẩn/thông báo.

**Acceptance Criteria**
- **AC-1** — Given plan có ≥ 2 địa điểm có dữ liệu giá, When xem plan, Then hiển thị tổng ước tính dạng khoảng với ghi chú.
- **AC-2** — Given không địa điểm nào có giá, When xem plan, Then hiển thị "Chưa đủ dữ liệu giá".

---

### Từ BR-406 · Lưu kế hoạch

#### US-406.1 — Lưu & khôi phục kế hoạch
*Là `người dùng`, tôi muốn `kế hoạch được lưu lại` để `xem lại sau mà không mất`.*

- **Mục tiêu**: Persist plan (localStorage ở client + tuỳ chọn DB nếu có account — account là out of scope BRD).
- **Input**: Plan hiện tại (autosave on change).
- **Output**: Refresh trang → plan vẫn còn; danh sách "Kế hoạch của tôi".
- **Edge**: localStorage đầy/bị chặn → cảnh báo "Không lưu được, kiểm tra trình duyệt"; nhiều tab → đồng bộ best-effort, không mất dữ liệu tab đang sửa.
- **Test**: TC-406.1 — tạo plan → reload → assert plan còn; mock localStorage throw → assert cảnh báo.

**Acceptance Criteria**
- **AC-1** — Given đã tạo plan, When reload trang, Then plan vẫn còn nguyên nội dung.
- **AC-2** — Given localStorage không khả dụng, When lưu, Then hệ thống cảnh báo rõ ràng, không mất dữ liệu im lặng.

---

### Từ BR-407 · Xem kế hoạch dạng timeline

#### US-407.1 — Xem kế hoạch dạng timeline
*Là `người dùng`, tôi muốn `xem lịch trình theo trục giờ trong ngày` để `dễ hình dung cả buổi`.*

- **Mục tiêu**: Hiển thị các item theo timeline dọc theo giờ.
- **Input**: Các `PlanItem` có `startTime`/`endTime`.
- **Output**: Timeline với block theo giờ; item chưa có giờ xếp cuối "Chưa xếp giờ".
- **Edge**: Item overlap → vẽ chồng/cảnh báo; item không có giờ → khu vực riêng.
- **Test**: TC-407.1 — 3 item có giờ → assert vị trí block theo giờ; item thiếu giờ → assert ở khu "chưa xếp".

**Acceptance Criteria**
- **AC-1** — Given plan có item đã gán giờ, When mở timeline, Then mỗi item là block đúng vị trí theo trục giờ.
- **AC-2** — Given item chưa gán giờ, When mở timeline, Then item nằm ở khu "Chưa xếp giờ".

---

### Từ BR-501 · Kéo dữ liệu địa điểm từ nguồn miễn phí

#### US-501.1 — Seed địa điểm từ Overture + OSM
*Là `Data Engineer (team)`, tôi muốn `nhập địa điểm Hà Nội từ Overture rồi ghép OSM` để `có nền dữ liệu mở, tọa độ POI thật và giờ mở cửa`.*

- **Mục tiêu**: Script tải Overture GeoJSON + Overpass JSON → lọc bbox/confidence/category → match → chuẩn hoá → ghi DB.
- **Input**: Overture Places theo bbox Hà Nội và Overpass tags (`amenity=restaurant/cafe/cinema`, `shop`, …).
- **Output**: ≥ 100 địa điểm với tên, địa chỉ, tọa độ, danh mục được map về 5 category chuẩn.
- **Edge**: POI thiếu tên/tọa độ hoặc ngoài Hà Nội → bỏ qua; chạy lại → upsert theo `overtureId`/`osmId`, archive ID không còn trong nguồn; matching OSM theo tên chuẩn hoá + bán kính 150 m.
- **Test**: TC-501.1 — chạy ETL trên fixture Overpass → assert ≥ 100 record hợp lệ; chạy 2 lần → assert không trùng.

**Acceptance Criteria**
- **AC-1** — Given chạy script seed, When hoàn tất, Then DB có ≥ 100 địa điểm đủ 4 trường (tên, địa chỉ, tọa độ, danh mục).
- **AC-2** — Given chạy lại script, When upsert, Then không tạo bản ghi trùng (idempotent theo `overtureId`/`osmId`).

---

### Từ BR-502 · Đồng bộ rating & review từ Google

#### US-502.1 — Enrich rating/review từ Google Places
*Là `Data Engineer (team)`, tôi muốn `bổ sung rating, review, ảnh từ Google Places` để `dữ liệu giàu và đáng tin`.*

- **Mục tiêu**: Với mỗi địa điểm, gọi Google Places (Find Place + Details) lấy rating, review, photos, priceLevel, opening_hours; cache vào DB.
- **Input**: Tên + tọa độ địa điểm (matching), Google API key.
- **Output**: Trường `rating`, `userRatingsTotal`, `reviews[]` (≥3 nếu có), `photos[]`, `priceLevel`, `googlePlaceId` được điền.
- **Edge**: Không match được Google place → giữ dữ liệu OSM, đánh dấu `enriched=false`; nhiều kết quả → chọn theo khoảng cách gần nhất + tên khớp.
- **Test**: TC-502.1 — mock Google response → assert rating/review ghi đúng; no-match → assert enriched=false, không ghi đè dữ liệu OSM.

**Acceptance Criteria**
- **AC-1** — Given địa điểm match được trên Google, When enrich, Then rating khớp Google Maps và lấy được ≥ 3 review (nếu địa điểm có).
- **AC-2** — Given không match được, When enrich, Then giữ nguyên dữ liệu OSM và đánh dấu chưa enrich.

---

### Từ BR-503 · Fallback khi hết quota API

#### US-503.1 — Phục vụ từ cache khi hết quota
*Là `người dùng`, tôi muốn `app vẫn chạy khi Google hết quota` để `không gặp lỗi trắng màn hình`.*

- **Mục tiêu**: App đọc dữ liệu đã cache trong DB; không gọi Google realtime ở luồng người dùng.
- **Input**: Trạng thái quota (job enrich báo lỗi `OVER_QUERY_LIMIT`).
- **Output**: UI vẫn hiển thị dữ liệu cache; banner "Dữ liệu có thể chưa cập nhật" nếu lần refresh gần nhất quá cũ.
- **Edge**: Job enrich gặp `OVER_QUERY_LIMIT` → dừng job, retry hôm sau, không crash; dữ liệu chưa từng fetch → hiển thị phần OSM có sẵn.
- **Test**: TC-503.1 — mock Google trả OVER_QUERY_LIMIT → assert job dừng gracefully + dữ liệu cache vẫn phục vụ; assert banner khi lastSync cũ.

**Acceptance Criteria**
- **AC-1** — Given Google API hết quota, When người dùng dùng app, Then dữ liệu cache vẫn hiển thị bình thường, không lỗi.
- **AC-2** — Given lần đồng bộ gần nhất quá cũ, When xem địa điểm, Then hiển thị banner "Dữ liệu có thể chưa cập nhật".

---

### Từ BR-504 · Cập nhật dữ liệu định kỳ

#### US-504.1 — Refresh dữ liệu định kỳ
*Là `Data Engineer (team)`, tôi muốn `dữ liệu được làm mới theo lịch` để `phản ánh thay đổi thực tế`.*

- **Mục tiêu**: Job định kỳ (vd weekly) re-run enrich cho địa điểm cũ; lưu `lastSyncedAt`.
- **Input**: Lịch chạy (cron) + danh sách địa điểm sắp theo `lastSyncedAt` cũ nhất.
- **Output**: Trường dữ liệu cập nhật; `lastSyncedAt` mới; UI hiển thị "Cập nhật lần cuối: …".
- **Edge**: Job vượt quota giữa chừng → xử lý phần đã làm, phần còn lại để lần sau (resumable theo lastSyncedAt); job lỗi → log + alert, không xoá dữ liệu cũ.
- **Test**: TC-504.1 — chạy job với quota giới hạn → assert cập nhật theo thứ tự cũ nhất trước + lastSyncedAt thay đổi; UI assert hiển thị timestamp.

**Acceptance Criteria**
- **AC-1** — Given job refresh chạy theo lịch, When hoàn tất một phần, Then các địa điểm cũ nhất được cập nhật trước và `lastSyncedAt` thay đổi.
- **AC-2** — Given xem chi tiết địa điểm, When đã có dữ liệu đồng bộ, Then UI hiển thị thời điểm cập nhật lần cuối.

---

## 4. Non-Functional Requirements (NFR)

| Loại | Yêu cầu | Đo lường |
|---|---|---|
| Hiệu năng | Map load ≤ 3s; API địa điểm theo bbox trả ≤ 500ms (p95); tìm kiếm ≤ 2s | Lighthouse/load test trên seed 1k place |
| Hiệu năng | Render pin theo bbox + clustering, không nạp toàn bộ DB cùng lúc | Giới hạn ≤ 200 marker/viewport |
| Khả dụng | App hoạt động khi Google hết quota (đọc cache DB) | Chaos test mock OVER_QUERY_LIMIT (US-503.1) |
| Bảo mật | Google API key chỉ ở server (API route/job), không lộ ra client; rate-limit endpoint công khai | Code review + kiểm tra bundle client không chứa key |
| Chi phí | Tối đa free tier: Overpass (free), Google Places free credit; cache để giảm call | Đếm số call Google/ngày < ngưỡng credit |
| i18n / Locale | UI tiếng Việt; tìm kiếm hỗ trợ không dấu; timezone Asia/Ho_Chi_Minh cho giờ mở cửa | Test unaccent search + tính trạng thái mở theo giờ VN |
| Accessibility | Tương phản đạt WCAG AA; pin/nút có aria-label; thao tác bàn phím cơ bản | Audit axe trên các màn chính |
| Responsive | Web responsive: panel chi tiết → bottom sheet trên mobile | Test breakpoint mobile/desktop |
| Khả chuyển dữ liệu | ETL idempotent (upsert theo osmId/googlePlaceId), resumable | TC-501.1, TC-504.1 |

---

## 5. Data Model

> PostgreSQL + Prisma. Tọa độ lưu lat/lng (đủ cho bbox query; PostGIS là tuỳ chọn nâng cao).

| Bảng / Entity | Trường chính | Quan hệ | BR liên quan |
|---|---|---|---|
| **Place** | id, osmId (unique), googlePlaceId (nullable, unique), name, address, district, lat, lng, category (enum), phone, priceLevel, priceMin, priceMax, rating, userRatingsTotal, ratingSource, enriched (bool), lastSyncedAt | 1-n OpeningHour, 1-n Photo, 1-n Review, n-m Plan (qua PlanItem) | BR-101→103, BR-201→205, BR-501→504 |
| **OpeningHour** | id, placeId (FK), dayOfWeek (0–6), openTime, closeTime, crossesMidnight (bool) | n-1 Place | BR-202, BR-305 |
| **Photo** | id, placeId (FK), url / googlePhotoRef, width, height, order | n-1 Place | BR-204 |
| **Review** | id, placeId (FK), authorName, rating, text, relativeTime, publishedAt, source | n-1 Place | BR-206 |
| **Plan** | id, name, date, ownerKey (client/localStorage id; nullable), estTotalMin, estTotalMax, createdAt, updatedAt | 1-n PlanItem | BR-401, BR-405, BR-406 |
| **PlanItem** | id, planId (FK), placeId (FK), orderIndex, startTime, endTime | n-1 Plan, n-1 Place | BR-402→405, BR-407 |
| **SyncLog** | id, jobType, startedAt, finishedAt, status, placesUpdated, error (nullable), quotaHit (bool) | — | BR-503, BR-504 |

**Enum Category**: `FOOD`, `CAFE`, `ENTERTAINMENT`, `CINEMA`, `SHOPPING`, `OTHER`.

---

## 6. API Endpoints

> Next.js App Router route handlers. Google key chỉ dùng server-side (jobs/enrich), không expose client.

| Method | Path | Mục đích | Auth | BR |
|---|---|---|---|---|
| GET | `/api/places?bbox=&category=&district=&price=&openNow=&q=` | Truy vấn địa điểm theo viewport + bộ lọc + tìm kiếm | No | BR-102, BR-301→306 |
| GET | `/api/places/:id` | Chi tiết một địa điểm (kèm hours, photos, reviews) | No | BR-201→207 |
| GET | `/api/districts` | Danh sách quận có dữ liệu (cho dropdown) | No | BR-303 |
| GET | `/api/categories` | Danh sách danh mục + count | No | BR-103, BR-302 |
| POST | `/api/plans` | Tạo kế hoạch mới | No (ownerKey) | BR-401 |
| GET | `/api/plans/:id` | Lấy chi tiết kế hoạch | No (ownerKey) | BR-406, BR-407 |
| PATCH | `/api/plans/:id` | Cập nhật tên/ngày/thứ tự/giờ item | No (ownerKey) | BR-403, BR-404, BR-405 |
| POST | `/api/plans/:id/items` | Thêm địa điểm vào kế hoạch | No (ownerKey) | BR-402 |
| DELETE | `/api/plans/:id/items/:itemId` | Xoá địa điểm khỏi kế hoạch | No (ownerKey) | BR-402 |
| POST | `/api/v1/places/import/open-data` | Import Overture + OSM từ file đã tải (chỉ profile `local`) | server | BR-501, BR-504 |
| POST | `/api/v1/places/import/overture` | Refresh riêng Overture | server | BR-501, BR-504 |
| POST | `/api/v1/places/import/osm` | Refresh riêng OSM/giờ mở cửa | server | BR-501, BR-504 |
| — (job) | `scripts/sync-open-place-data.ps1` | Tải hai nguồn và tùy chọn gọi import | server | BR-501, BR-504 |

> Ghi chú: plan dùng `ownerKey` (id ẩn danh sinh ở client, lưu localStorage) để gắn plan với trình duyệt mà không cần account — phù hợp scope "account out of scope" của BRD. Phần lưu DB của plan là tuỳ chọn; nếu chỉ localStorage thì các endpoint `/api/plans*` có thể không cần ở v3 tối giản.

---

## 7. Screens

| Màn hình | Mô tả | US liên quan |
|---|---|---|
| Map Home (`/`) | Bản đồ Hà Nội + pin + thanh tìm kiếm + bộ lọc + (mobile) bottom sheet | US-101.1, US-102.1, US-103.1, US-104.1, US-301.1, US-302.1, US-303.1, US-304.1, US-305.1, US-306.1 |
| Place Detail (panel/sheet) | Panel chi tiết: tên, địa chỉ, giờ, giá, rating, ảnh, review, link Google, nút "Thêm vào kế hoạch" | US-201.1→US-207.1, US-402.1 |
| Plan Panel / Page (`/plans/:id`) | Danh sách item, gán giờ, kéo-thả sắp xếp, tổng ngân sách, lưu | US-401.1, US-402.1, US-403.1, US-404.1, US-405.1, US-406.1 |
| Plan Timeline | Trục giờ trong ngày, block theo item, khu "chưa xếp giờ" | US-407.1 |
| My Plans (`/plans`) | Danh sách kế hoạch đã lưu của trình duyệt | US-406.1 |
| (Admin/CLI) ETL & Sync | Không có UI — chạy script + đọc SyncLog | US-501.1, US-502.1, US-503.1, US-504.1 |

---

## 8. Traceability Matrix

> Độ phủ: **27/27 BR đều có ≥ 1 User Story** — không BR mồ côi, không story thiếu nguồn.

| BR | User Story | Screen | Table | Test hook |
|---|---|---|---|---|
| BR-101 | US-101.1 | Map Home | — | TC-101.1 |
| BR-102 | US-102.1 | Map Home | Place | TC-102.1 |
| BR-103 | US-103.1 | Map Home | Place (category) | TC-103.1 |
| BR-104 | US-104.1 | Map Home | Place | TC-104.1 |
| BR-201 | US-201.1 | Place Detail | Place | TC-201.1 |
| BR-202 | US-202.1 | Place Detail | OpeningHour | TC-202.1 |
| BR-203 | US-203.1 | Place Detail | Place (price) | TC-203.1 |
| BR-204 | US-204.1 | Place Detail | Photo | TC-204.1 |
| BR-205 | US-205.1 | Place Detail | Place (rating) | TC-205.1 |
| BR-206 | US-206.1 | Place Detail | Review | TC-206.1 |
| BR-207 | US-207.1 | Place Detail | Place (googlePlaceId) | TC-207.1 |
| BR-301 | US-301.1 | Map Home | Place | TC-301.1 |
| BR-302 | US-302.1 | Map Home | Place (category) | TC-302.1 |
| BR-303 | US-303.1 | Map Home | Place (district) | TC-303.1 |
| BR-304 | US-304.1 | Map Home | Place (price) | TC-304.1 |
| BR-305 | US-305.1 | Map Home | OpeningHour | TC-305.1 |
| BR-306 | US-306.1 | Map Home | Place + OpeningHour | TC-306.1 |
| BR-401 | US-401.1 | Plan Panel | Plan | TC-401.1 |
| BR-402 | US-402.1 | Plan Panel / Place Detail | PlanItem | TC-402.1 |
| BR-403 | US-403.1 | Plan Panel | PlanItem | TC-403.1 |
| BR-404 | US-404.1 | Plan Panel | PlanItem (orderIndex) | TC-404.1 |
| BR-405 | US-405.1 | Plan Panel | PlanItem + Place (price) | TC-405.1 |
| BR-406 | US-406.1 | My Plans / Plan Panel | Plan | TC-406.1 |
| BR-407 | US-407.1 | Plan Timeline | PlanItem | TC-407.1 |
| BR-501 | US-501.1 | (ETL CLI) | Place | TC-501.1 |
| BR-502 | US-502.1 | (ETL CLI) | Place, Review, Photo | TC-502.1 |
| BR-503 | US-503.1 | Map Home / Detail (banner) | SyncLog | TC-503.1 |
| BR-504 | US-504.1 | (Cron) + Detail timestamp | SyncLog, Place (lastSyncedAt) | TC-504.1 |

---

> Bước tiếp theo trong pipeline: `/write-hld` (vẽ kiến trúc + data pipeline ETL Overpass→Enrich→DB + sequence flow) → `/deep-research` (đào sâu: quota Google Places thực tế, độ phủ OSM tại Hà Nội, chuẩn `opening_hours`) → **QC spec thủ công** → implement.
> Lưu ý: `/review` và `/code-review` chỉ chạy sau khi đã có code/PR, không dùng để QC tài liệu này.
