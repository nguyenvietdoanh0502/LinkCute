# AGENTS.md — Hướng dẫn làm việc với LinkCute

> Phạm vi: toàn bộ dự án, gồm `frontend/` và `backend/`.
> Cập nhật gần nhất: 2026-09-05.
> Mục tiêu: đọc file này trước, sau đó chỉ đọc code và test liên quan đến nhiệm vụ; không khảo sát lại toàn bộ dự án mỗi phiên.

## 1. Quy định bắt buộc về cập nhật tài liệu

**Mỗi khi agent thêm, sửa, xóa hoặc đổi tên bất kỳ file nào trong dự án, agent PHẢI cập nhật chính `AGENTS.md` này trước khi kết thúc công việc.** Áp dụng cho code, giao diện, test, cấu hình, dependency, migration, script và tài liệu; kể cả sửa nhỏ.

1. Cập nhật nội dung tương ứng bên dưới nếu thay đổi ảnh hưởng đến cấu trúc, hành vi, hợp đồng API, dữ liệu, cấu hình, lệnh chạy hoặc cách kiểm thử.
2. Luôn thêm một mục vào **Nhật ký thay đổi của agent** ở cuối file: ngày, việc đã làm, file/phạm vi thay đổi, kết quả kiểm tra và việc còn lại.
3. Nếu thay đổi không ảnh hưởng bản đồ hoặc quy ước, vẫn ghi nhật ký và nêu rõ điều đó. Không chỉ ghi chung chung “đã cập nhật code”.
4. Cập nhật ngày ở đầu file. Việc sửa tài liệu phải nằm cùng đợt bàn giao/commit/PR với thay đổi liên quan nếu có commit/PR.
5. Một mục nhật ký có thể tổng hợp nhiều lần chỉnh sửa trong cùng một nhiệm vụ; không cần ghi từng thao tác lưu file. Sửa nhật ký không tạo vòng lặp yêu cầu ghi thêm nhật ký cho chính nó.
6. Phiên chỉ đọc, giải thích hoặc review mà không sửa file thì không bắt buộc ghi nhật ký. Nếu công việc dở dang nhưng đã sửa file, vẫn ghi trạng thái dở dang và điểm tiếp tục.

**Chưa cập nhật `AGENTS.md` thì chưa được coi là hoàn thành nhiệm vụ.** Không thay việc này bằng một lời nhắc trong câu trả lời cuối. Không tự bỏ hoặc làm yếu quy định này nếu người dùng chưa yêu cầu.

## 2. Quy trình bắt đầu và kết thúc mỗi nhiệm vụ

1. Đọc file này, đặc biệt bảng tra cứu và nhật ký gần nhất. Kiểm tra hướng dẫn `AGENTS.md` trong thư mục con nếu có trước khi sửa phạm vi đó; hướng dẫn con bổ sung quy tắc chuyên biệt. Tuân theo chỉ dẫn ưu tiên cao hơn của môi trường và yêu cầu trực tiếp của người dùng.
2. Xem `git status --short` và diff liên quan để nhận biết thay đổi đang có. Không ghi đè, revert hay nhận là của mình những thay đổi do người dùng/agent khác thực hiện.
3. Chọn tính năng ở bảng mục 4, đọc file đích, nơi gọi trực tiếp và test tương ứng. Dùng `rg` tìm symbol khi thiếu ngữ cảnh; chỉ mở rộng phạm vi khi có phụ thuộc hoặc bằng chứng cần thiết.
4. Sửa trong phạm vi yêu cầu, theo cấu trúc hiện có. Không tự nâng cấp stack hoặc refactor diện rộng kèm một sửa lỗi nhỏ.
5. Chạy kiểm tra phù hợp ở mục 7; xem lại diff, cập nhật mục liên quan và nhật ký trong file này.
6. Bàn giao bằng tiếng Việt: thay đổi chính, kiểm tra đã chạy/kết quả, hạn chế còn lại và xác nhận đã cập nhật `AGENTS.md`.

Không mặc định đọc hàng loạt `node_modules/`, `dist/`, `target/`, `.git/`, `backend/data/` hoặc kho skill/cấu hình công cụ trong thư mục ẩn. Chỉ mở khi nhiệm vụ cần. Với PowerShell, đọc/ghi tài liệu tiếng Việt bằng UTF-8 để tránh lỗi dấu.

Tài liệu này là bản đồ, không thay thế việc đọc đoạn code sắp sửa. Nếu phát hiện mô tả cũ, kiểm chứng bằng code, cấu hình và test hiện tại rồi sửa lại tài liệu; không sửa code để khớp một mô tả đã lỗi thời.

## 3. Tổng quan và cấu trúc dự án

LinkCute là ứng dụng khám phá địa điểm quanh Hà Nội, lập lịch trình, chia sẻ kế hoạch với bạn bè, nhắn tin riêng, chia sẻ vị trí và gọi thoại. Tên cũ `roamly` còn xuất hiện trong cấu hình/database; Java vẫn dùng package `com.hadilao.be`. Không đổi hàng loạt các tên này nếu không thuộc yêu cầu.

| Phần | Công nghệ và vai trò |
| --- | --- |
| `frontend/` | React 19, Vite 8, JavaScript ESM/JSX, CSS thuần; MapLibre GL, Amazon Location Maps, Lucide, STOMP và WebRTC. Phiên bản chính xác nằm trong `package.json` và `package-lock.json`. |
| `backend/` | Java 17, Spring Boot 3.5.15, Maven Wrapper; Spring MVC, Security/JWT, JPA, Redis, Mail, WebSocket/STOMP, Flyway, PostgreSQL và Cloudinary. Xem `pom.xml` khi sửa dependency. |
| `frontend/test/` | Node test runner (`node --test`); một số test tải module qua Vite và dùng `react-test-renderer`. |
| `backend/src/test/` | JUnit/Spring Boot Test/Spring Security Test, H2; cấu hình test riêng trong `resources/`. |
| `backend/src/main/resources/db/migration/` | Schema Flyway; hiện có V1–V11. |
| `backend/scripts/` | Script khởi tạo/seed/schema và đồng bộ dữ liệu địa điểm; chỉ đọc/chạy khi nhiệm vụ liên quan. |
| `backend/plan/` | BRD, PRD và mô tả các luồng auth; tài liệu tham khảo nghiệp vụ, cần đối chiếu code. |
| `backend/AWS_DEPLOYMENT_RUNBOOK.md` | Hướng dẫn vận hành/deploy; đọc khi làm hạ tầng. |
| `frontend/README.md` | Hướng dẫn frontend, môi trường, bản đồ và các tính năng; lưu ý mô tả token cũ ở mục 8 bên dưới. |

Entrypoint frontend: `frontend/index.html` → `src/main.jsx` → `src/App.jsx`. `main.jsx` bật React StrictMode và import CSS toàn cục/MapLibre. `App.jsx` điều phối session, tìm kiếm, bản đồ và các panel; chưa dùng thư viện router trong dependency hiện tại.

Entrypoint backend: `backend/src/main/java/com/hadilao/be/HadilaoBackendApplication.java`. Bên dưới package này, `core/` chứa hạ tầng dùng chung; `modules/` chia theo nghiệp vụ với `controller`, `service`, `repository`, `entity`, `dto`, `enums` và `util` tùy module.

## 4. Bảng tra cứu: sửa tính năng nào thì đọc ở đâu

Đường dẫn frontend bên dưới tương đối với `frontend/src/`; đường dẫn backend `modules/` và `core/` tương đối với `backend/src/main/java/com/hadilao/be/`.

| Nhiệm vụ | Điểm đọc frontend | Điểm đọc backend / kiểm tra liên quan |
| --- | --- | --- |
| Trang khám phá, tìm kiếm, bộ lọc | `App.jsx`, `components/DiscoverSection.jsx`, `Hero.jsx`, `CategoryStrip.jsx`, `FilterBar.jsx`, `PlaceGrid.jsx`, `PlaceCard.jsx` trong `components/` | `modules/place/`; test `PlaceControllerTest`, `PlaceServiceTest`, `PlaceRepositoryTest` |
| Chi tiết địa điểm, bản đồ | `components/PlaceDetailModal.jsx`, `components/AwsPlacesMap.jsx`, `api/client.js` | `modules/place/`, `PlaceMapDTO`, `PlaceDetailDTO`; API danh sách và bản đồ khác nhau |
| Sáng/tối, bố cục, CSS | `hooks/useTheme.js`, `theme/theme.js`, `styles.css`, `components/SiteHeader.jsx`, `components/AppFooter.jsx`, `App.jsx`, `../index.html` | Không có backend riêng; kiểm tra light/dark, desktop/mobile và build |
| Đăng nhập, OTP, mật khẩu, session | `components/AuthModal.jsx`, `api/client.js`, `App.jsx` | `modules/auth/`, `modules/user/`, `core/security/`, `core/config/SecurityConfig.java`; frontend `auth-cookie.test.mjs`, backend test auth/security |
| Hồ sơ, avatar | `profile/model.js`, `components/ProfileAvatar.jsx`, màn hình tài khoản trong `App.jsx`, `api/client.js` | `modules/user/`, `core/config/CloudinaryConfig.java`; frontend `profile-*`, backend test user |
| Lịch trình cục bộ | `itinerary/model.js`, `hooks/useItineraryPlans.js`, `components/ItineraryPanel.jsx` | Chưa chia sẻ thì lưu ở trình duyệt; frontend `itinerary-model.test.js` |
| Chia sẻ lịch trình, lời mời, thành viên | `itinerary/sharing.js`, `hooks/usePlanSharing.js`, `components/PlanPeopleView.jsx`, `components/ItineraryPanel.jsx` | `modules/plan/`; frontend `plan-sharing-*`, backend test plan |
| Bạn bè | `friends/model.js`, `hooks/useFriendships.js`, `components/FriendsPanel.jsx` | `modules/friendship/`; frontend `friends-model.test.js`, backend test friendship |
| Chat riêng | `chat/model.js`, `hooks/useChat.js`, `realtime/chatSocket.js`, `components/ChatPanel.jsx` | `modules/chat/`, `core/config/WebSocketConfig.java`, `core/security/JwtStompChannelInterceptor.java`; frontend `chat-*`, backend test chat/security |
| Vị trí hiện tại, gửi vị trí | `location/model.js`, `hooks/useCurrentLocation.js`, `components/ShareLocationDialog.jsx`, `components/AwsPlacesMap.jsx`, `components/ChatPanel.jsx` | `modules/chat/`, migration V11; frontend `location-*` và test chat |
| Gọi thoại | `call/model.js`, `hooks/useCall.js`, `realtime/callSocket.js`, `components/CallOverlay.jsx` | `modules/call/`, interceptor STOMP; frontend `call-*`, backend test call/security |
| API chung, lỗi, phân quyền | `api/client.js` và nơi gọi API bị thay đổi | `core/constant/UrlConstant.java`, `core/common/ApiResponse.java`, `PageResponse.java` trong `core/common/`, `core/exception/`, `core/config/SecurityConfig.java` |
| Import dữ liệu địa điểm | UI chỉ liên quan nếu thay dữ liệu trả về | `modules/place/service/FullDataPlaceImporter.java`, `OpenPlaceDataImporter.java` cùng thư mục, `modules/place/util/`, `backend/scripts/`; test importer/parser/migration |
| Database, môi trường, deploy | `frontend/.env.example`, `frontend/vite.config.js` khi liên quan | `backend/src/main/resources/application*.yml`, `.env.example`, `.env.production.example`, `docker-compose*.yml`, `Dockerfile`, runbook trong `backend/` |

Frontend test nằm trong `frontend/test/`; backend test phản chiếu package tại `backend/src/test/java/com/hadilao/be/`. Ký hiệu `profile-*`, `chat-*`… là nhóm tên file, không phải lệnh shell.

## 5. Các hợp đồng và hành vi cần giữ

### API và xác thực

- REST dùng tiền tố `/api/v1`. Route tập trung tại `UrlConstant.java`; controller xác định HTTP method và quyền truy cập.
- Backend trả `ApiResponse<T>` với `code`, `status`, `message`, `data`, `errorCode`, `timestamp` (trường null có thể bị bỏ). Dùng `ApiError` và cơ chế request hiện có ở frontend; tránh tạo client/session song song trong component.
- Session frontend dùng khóa `linkcute.demo.session`: `user`, `accessToken`, `expiresAt`. **Refresh token nằm trong cookie HttpOnly/Secure `__Host-linkcute_refresh`, không lưu vào localStorage.** Client loại bỏ trường refresh token cũ khi đọc session.
- Luồng cookie auth gửi `credentials: 'include'` và header `X-Requested-With: XMLHttpRequest`. Giữ đồng bộ với `AuthRequestSecurityPolicy`, `RefreshTokenCookieService` và CORS backend.
- Client gộp refresh đồng thời bằng `refreshPromise`; request có auth nhận 401 chỉ thử refresh/retry một lần. Không tạo vòng lặp retry vô hạn.
- Phân quyền phải thực thi ở backend, gồm trạng thái tài khoản/session, quan hệ bạn bè, chủ sở hữu và thành viên kế hoạch; ẩn nút ở UI không thay thế kiểm tra quyền.

### Realtime, dữ liệu cục bộ và thiết bị

- WebSocket endpoint `/ws`; access token trong STOMP `CONNECT`, không đưa lên URL. Chat gửi `/app/chat.send`, nhận `/user/queue/messages` và `/user/queue/chat-errors`. Call gửi `/app/call.signal`, nhận `/user/queue/call-signals` và `/user/queue/call-errors`.
- Chat đối soát tin nhắn lạc quan bằng `clientMessageId`. Khi sửa chat, giữ xử lý tin trùng, reconnect, lịch sử và kiểm tra quan hệ bạn bè.
- Cuộc gọi hiện là thoại WebRTC (`audio: true`, `video: false`); backend xử lý signaling. Cấu hình ICE tại `VITE_WEBRTC_ICE_SERVERS`.
- Lịch trình cục bộ dùng `linkcute.itineraries.v1`. Kế hoạch chưa chia sẻ dùng được khi chưa đăng nhập; khi mời bạn lần đầu mới xuất bản lên backend rồi đồng bộ thay đổi của chủ kế hoạch.
- Dữ liệu kế hoạch đã xuất bản gắn `serverOwnerId`; không để lọt dữ liệu giữa các tài khoản dùng chung trình duyệt. Kế hoạch được mời chỉ đọc và giữ trong bộ nhớ phiên của người nhận, không nhập vào kho lịch trình cục bộ của họ.
- Geolocation chỉ yêu cầu sau thao tác người dùng. Tin nhắn vị trí là tọa độ tại thời điểm gửi, không theo dõi trực tiếp và không lưu tọa độ vào localStorage.
- Avatar đi qua backend bằng multipart rồi lưu Cloudinary; không đưa khóa Cloudinary xuống frontend. UI hỗ trợ JPEG/PNG/WebP tối đa 5 MiB; cấu hình multipart backend nằm trong `application.yml`.
- Theme dùng khóa `linkcute.theme` với `light`, `dark`, `system`; `theme/theme.js` cập nhật `data-theme`, `colorScheme` và meta `theme-color`. Đọc cả model và hook khi sửa theme; giữ cleanup listener khi đổi preference/unmount.
- Effect, socket, timer, media stream và request bất đồng bộ phải có cleanup phù hợp khi đóng panel, unmount, logout hoặc đổi tài khoản; lưu ý StrictMode.

### Quy ước triển khai

- Frontend dùng component/hook hiện có; logic thuần đặt ở model theo tính năng, API ở `api/client.js`, realtime ở `realtime/`. Giữ phong cách ESM, single quote, không semicolon như code xung quanh.
- Tái sử dụng biến CSS và mẫu UI trong `styles.css`; giữ tiếng Việt đúng dấu, responsive, label truy cập, focus và thao tác bàn phím cho modal/drawer.
- Backend giữ phân lớp controller → service → repository, DTO cho request/response và xử lý lỗi chung. Đặt validation, transaction và kiểm tra quyền theo mẫu module hiện có.
- Schema mới phải có migration Flyway mới theo số tiếp theo chưa dùng; kiểm tra lại thư mục migration trước khi chọn số. Không sửa migration đã áp dụng để thay đổi schema và không dựa vào `ddl-auto` tự cập nhật production.
- Khi thêm dependency, cập nhật manifest/lockfile tương ứng, nêu lý do trong nhật ký. Không sửa trực tiếp file sinh ra trong `dist/`, `target/` hay `node_modules/`.
- Chỉ dùng `.env.example`/cấu hình mẫu để tìm tên biến; không ghi token, mật khẩu hay nội dung `.env` thật vào tài liệu, log hoặc commit. Mọi biến `VITE_*` đều có thể lộ cho trình duyệt.

## 6. Chạy dự án và cấu hình

Các lệnh sau dùng PowerShell, bắt đầu từ thư mục gốc dự án; chạy frontend/backend ở terminal riêng. Frontend README yêu cầu Node.js 20.19+ hoặc 22.12+; backend yêu cầu JDK 17. Dùng npm và Maven Wrapper có sẵn.

Frontend:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Vite mặc định cổng `5173`. Khi `VITE_API_BASE_URL` trống, `/api` và `/ws` proxy tới **`https://linkcute.duckdns.org`** theo `frontend/vite.config.js`. Chạy frontend local vì vậy vẫn có thể thao tác dữ liệu backend đang deploy. Khi cần kiểm thử ghi dữ liệu trên backend local, trỏ cả REST và WebSocket về môi trường local trước; không mặc định cho rằng Vite đang gọi `localhost:8080`.

Biến frontend xem tại `frontend/.env.example`: `VITE_API_BASE_URL`, `VITE_AWS_LOCATION_API_KEY`, `VITE_AWS_LOCATION_REGION`, `VITE_AWS_MAP_STYLE`, `VITE_AWS_MAP_COLOR_SCHEME`, `VITE_WEBRTC_ICE_SERVERS`. Bản đồ dùng public key giới hạn quyền đọc; không đưa IAM secret vào frontend.

Backend (terminal khác, từ gốc dự án):

```powershell
Set-Location backend
docker compose up -d postgres redis
.\mvnw.cmd spring-boot:run
```

Chuẩn bị `backend/.env` từ mẫu khi chưa có, không ghi đè cấu hình đang tồn tại. Cần cấu hình DB/Redis, JWT và Mail; avatar cần Cloudinary. Profile mặc định là `local`, backend local cổng `8080`; PostgreSQL local `5433` và Redis `6380` khớp Docker Compose. Profile `prod` và biến production xem file mẫu/runbook trước khi sửa hoặc triển khai.

## 7. Kiểm tra phù hợp với thay đổi

Frontend, chạy trong `frontend/` (một số test phụ thuộc thư mục làm việc này):

```powershell
npm test
npm run build
# Ví dụ chạy riêng test liên quan:
node --test test/auth-cookie.test.mjs
```

Backend, chạy trong `backend/`:

```powershell
.\mvnw.cmd test
# Ví dụ chạy riêng test liên quan:
.\mvnw.cmd '-Dtest=AuthControllerTest,AuthServiceTest' test
```

Trên shell Unix dùng `./mvnw` thay `.\mvnw.cmd`. Backend test mặc định dùng profile `test`, H2 và tắt Flyway tự chạy; một số migration test thực thi SQL trực tiếp. Test H2 không xác nhận đầy đủ hành vi PostgreSQL production.

- Thay logic: chạy test liên quan; thêm/cập nhật test hành vi khi sửa bug hoặc thêm nhánh xử lý cần bảo vệ. Thay API/auth/schema/realtime: kiểm tra cả hai đầu và trường hợp bị từ chối/trùng/reconnect nếu liên quan.
- Thay frontend: chạy build; với thay đổi giao diện, kiểm tra trực quan desktop/mobile và light/dark nếu có môi trường trình duyệt. Test đọc source/wiring không thay thế kiểm tra tương tác thực tế.
- Thay dùng chung hoặc ảnh hưởng nhiều module: chạy suite của phần bị ảnh hưởng. `frontend/package.json` hiện không có script lint; không báo đã chạy lint nếu chưa có lệnh thật.
- Chỉ sửa tài liệu: kiểm tra đường dẫn, lệnh, tính nhất quán và diff; không cần chạy toàn bộ test ứng dụng.
- Nếu không chạy được vì thiếu môi trường/dependency/dịch vụ, ghi đúng lệnh, lý do và phần chưa xác minh. Phân biệt “pass”, “fail” và “chưa chạy”; không suy diễn test đã pass chỉ từ việc đọc code.

## 8. Lưu ý đã xác minh khi lập bản đồ

- `frontend/README.md` còn nói lưu refresh token trong localStorage ở phần bảo mật và nhắc giữ access/refresh token khi sửa hồ sơ. Code hiện tại dùng refresh cookie như mục 5; không lấy các câu này làm hợp đồng hiện hành. Phiên lập tài liệu chưa sửa README.
- `backend/AGENTS.MD` hiện là file rỗng, tên có đuôi viết hoa. File gốc này là hướng dẫn chung; nếu bổ sung hướng dẫn backend sau này, giữ liên kết về đây và chú ý tên `AGENTS.md` chuẩn trên hệ thống phân biệt hoa/thường.
- `LocalPlaceDataController` chỉ có ở profile `local` và hiện mang `@PreAuthorize("denyAll()")`; không giả định endpoint import gọi được hoặc tự gỡ chặn để chạy thử.
- Snapshot này có các thay đổi UI/theme trong working tree chưa commit. Bản đồ phản ánh file đang có trên đĩa; mỗi phiên phải xem Git lại, không xem trạng thái ghi ở đây là trạng thái Git hiện tại.

## 9. Nhật ký thay đổi của agent

Ghi mục mới nhất lên đầu, nội dung ngắn và đủ để agent sau tiếp tục mà không đọc lại toàn dự án. Bản đồ phía trên luôn phải phản ánh hiện trạng; nhật ký không thay thế việc sửa mô tả đã cũ. Khi nhật ký quá dài, có thể gom các mục cũ thành bản tóm tắt ngay trong phần này, giữ quyết định quan trọng, mốc thay đổi và việc còn mở.

Mẫu bắt buộc:

```text
### YYYY-MM-DD — Tên nhiệm vụ
- Thay đổi: hành vi trước/sau hoặc nội dung được thêm/sửa/xóa.
- Phạm vi: đường dẫn file hoặc nhóm file cụ thể.
- Tài liệu: các mục đã đồng bộ, hoặc lý do không cần đổi bản đồ/quy ước.
- Kiểm tra: lệnh/kiểm tra đã thực hiện và kết quả; phần chưa chạy kèm lý do.
- Còn lại: không có, hoặc vấn đề chưa giải quyết và điểm tiếp tục.
```

### 2026-09-05 — Tạo hướng dẫn agent cho toàn dự án

- Thay đổi: thêm bản đồ frontend/backend, bảng tra cứu tính năng, hợp đồng quan trọng, lệnh chạy/test và quy định bắt buộc cập nhật tài liệu sau mỗi nhiệm vụ có sửa file.
- Phạm vi: chỉ thêm `AGENTS.md` ở gốc dự án; các thay đổi UI/theme có sẵn không thuộc nhiệm vụ này.
- Tài liệu: tạo các mục 1–9, ghi nhận mô tả refresh token lỗi thời trong README và tình trạng file hướng dẫn backend rỗng.
- Kiểm tra: đối chiếu bản đồ/lệnh với file nguồn, manifest, cấu hình và test hiện có; kiểm tra đường dẫn và whitespace của tài liệu. Không chạy test/build ứng dụng vì chỉ thêm Markdown.
- Còn lại: README cần đồng bộ mô tả refresh cookie khi thực hiện nhiệm vụ tài liệu/auth liên quan.
