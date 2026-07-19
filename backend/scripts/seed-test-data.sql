-- Seed data for testing Places API
-- Run: psql -U postgres -d roamly -p 5433 -f seed-test-data.sql

-- Helper: generate UUIDs
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Insert sample places (Hanoi locations)
INSERT INTO places (id, name, address, district, lat, lng, category, phone, price_level, price_min, price_max, rating, user_ratings_total, rating_source, enriched, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'Phở Thìn', '13 Lò Đúc', 'Hai Ba Trung', 21.0185, 105.8580, 'FOOD', '0123456789', 1, 50000, 100000, 4.5, 1200, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Bún Chả Hương Liên', '24 Lê Văn Hưu', 'Hai Ba Trung', 21.0200, 105.8550, 'FOOD', '0987654321', 1, 35000, 80000, 4.2, 800, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Cafe Giảng', '39 Nguyễn Hữu Huân', 'Hoan Kiem', 21.0340, 105.8540, 'CAFE', '02438283579', 1, 20000, 50000, 4.3, 2000, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Cafe Đinh', '13 Đinh Tiên Hoàng', 'Hoan Kiem', 21.0290, 105.8530, 'CAFE', '02438683979', 1, 20000, 45000, 4.1, 1500, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'CGV Vincom Center', '54A Nguyễn Chí Thanh', 'Dong Da', 21.0240, 105.8350, 'CINEMA', '19006017', 3, 80000, 200000, 4.0, 5000, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Lotte Cinema', '27 Lê Đại Hành', 'Hai Ba Trung', 21.0120, 105.8500, 'CINEMA', '19001520', 3, 70000, 180000, 4.1, 3500, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Hồ Gươm Xanh', '1 Lê Thái Tổ', 'Hoan Kiem', 21.0288, 105.8530, 'ENTERTAINMENT', '02439238888', 2, 100000, 300000, 4.0, 100, 'google', false, NOW(), NOW()),
  (gen_random_uuid(), 'Bờ Hồ đi bộ', 'Hồ Gươm', 'Hoan Kiem', 21.0285, 105.8542, 'ENTERTAINMENT', NULL, 0, 0, 0, 4.6, 5000, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Vincom Mega Mall Times City', '458 Minh Khai', 'Hai Ba Trung', 20.9940, 105.8720, 'SHOPPING', '02439759888', 2, NULL, NULL, 4.2, 3000, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Chợ Đồng Xuân', 'Đồng Xuân', 'Hoan Kiem', 21.0380, 105.8510, 'SHOPPING', NULL, 1, NULL, NULL, 4.0, 1500, 'google', false, NOW(), NOW()),
  (gen_random_uuid(), 'Phở Cuốn Hưng Thiện', '39 Nguyễn Du', 'Hai Ba Trung', 21.0190, 105.8560, 'FOOD', '02438253939', 1, 30000, 70000, 4.3, 600, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Cộng Cà Phê', '116 Nguyễn Thái Học', 'Dong Da', 21.0260, 105.8420, 'CAFE', '0981122334', 1, 25000, 55000, 4.2, 800, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Galaxy Cinema Nguyễn Du', '116 Nguyễn Du', 'Hai Ba Trung', 21.0210, 105.8580, 'CINEMA', '02436365656', 3, 60000, 150000, 4.0, 2000, 'google', true, NOW(), NOW()),
  (gen_random_uuid(), 'Trà Chanh Quận 5', '5 Lý Quốc Sư', 'Hoan Kiem', 21.0330, 105.8540, 'ENTERTAINMENT', NULL, 0, 10000, 30000, 4.4, 300, 'google', false, NOW(), NOW()),
  (gen_random_uuid(), 'AEON Mall Long Biên', '27 Cổ Linh', 'Long Bien', 21.0460, 105.8950, 'SHOPPING', '02422228888', 2, NULL, NULL, 4.1, 4000, 'google', true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Insert opening hours for some places (day_of_week: 0=Mon, 1=Tue, ..., 6=Sun)
INSERT INTO opening_hours (id, place_id, day_of_week, open_time, close_time, crosses_midnight)
SELECT gen_random_uuid(), p.id, d.day, '08:00'::time, '22:00'::time, false
FROM places p
CROSS JOIN (VALUES (0),(1),(2),(3),(4),(5),(6)) AS d(day)
WHERE p.name = 'Phở Thìn'
ON CONFLICT DO NOTHING;

INSERT INTO opening_hours (id, place_id, day_of_week, open_time, close_time, crosses_midnight)
SELECT gen_random_uuid(), p.id, d.day, '07:00'::time, '21:00'::time, false
FROM places p
CROSS JOIN (VALUES (0),(1),(2),(3),(4),(5),(6)) AS d(day)
WHERE p.name = 'Cafe Giảng'
ON CONFLICT DO NOTHING;

INSERT INTO opening_hours (id, place_id, day_of_week, open_time, close_time, crosses_midnight)
SELECT gen_random_uuid(), p.id, d.day, '09:00'::time, '23:00'::time, false
FROM places p
CROSS JOIN (VALUES (0),(1),(2),(3),(4),(5),(6)) AS d(day)
WHERE p.name = 'CGV Vincom Center'
ON CONFLICT DO NOTHING;

INSERT INTO opening_hours (id, place_id, day_of_week, open_time, close_time, crosses_midnight)
SELECT gen_random_uuid(), p.id, 0, '18:00'::time, '02:00'::time, true
FROM places p
WHERE p.name = 'Hồ Gươm Xanh'
ON CONFLICT DO NOTHING;

INSERT INTO opening_hours (id, place_id, day_of_week, open_time, close_time, crosses_midnight)
SELECT gen_random_uuid(), p.id, 1, '18:00'::time, '02:00'::time, true
FROM places p
WHERE p.name = 'Hồ Gươm Xanh'
ON CONFLICT DO NOTHING;

-- Insert sample photos
INSERT INTO photos (id, place_id, url, sort_order)
SELECT gen_random_uuid(), p.id, 'https://via.placeholder.com/400x300?text=Photo+1', 0
FROM places p
ON CONFLICT DO NOTHING;

INSERT INTO photos (id, place_id, url, sort_order)
SELECT gen_random_uuid(), p.id, 'https://via.placeholder.com/400x300?text=Photo+2', 1
FROM places p
WHERE p.name IN ('Phở Thìn', 'Bún Chả Hương Liên', 'Cafe Giảng')
ON CONFLICT DO NOTHING;

-- Insert sample reviews
INSERT INTO reviews (id, place_id, author_name, rating, text, relative_time_description, published_at, source)
SELECT gen_random_uuid(), p.id, 'Nguyễn Văn A', 5, 'Quán ngon, phở bò thơm ngon, nước dùng ngọt thanh!', '2 tháng trước', NOW() - INTERVAL '2 months', 'google'
FROM places p WHERE p.name = 'Phở Thìn'
ON CONFLICT DO NOTHING;

INSERT INTO reviews (id, place_id, author_name, rating, text, relative_time_description, published_at, source)
SELECT gen_random_uuid(), p.id, 'Trần Thị B', 4, 'Cà phê trứng ngon, không gian cổ kính. Giá cả phải chăng.', '1 tháng trước', NOW() - INTERVAL '1 month', 'google'
FROM places p WHERE p.name = 'Cafe Giảng'
ON CONFLICT DO NOTHING;

INSERT INTO reviews (id, place_id, author_name, rating, text, relative_time_description, published_at, source)
SELECT gen_random_uuid(), p.id, 'Lê Văn C', 4, 'Rạp chiếu phim sạch sẽ, âm thanh tốt. Có nhiều suất chiếu.', '3 tuần trước', NOW() - INTERVAL '3 weeks', 'google'
FROM places p WHERE p.name = 'CGV Vincom Center'
ON CONFLICT DO NOTHING;

-- Verify
SELECT category, COUNT(*) FROM places GROUP BY category ORDER BY category;
