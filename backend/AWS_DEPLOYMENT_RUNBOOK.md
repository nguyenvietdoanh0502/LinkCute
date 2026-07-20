# Runbook deploy Roamly Backend lên AWS EC2

Tài liệu này ghi lại toàn bộ quy trình đã dùng để deploy backend Spring Boot của dự án LinkCute/Roamly lên AWS EC2, chuyển dữ liệu PostgreSQL từ máy local lên production và vận hành hệ thống sau khi deploy.

> Trạng thái tại thời điểm viết tài liệu
>
> - Production API: `https://linkcute.duckdns.org`
> - EC2: Ubuntu 24.04 LTS, `t3.small`, region Singapore (`ap-southeast-1`)
> - Java: 17
> - Spring Boot: 3.5.15
> - PostgreSQL production: 18
> - Redis production: 7
> - Reverse proxy: Nginx
> - TLS: Let's Encrypt/Certbot
> - Database migrations: Flyway V1 đến V6
> - Tổng số địa điểm trong database: 79.896
> - Số địa điểm API hiển thị (`is_deleted = false`): 65.342

Không ghi password, JWT secret, DuckDNS token hoặc nội dung file `.env` vào tài liệu, GitHub, issue hay chat.

---

## 1. Kiến trúc hệ thống

```text
Người dùng / Frontend
        |
        | HTTPS 443
        v
linkcute.duckdns.org
        |
        v
Elastic IP của EC2
        |
        v
Nginx trên Ubuntu
        |
        | http://127.0.0.1:8080
        v
Spring Boot container (app)
        |
        +---- postgres:5432 (Docker network nội bộ)
        |
        +---- redis:6379 (Docker network nội bộ)
```

Luồng một request:

1. Client gọi `https://linkcute.duckdns.org/api/...`.
2. DuckDNS phân giải domain thành Elastic IP.
3. AWS Security Group cho phép traffic 443 đi vào EC2.
4. Nginx kết thúc kết nối TLS và chuyển request về `127.0.0.1:8080`.
5. Docker map `127.0.0.1:8080` của EC2 vào port 8080 của container `app`.
6. Spring Boot xử lý request và truy cập PostgreSQL/Redis qua Docker network.
7. Response quay ngược lại qua Nginx và HTTPS.

PostgreSQL và Redis không khai báo `ports` trong Compose production, vì vậy không được public ra host hoặc Internet. Spring Boot chỉ bind vào loopback `127.0.0.1`, vì vậy Internet cũng không truy cập trực tiếp được port 8080.

---

## 2. Phân biệt nơi chạy câu lệnh

Đây là nguyên tắc quan trọng nhất khi làm theo runbook.

### 2.1 Windows local – PowerShell

Prompt thường có dạng:

```text
PS E:\LinkCute>
```

Dùng cho:

- Code và chạy test.
- Git commit/push.
- `pg_dump` database local.
- `scp` file lên EC2.
- SSH vào EC2.

### 2.2 Windows local – Git Bash

Prompt thường có dạng:

```text
ADMIN@VietDoanhDZ MINGW64 /e/LinkCute (dev)
```

Đây vẫn là máy local, không phải EC2.

### 2.3 EC2 – Bash qua SSH

Prompt thường có dạng:

```text
ubuntu@ip-172-31-45-181:~$
```

Dùng cho:

- Docker Compose production.
- Nginx/Certbot.
- File `.env` production.
- Backup/restore PostgreSQL production.
- Theo dõi log và tài nguyên server.

Luôn chạy hai lệnh sau nếu không chắc đang ở đâu:

```bash
whoami
pwd
```

Trên EC2, `whoami` phải trả về `ubuntu`.

---

## 3. Các file production trong repository

### `Dockerfile`

Dockerfile dùng multi-stage build:

1. Stage `build` dùng Maven + JDK 17 để tải dependency và build JAR.
2. Stage `runtime` chỉ dùng JRE 17 để chạy JAR.
3. Ứng dụng chạy bằng user `spring`, không chạy bằng root.

Lợi ích:

- Image runtime nhỏ hơn.
- Không mang Maven cache và source thừa vào runtime.
- Giảm bề mặt tấn công.
- Docker cache dependency khi `pom.xml` không đổi.

### `.dockerignore`

Loại khỏi Docker build context các thư mục/file như `.git`, IDE config, `target`, `data`, log và `.env`.

Tác dụng:

- Build nhanh hơn.
- Tránh đưa secret hoặc dữ liệu lớn vào image.

### `docker-compose.prod.yml`

Quản lý ba service:

- `postgres`: PostgreSQL 18, named volume, health check.
- `redis`: Redis 7, password, AOF persistence, health check.
- `app`: Spring Boot, profile `prod`, giới hạn JVM heap 512 MB.

Named volumes:

```text
roamly_postgres_data
roamly_redis_data
```

Container có thể được xóa/tạo lại mà volume vẫn giữ dữ liệu. Lệnh `down -v` sẽ xóa volume và có thể làm mất dữ liệu.

### `.env.production.example`

Là template biến môi trường, không chứa secret thật. Trên EC2 copy thành `.env` rồi điền giá trị thật.

### `application-prod.yml`

Cấu hình production gồm:

- Database và Redis lấy từ biến môi trường.
- Hikari pool nhỏ để tiết kiệm RAM.
- `ddl-auto: validate` để Hibernate chỉ kiểm tra schema.
- Flyway chịu trách nhiệm thay đổi schema.
- Swagger tắt mặc định.
- Không trả stack trace ra client.
- Graceful shutdown.
- Hỗ trợ reverse proxy headers.
- CORS lấy từ `APP_CORS_ALLOWED_ORIGINS`.

### `SecurityConfig.java`

CORS chỉ cho phép các frontend origin được khai báo rõ ràng. Với JWT/credentials, không sử dụng wildcard `*` cho production.

---

## 4. Tạo AWS account và bảo vệ chi phí

1. Tạo AWS account/Free Plan.
2. Bật MFA cho root account.
3. Vào Billing and Cost Management.
4. Tạo `Zero spend budget` và cảnh báo chi phí phù hợp.

AWS Free Tier hiện dùng credit và có giới hạn thời gian; không coi EC2 là miễn phí vĩnh viễn. Public IPv4/Elastic IP cũng có chi phí và trừ vào credit.

---

## 5. Tạo EC2

Cấu hình đã dùng:

```text
Region:        Asia Pacific (Singapore), ap-southeast-1
AMI:           Ubuntu Server 24.04 LTS x86_64
Instance type: t3.small (2 GB RAM)
Storage:       20 GiB gp3
Instance name: roamly-backend
```

Security Group:

| Type | Port | Source | Mục đích |
|---|---:|---|---|
| SSH | 22 | IP cá nhân `/32` | Quản trị server |
| HTTP | 80 | `0.0.0.0/0` | Redirect và Let's Encrypt HTTP-01 |
| HTTPS | 443 | `0.0.0.0/0` | API production |

Không mở public các port:

```text
8080
5432
6379
```

Nếu IP mạng nhà thay đổi, cập nhật source rule SSH bằng `My IP` trong Security Group.

---

## 6. SSH từ Windows

Private key đang được lưu local, ví dụ:

```text
E:\Deploy\roamly-key.pem
```

### 6.1 Sửa ACL private key trên Windows

Mở PowerShell Administrator:

```powershell
$keyPath = "E:\Deploy\roamly-key.pem"

takeown.exe /F $keyPath
icacls.exe $keyPath /inheritance:r
icacls.exe $keyPath /remove:g "*S-1-5-32-545"
icacls.exe $keyPath /remove:g "*S-1-5-11"
icacls.exe $keyPath /remove:g "*S-1-1-0"
icacls.exe $keyPath /grant:r "$($env:USERNAME):(R)"
```

Kiểm tra:

```powershell
icacls.exe $keyPath
```

Không được còn quyền đọc cho `BUILTIN\Users`, `Authenticated Users` hoặc `Everyone`.

### 6.2 Kết nối

Biến PowerShell mất khi đóng terminal, nên phải khai báo lại trong terminal mới:

```powershell
$keyPath = "E:\Deploy\roamly-key.pem"
ssh -i "$keyPath" ubuntu@ELASTIC_IP
```

Hoặc dùng domain sau khi DNS đã hoạt động:

```powershell
ssh -i "$keyPath" ubuntu@linkcute.duckdns.org
```

Nếu SSH hỏi xác nhận fingerprint lần đầu hoặc sau khi đổi sang Elastic IP, kiểm tra đúng IP rồi nhập `yes`.

Xóa entry IP cũ nếu cần:

```powershell
ssh-keygen -R OLD_PUBLIC_IP
```

Không bao giờ gửi file `.pem` cho người khác hoặc commit lên Git.

---

## 7. Cài Docker Engine và Docker Compose trên EC2

Chạy trên EC2:

```bash
sudo apt update
sudo apt install -y ca-certificates curl git
```

Thêm GPG key:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

Thêm Docker repository:

```bash
sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
```

Cài Docker:

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
sudo systemctl enable --now docker
```

Đăng xuất SSH và kết nối lại để group mới có hiệu lực.

Kiểm tra:

```bash
docker --version
docker compose version
docker run --rm hello-world
```

---

## 8. Git workflow trước lần deploy đầu

Repository:

```text
https://github.com/nguyenvietdoanh0502/LinkCute.git
```

Workflow đang dùng:

```text
feature/dev -> Pull Request -> main -> EC2 pull main
```

### 8.1 Local: test trước khi commit

```powershell
cd E:\LinkCute\backend
.\mvnw.cmd test
```

Hoặc nếu Maven có trong PATH:

```powershell
mvn test
```

### 8.2 Local: commit/push

```powershell
cd E:\LinkCute
git status --short
git add <danh-sach-file-can-commit>
git diff --cached --stat
git commit -m "noi dung commit"
git push origin dev
```

Không dùng `git add .` nếu workspace có file/thư mục cá nhân không liên quan.

Tạo Pull Request với:

```text
base: main <- compare: dev
```

Review file, merge vào `main`, không đưa `.env`, `.pem` hoặc dump database lên GitHub.

---

## 9. Clone repository lên EC2

```bash
cd ~
git clone --branch main --single-branch \
  https://github.com/nguyenvietdoanh0502/LinkCute.git
cd ~/LinkCute/backend
```

Kiểm tra:

```bash
git branch --show-current
git log -1 --oneline
ls -la Dockerfile docker-compose.prod.yml .env.production.example
```

Branch phải là `main`.

Nếu repository chuyển thành private, dùng GitHub deploy key hoặc cơ chế credential an toàn; không nhập password GitHub thông thường lên server.

---

## 10. Tạo `.env` production

Chạy trên EC2:

```bash
cd ~/LinkCute/backend
cp .env.production.example .env
chmod 600 .env
```

Sinh secret mà không in ra màn hình:

```bash
db_secret=$(openssl rand -hex 32)
redis_secret=$(openssl rand -hex 32)
jwt_secret=$(openssl rand -base64 64 | tr -d '\n')

sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$db_secret|" .env
sed -i "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=$redis_secret|" .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$jwt_secret|" .env

unset db_secret redis_secret jwt_secret
```

Sửa các biến còn lại:

```bash
nano .env
```

Các nhóm biến quan trọng:

```dotenv
DB_NAME=roamly
DB_USERNAME=roamly
DB_PASSWORD=<secret>

REDIS_PASSWORD=<secret>

MAIL_HOST=<smtp-host>
MAIL_PORT=587
MAIL_USERNAME=<smtp-user>
MAIL_PASSWORD=<smtp-app-password>

JWT_SECRET=<base64-secret>

APP_CORS_ALLOWED_ORIGINS=
SWAGGER_ENABLED=false
JAVA_TOOL_OPTIONS=-Xms128m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError
```

Nếu dùng Gmail, sử dụng Google App Password, không dùng password Gmail thông thường.

Lưu trong Nano:

```text
Ctrl+O, Enter, Ctrl+X
```

Kiểm tra quyền và Git ignore:

```bash
ls -l .env
git status --short
```

Quyền file phải bắt đầu bằng `-rw-------`; `.env` không được xuất hiện trong `git status`.

Không chạy `docker compose config` không tham số vì có thể in secret. Chỉ kiểm tra bằng:

```bash
docker compose -f docker-compose.prod.yml config --quiet
```

---

## 11. Chạy PostgreSQL và Redis

```bash
cd ~/LinkCute/backend
docker compose -f docker-compose.prod.yml pull postgres redis
docker compose -f docker-compose.prod.yml up -d postgres redis
docker compose -f docker-compose.prod.yml ps
```

Hai service phải chuyển sang `healthy`.

Xem log:

```bash
docker compose -f docker-compose.prod.yml logs --tail=100 postgres redis
```

PostgreSQL lần đầu sẽ khởi động tạm để tạo database, tự dừng rồi khởi động chính thức. Đây là hành vi bình thường.

### Sửa cảnh báo Redis memory overcommit

```bash
echo "vm.overcommit_memory=1" | sudo tee /etc/sysctl.d/99-redis.conf
sudo sysctl --system
sysctl vm.overcommit_memory
docker compose -f docker-compose.prod.yml restart redis
```

Kết quả cần có:

```text
vm.overcommit_memory = 1
```

File trong `/etc/sysctl.d` giúp cấu hình còn hiệu lực sau reboot.

---

## 12. Build và chạy Spring Boot

Build image:

```bash
cd ~/LinkCute/backend
docker compose -f docker-compose.prod.yml build app
```

Khởi động:

```bash
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 app
```

Các dấu hiệu startup thành công:

```text
The following 1 profile is active: "prod"
Successfully validated 6 migrations
Tomcat started on port 8080
Started HadilaoBackendApplication
```

Smoke test nội bộ:

```bash
curl -i "http://127.0.0.1:8080/api/v1/places?page=0&size=1"
```

Phải trả `HTTP/1.1 200`.

---

## 13. Flyway và incident thiếu cột `users.status`

Lần deploy đầu, ứng dụng dừng với lỗi:

```text
Schema-validation: missing column [status] in table [users]
```

Nguyên nhân:

- Entity `User` có field `status`.
- Migration V1 cũ chưa tạo cột này.
- Production dùng `ddl-auto: validate`, nên Hibernate từ chối khởi động thay vì tự sửa database.

Cách sửa đúng là tạo migration mới:

```text
V6__add_user_account_status.sql
```

Không sửa V1 đã chạy, vì Flyway lưu checksum migration và sẽ báo lỗi nếu file cũ bị thay đổi.

Nguyên tắc cho mọi thay đổi schema tương lai:

1. Không sửa V1–V6 đã chạy production.
2. Tạo V7, V8... theo thứ tự.
3. Migration phải xử lý dữ liệu cũ trước khi thêm `NOT NULL`.
4. Backup production trước migration lớn.
5. Test local rồi mới merge `main`.

Ví dụ:

```text
src/main/resources/db/migration/V7__add_favorite_places.sql
```

Kiểm tra Flyway production:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

---

## 14. Cài và cấu hình Nginx

Cài:

```bash
sudo apt update
sudo apt install -y nginx
```

Cấu hình HTTP ban đầu:

```bash
sudo tee /etc/nginx/sites-available/default > /dev/null <<'EOF'
server {
    listen 80 default_server;
    listen [::]:80 default_server;

    server_name _;
    server_tokens off;

    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 5s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
EOF
```

Kiểm tra và bật Nginx:

```bash
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl restart nginx
sudo systemctl status nginx --no-pager
```

Test:

```bash
curl -i "http://127.0.0.1/api/v1/places?page=0&size=1"
```

> Sau khi Certbot đã cấu hình HTTPS, không ghi đè toàn bộ file Nginx bằng block HTTP ban đầu vì có thể làm mất phần TLS do Certbot quản lý. Luôn backup và kiểm tra diff trước khi sửa Nginx production.

---

## 15. Elastic IP và DuckDNS

Public IP tự động của EC2 có thể thay đổi sau stop/start. Đã cấp Elastic IP và associate vào instance `roamly-backend`.

Sau khi terminate EC2, phải release Elastic IP nếu không dùng nữa; chỉ disassociate mà không release vẫn có thể phát sinh phí.

DuckDNS domain:

```text
linkcute.duckdns.org
```

Trong DuckDNS, record phải trỏ vào Elastic IP. Không chia sẻ DuckDNS token.

Kiểm tra DNS từ Windows:

```powershell
nslookup linkcute.duckdns.org
curl.exe -i "http://linkcute.duckdns.org/api/v1/places?page=0&size=1"
```

Vì dùng Elastic IP cố định, không cần chạy DuckDNS updater định kỳ.

---

## 16. HTTPS bằng Certbot

Đổi Nginx `server_name`:

```bash
sudo sed -i \
  's/server_name _;/server_name linkcute.duckdns.org;/' \
  /etc/nginx/sites-available/default

sudo nginx -t
sudo systemctl reload nginx
```

Cài Certbot:

```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
certbot --version
certbot plugins
```

Cấp chứng chỉ và bật redirect:

```bash
sudo certbot --nginx \
  -d linkcute.duckdns.org \
  --redirect
```

Kiểm tra:

```bash
curl -i "https://linkcute.duckdns.org/api/v1/places?page=0&size=1"
curl -I "http://linkcute.duckdns.org/api/v1/places?page=0&size=1"
sudo certbot certificates
```

HTTP phải redirect sang HTTPS; HTTPS phải trả 200.

Kiểm tra tự gia hạn:

```bash
systemctl status certbot.timer --no-pager
sudo systemctl enable --now certbot.timer
sudo certbot renew --dry-run
systemctl list-timers certbot.timer
```

Giữ port 80 mở để redirect và hỗ trợ HTTP-01 renewal.

---

## 17. Chuyển PostgreSQL local lên production

PostgreSQL local đang chạy native trên Windows:

```text
Service: postgresql-x64-18
Host: localhost
Port: 5433
Database: roamly
User: postgres
pg_dump: C:\Program Files\PostgreSQL\18\bin\pg_dump.exe
```

Redis local không được chuyển lên production. OTP, rate-limit và session cũ không nên mang sang môi trường mới.

### 17.1 Local: kiểm tra dữ liệu

```powershell
$pgBin = "C:\Program Files\PostgreSQL\18\bin"

& "$pgBin\psql.exe" `
  -h localhost `
  -p 5433 `
  -U postgres `
  -d roamly `
  -c "SELECT COUNT(*) AS places FROM places;"

& "$pgBin\psql.exe" `
  -h localhost `
  -p 5433 `
  -U postgres `
  -d roamly `
  -c "SELECT COUNT(*) AS users FROM users;"
```

### 17.2 Local: tạo custom dump

```powershell
$backupDir = "E:\Deploy\db-backups"
New-Item -ItemType Directory -Force $backupDir

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dumpPath = Join-Path $backupDir "roamly-$timestamp.dump"

& "$pgBin\pg_dump.exe" `
  -h localhost `
  -p 5433 `
  -U postgres `
  -d roamly `
  -Fc `
  --no-owner `
  --no-acl `
  -f $dumpPath

$LASTEXITCODE
Get-Item $dumpPath | Select-Object FullName, Length, LastWriteTime
```

`$LASTEXITCODE` phải là 0.

Kiểm tra archive:

```powershell
$entryCount = (
  & "$pgBin\pg_restore.exe" --list $dumpPath |
  Measure-Object -Line
).Lines

$entryCount
```

### 17.3 Local: upload và checksum

```powershell
$localHash = (Get-FileHash -Algorithm SHA256 $dumpPath).Hash.ToLower()
$localHash

scp `
  -i "E:\Deploy\roamly-key.pem" `
  "$dumpPath" `
  ubuntu@ELASTIC_IP:/home/ubuntu/roamly-production.dump
```

Trên EC2:

```bash
chmod 600 /home/ubuntu/roamly-production.dump
ls -lh /home/ubuntu/roamly-production.dump
sha256sum /home/ubuntu/roamly-production.dump
```

Hai SHA-256 phải giống nhau.

### 17.4 EC2: backup database hiện tại trước restore

```bash
cd ~/LinkCute/backend

backup_path="/home/ubuntu/roamly-before-restore-$(date +%Y%m%d-%H%M%S).dump"

docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' \
  > "$backup_path"

echo $?
chmod 600 "$backup_path"
ls -lh "$backup_path"
sha256sum "$backup_path"
```

Kiểm tra backup:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_restore --list < "$backup_path" | head -20
```

### 17.5 EC2: dừng app và restore

```bash
docker compose -f docker-compose.prod.yml stop app
docker compose -f docker-compose.prod.yml ps
```

Xóa/tạo lại database sau khi đã backup:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'dropdb --force -U "$POSTGRES_USER" "$POSTGRES_DB"'

docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
```

Restore trong một transaction:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --exit-on-error --single-transaction' \
  < /home/ubuntu/roamly-production.dump

echo $?
```

Exit code phải là 0.

Kiểm tra count:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT COUNT(*) AS places FROM places;"'

docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT COUNT(*) AS users FROM users;"'
```

Khởi động app và kiểm tra Flyway/API:

```bash
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml logs --since=3m app
docker compose -f docker-compose.prod.yml ps
curl -s "https://linkcute.duckdns.org/api/v1/places?page=0&size=1"
```

### 17.6 Vì sao database có 79.896 nhưng API trả 65.342?

API luôn thêm điều kiện:

```java
isDeleted = false
```

Kiểm tra:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
    SELECT
      COUNT(*) AS total,
      COUNT(*) FILTER (WHERE is_deleted = FALSE) AS visible,
      COUNT(*) FILTER (WHERE is_deleted = TRUE) AS soft_deleted
    FROM places;
  "'
```

Kết quả tại thời điểm migration:

```text
total:        79896
visible:      65342
soft_deleted: 14554
```

Đây là soft delete, không phải mất dữ liệu.

---

## 18. Quy trình deploy code mới

### 18.1 Local

```powershell
cd E:\LinkCute
git switch dev
git pull origin dev
```

Code và test:

```powershell
cd backend
.\mvnw.cmd test
cd ..
```

Commit/push:

```powershell
git status --short
git add <cac-file-can-commit>
git diff --cached
git commit -m "feat: mo ta thay doi"
git push origin dev
```

Tạo Pull Request `dev -> main`, review rồi merge.

### 18.2 EC2

Nếu thay đổi có migration database, backup trước khi deploy.

```bash
cd ~/LinkCute/backend
git status --short
git pull --ff-only origin main
git log -1 --oneline
docker compose -f docker-compose.prod.yml build app
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml logs --tail=200 app
```

Smoke test:

```bash
curl -s -o /dev/null \
  -w "HTTP %{http_code} - %{time_total}s\n" \
  "https://linkcute.duckdns.org/api/v1/places?page=0&size=1"
```

Phải trả HTTP 200.

`git pull --ff-only` tránh tạo merge commit bất ngờ trên server. EC2 chỉ chạy branch `main`; không code hoặc commit trực tiếp trên EC2.

---

## 19. Thay đổi biến môi trường production

Trên EC2:

```bash
cd ~/LinkCute/backend
nano .env
chmod 600 .env
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml logs --tail=100 app
```

Không thay `JWT_SECRET` tùy tiện. Thay JWT secret sẽ làm toàn bộ access/refresh token cũ mất hiệu lực.

Khi frontend được deploy, cập nhật:

```dotenv
APP_CORS_ALLOWED_ORIGINS=https://frontend-domain.example
```

Nhiều origin được phân cách bằng dấu phẩy:

```dotenv
APP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

Sau đó recreate app:

```bash
docker compose -f docker-compose.prod.yml up -d app
```

---

## 20. Lệnh vận hành thường dùng trên EC2

### Trạng thái

```bash
cd ~/LinkCute/backend
docker compose -f docker-compose.prod.yml ps
docker stats --no-stream
free -h
df -h /
```

### Logs

```bash
docker compose -f docker-compose.prod.yml logs --tail=200 app
docker compose -f docker-compose.prod.yml logs --tail=100 postgres
docker compose -f docker-compose.prod.yml logs --tail=100 redis
docker compose -f docker-compose.prod.yml logs -f app
```

Nhấn `Ctrl+C` để thoát chế độ follow log; container không bị dừng.

### Restart

```bash
docker compose -f docker-compose.prod.yml restart app
docker compose -f docker-compose.prod.yml restart redis
sudo systemctl restart nginx
```

### Stop/start có kiểm soát

```bash
docker compose -f docker-compose.prod.yml stop app
docker compose -f docker-compose.prod.yml start app
```

### Kiểm tra API

```bash
curl -i "http://127.0.0.1:8080/api/v1/places?page=0&size=1"
curl -i "https://linkcute.duckdns.org/api/v1/places?page=0&size=1"
```

### Nginx

```bash
sudo nginx -t
sudo systemctl status nginx --no-pager
sudo systemctl reload nginx
sudo journalctl -u nginx --since "30 minutes ago" --no-pager
```

### Certbot

```bash
sudo certbot certificates
systemctl status certbot.timer --no-pager
sudo certbot renew --dry-run
```

### Docker disk usage

```bash
docker system df
docker image ls
```

Có thể xóa image build không còn được container sử dụng:

```bash
docker image prune -f
```

Không dùng `docker system prune --volumes` trên production.

---

## 21. Backup PostgreSQL production thường xuyên

Backup thủ công:

```bash
cd ~/LinkCute/backend

backup_path="/home/ubuntu/roamly-$(date +%Y%m%d-%H%M%S).dump"

docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' \
  > "$backup_path"

chmod 600 "$backup_path"
ls -lh "$backup_path"
sha256sum "$backup_path"
```

Kiểm tra archive:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_restore --list < "$backup_path" | head -20
```

Một backup chỉ nằm trên cùng EC2 chưa đủ an toàn. Nên có bản copy ở một vị trí khác như máy local hoặc S3.

Tải backup từ EC2 về Windows:

```powershell
scp `
  -i "E:\Deploy\roamly-key.pem" `
  ubuntu@linkcute.duckdns.org:/home/ubuntu/roamly-YYYYMMDD-HHMMSS.dump `
  E:\Deploy\db-backups\
```

Sau khi xác nhận backup ngoài EC2 đọc được, thiết lập retention và xóa có chọn lọc các dump cũ. Không dùng wildcard xóa hàng loạt nếu chưa kiểm tra chính xác tên/path.

---

## 22. Kiểm tra sau khi EC2 reboot

Docker, container, Nginx và Certbot timer đã được cấu hình tự khởi động.

Sau reboot:

```bash
sudo reboot
```

SSH lại sau vài phút rồi kiểm tra:

```bash
cd ~/LinkCute/backend
docker compose -f docker-compose.prod.yml ps
sudo systemctl is-active docker
sudo systemctl is-active nginx
systemctl is-active certbot.timer
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  "https://linkcute.duckdns.org/api/v1/places?page=0&size=1"
```

API phải trả HTTP 200.

---

## 23. Troubleshooting

### SSH báo `UNPROTECTED PRIVATE KEY FILE`

Nguyên nhân: `.pem` có quyền đọc cho `Users`, `Authenticated Users` hoặc `Everyone`.

Sửa ACL theo mục 6.1.

### SSH hiểu `ubuntu@IP` là identity file

Nguyên nhân: biến `$keyPath` bị mất trong terminal PowerShell mới.

```powershell
$keyPath = "E:\Deploy\roamly-key.pem"
Test-Path $keyPath
ssh -i "$keyPath" ubuntu@ELASTIC_IP
```

### App ở trạng thái `Restarting`

```bash
docker compose -f docker-compose.prod.yml logs --tail=200 app
```

Tìm `Caused by:` cuối cùng. Các nguyên nhân thường gặp:

- Thiếu biến môi trường.
- Database/Redis chưa healthy.
- Flyway migration lỗi.
- Hibernate schema validation lỗi.
- JWT secret không hợp lệ.

Nếu app restart liên tục trong lúc sửa lỗi:

```bash
docker compose -f docker-compose.prod.yml stop app
```

### Nginx trả `502 Bad Gateway`

Kiểm tra app:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 app
curl -i http://127.0.0.1:8080/api/v1/places?page=0&size=1
```

Nếu curl localhost thất bại, lỗi nằm ở app/container chứ không phải Nginx.

### HTTPS/Certbot challenge thất bại

Kiểm tra:

```bash
getent ahostsv4 linkcute.duckdns.org
curl -I http://linkcute.duckdns.org
sudo nginx -t
```

DNS phải trỏ đúng Elastic IP; Security Group phải mở port 80 và 443.

### Redis cảnh báo `Memory overcommit must be enabled`

Sửa theo mục 11 với `vm.overcommit_memory=1`.

### `totalElements` nhỏ hơn `COUNT(*)`

API lọc `is_deleted = false`. Xem truy vấn kiểm tra soft delete tại mục 17.6.

### Git pull trên EC2 báo local changes

Không reset/xóa ngay. Kiểm tra:

```bash
git status --short
git diff
```

`.env` phải được ignore. Nếu có file source bị sửa trực tiếp trên server, lưu lại diff và xử lý từ máy local/Git thay vì ép pull.

---

## 24. Các lệnh tuyệt đối cẩn trọng

Không chạy trên production nếu chưa hiểu rõ hậu quả:

```bash
docker compose -f docker-compose.prod.yml down -v
docker volume rm roamly_postgres_data
docker system prune --volumes
rm -rf <duong-dan-rong-hoac-khong-chac-chan>
```

Các lệnh trên có thể xóa database hoặc dữ liệu không thể phục hồi.

Trước mọi thao tác restore/drop database:

1. Tạo backup.
2. Kiểm tra exit code bằng `echo $?`.
3. Kiểm tra archive bằng `pg_restore --list`.
4. Ghi checksum SHA-256.
5. Có ít nhất một bản backup ngoài EC2.

---

## 25. Checklist production

- [ ] AWS MFA đang bật.
- [ ] AWS Budget/cost alerts đang bật.
- [ ] Security Group chỉ mở 22 từ IP cá nhân, 80 và 443 public.
- [ ] Elastic IP đang associate đúng EC2.
- [ ] DuckDNS trỏ đúng Elastic IP.
- [ ] HTTPS hợp lệ và `certbot renew --dry-run` thành công.
- [ ] PostgreSQL và Redis không publish host ports.
- [ ] `.env` có mode `600` và không nằm trong Git.
- [ ] SMTP production đã được kiểm thử.
- [ ] `docker compose ps` hiển thị PostgreSQL/Redis healthy.
- [ ] API HTTPS trả HTTP 200.
- [ ] Flyway history V1–V6 đều success.
- [ ] Có backup PostgreSQL ngoài EC2.
- [ ] Đã kiểm tra RAM, disk và Docker disk usage.
- [ ] Khi có frontend, `APP_CORS_ALLOWED_ORIGINS` đã được cập nhật.

---

## 26. Tài liệu chính thức tham khảo

- AWS Free Tier: <https://aws.amazon.com/free/>
- AWS EC2 Security Groups: <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html>
- Docker Engine trên Ubuntu: <https://docs.docker.com/engine/install/ubuntu/>
- Docker Compose: <https://docs.docker.com/compose/>
- PostgreSQL backup/restore: <https://www.postgresql.org/docs/current/backup-dump.html>
- Flyway migrations: <https://documentation.red-gate.com/flyway>
- Certbot: <https://certbot.eff.org/>
- Let's Encrypt và port 80: <https://letsencrypt.org/docs/allow-port-80/>
- DuckDNS: <https://www.duckdns.org/>

