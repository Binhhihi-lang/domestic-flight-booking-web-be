package com.example.domesticflightbookingwebbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeder dữ liệu lớn sát thực tế cho 5 hãng hàng không nội địa Việt Nam
 * ({@code VN} Vietnam Airlines, {@code VJ} VietJet Air, {@code QH} Bamboo Airways,
 * {@code BL} Pacific Airlines, {@code VU} Vietravel Airlines) với **100.000 chuyến bay**
 * kèm toàn bộ dữ liệu liên quan:
 * <ul>
 *   <li>Đội tàu bay 30 chiếc của 5 hãng (Airbus A320, A321, A321neo, A350-900, Boeing 787-9)</li>
 *   <li>Sơ đồ ghế vật lý ({@code aircraft_seats}) khớp 100% {@code seat_capacity} từng máy bay (~5.800 ghế)</li>
 *   <li>100.000 chuyến bay ({@code flights}) phân bổ thực tế theo tỷ trọng thị phần 5 hãng và 42 đường bay nội địa</li>
 *   <li>Bảng giá theo chuyến ({@code flight_fares}) cho tất cả 100.000 chuyến (~380.000 dòng giá theo cự ly & hãng)</li>
 *   <li>Tồn ghế theo chuyến ({@code flight_seats}) cho toàn bộ các chuyến bay trong khung ngày cao điểm để kiểm thử chọn ghế/giữ ghế (~900.000 dòng ghế)</li>
 *   <li>Khuyến mãi ({@code promotions} + {@code promotion_scopes}), Voucher ({@code vouchers} + {@code voucher_scopes}),
 *       Chuyên mục & Bài viết tin tức ({@code news_categories}, {@code news_items})</li>
 * </ul>
 *
 * <p>Bật tự động khi {@code app.seed.large-dataset=true} (hoặc chạy trên profile {@code local}/{@code docker})
 * và bảng {@code flights} đang có ít hơn 100.000 chuyến. Thực thi bằng SQL set-based trên PostgreSQL
 * nên sinh 100.000 chuyến + hàng trăm nghìn bản ghi liên quan chỉ trong vài giây.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LargeDatasetSeeder {

    private static final int TARGET_FLIGHTS = 100_000;

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.seed.large-dataset:true}")
    private boolean seedEnabled;

    @Bean
    @Order(20)
    public ApplicationRunner seedRealisticLargeDataset() {
        return args -> {
            if (!seedEnabled) {
                log.info("LargeDatasetSeeder đang tắt (app.seed.large-dataset=false)");
                return;
            }
            Long currentFlights = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flights WHERE is_deleted = false", Long.class);
            if (currentFlights != null && currentFlights >= TARGET_FLIGHTS) {
                log.info("Database đã có {} chuyến bay (>= {}), bỏ qua LargeDatasetSeeder.",
                        currentFlights, TARGET_FLIGHTS);
                return;
            }

            long t0 = System.currentTimeMillis();
            log.info("=== BẮT ĐẦU SEED DỮ LIỆU THỰC TẾ LỚN (100.000 CHUYẾN BAY CỦA 5 HÃNG HÀNG KHÔNG) ===");

            seedAirlinesLogosAndCitiesImages();
            seedAircraftFleetAndSeatMaps();
            seedPromotionsVouchersAndNews();
            seed100kFlightsAndFaresAndSeatInventory();
            resetAllSequences();

            long elapsedMs = System.currentTimeMillis() - t0;
            Long totalFlights = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flights", Long.class);
            Long totalFares = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flight_fares", Long.class);
            Long totalFlightSeats = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flight_seats", Long.class);
            Long totalAircrafts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM aircrafts", Long.class);
            Long totalAircraftSeats = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM aircraft_seats", Long.class);

            log.info("=== HOÀN TẤT SEED DỮ LIỆU LỚN TRONG {} ms ===", elapsedMs);
            log.info("Thống kê dữ liệu sau khi seed: aircrafts={}, aircraft_seats={}, flights={}, flight_fares={}, flight_seats={}",
                    totalAircrafts, totalAircraftSeats, totalFlights, totalFares, totalFlightSeats);
        };
    }

    private void seedAirlinesLogosAndCitiesImages() {
        jdbcTemplate.execute("""
                UPDATE airlines SET logo_url = CASE airline_code
                    WHEN 'VN' THEN 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/Vietnam_Airlines_logo.svg/512px-Vietnam_Airlines_logo.svg.png'
                    WHEN 'VJ' THEN 'https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/VietJet_Air_logo.svg/512px-VietJet_Air_logo.svg.png'
                    WHEN 'QH' THEN 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Bamboo_Airways_Logo.svg/512px-Bamboo_Airways_Logo.svg.png'
                    WHEN 'BL' THEN 'https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Pacific_Airlines_logo.svg/512px-Pacific_Airlines_logo.svg.png'
                    WHEN 'VU' THEN 'https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Vietravel_Airlines_Logo.svg/512px-Vietravel_Airlines_Logo.svg.png'
                    ELSE logo_url
                END
                WHERE logo_url IS NULL;

                UPDATE cities SET image_url = CASE city_code
                    WHEN 'HAN' THEN 'https://images.unsplash.com/photo-1583417319070-4a69db38a482?auto=format&fit=crop&w=800&q=80'
                    WHEN 'SGN' THEN 'https://images.unsplash.com/photo-1583417267826-aebc4d1542e1?auto=format&fit=crop&w=800&q=80'
                    WHEN 'DAD' THEN 'https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=800&q=80'
                    WHEN 'PQC' THEN 'https://images.unsplash.com/photo-1528181304800-259b08848526?auto=format&fit=crop&w=800&q=80'
                    WHEN 'CXR' THEN 'https://images.unsplash.com/photo-1589394815804-964ed0be2eb5?auto=format&fit=crop&w=800&q=80'
                    WHEN 'DLI' THEN 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80'
                    ELSE 'https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=800&q=80'
                END
                WHERE image_url IS NULL;
                """);
    }

    /**
     * Tạo 30 tàu bay thực tế của 5 hãng hàng không và sinh đầy đủ sơ đồ ghế ({@code aircraft_seats})
     * khớp 100% {@code seat_capacity} (mỗi hàng 6 ghế A-F, lối đi sau cột C).
     */
    private void seedAircraftFleetAndSeatMaps() {
        jdbcTemplate.execute("""
                INSERT INTO aircrafts (aircraft_code, model, manufacturer, seat_capacity, aisle_after, is_active, is_deleted)
                SELECT v.code, v.model, v.maker, v.capacity, 'C', true, false
                FROM (VALUES
                    -- Vietnam Airlines (VN): 8 tàu bay (A321, A321neo, B787-9, A350-900)
                    ('VN-A321-01', 'Airbus A321-200',   'Airbus', 180),
                    ('VN-A321-02', 'Airbus A321-200',   'Airbus', 180),
                    ('VN-A321-03', 'Airbus A321neo',    'Airbus', 198),
                    ('VN-A321-04', 'Airbus A321neo',    'Airbus', 198),
                    ('VN-A321-05', 'Airbus A321neo',    'Airbus', 198),
                    ('VN-B787-01', 'Boeing 787-9',      'Boeing', 240),
                    ('VN-B787-02', 'Boeing 787-9',      'Boeing', 240),
                    ('VN-A350-01', 'Airbus A350-900',   'Airbus', 240),
                    -- VietJet Air (VJ): 8 tàu bay (A320, A321, A321neo)
                    ('VJ-A320-01', 'Airbus A320-200',   'Airbus', 180),
                    ('VJ-A320-02', 'Airbus A320-200',   'Airbus', 180),
                    ('VJ-A320-03', 'Airbus A320-200',   'Airbus', 180),
                    ('VJ-A321-01', 'Airbus A321-200',   'Airbus', 216),
                    ('VJ-A321-02', 'Airbus A321-200',   'Airbus', 216),
                    ('VJ-A321-03', 'Airbus A321neo',    'Airbus', 228),
                    ('VJ-A321-04', 'Airbus A321neo',    'Airbus', 228),
                    ('VJ-A321-05', 'Airbus A321neo',    'Airbus', 228),
                    -- Bamboo Airways (QH): 6 tàu bay (A320neo, A321neo, B787-9)
                    ('QH-A320-01', 'Airbus A320neo',    'Airbus', 174),
                    ('QH-A320-02', 'Airbus A320neo',    'Airbus', 174),
                    ('QH-A321-01', 'Airbus A321neo',    'Airbus', 198),
                    ('QH-A321-02', 'Airbus A321neo',    'Airbus', 198),
                    ('QH-A321-03', 'Airbus A321-200',   'Airbus', 192),
                    ('QH-B787-01', 'Boeing 787-9',      'Boeing', 240),
                    -- Pacific Airlines (BL): 4 tàu bay (A320-200)
                    ('BL-A320-01', 'Airbus A320-200',   'Airbus', 180),
                    ('BL-A320-02', 'Airbus A320-200',   'Airbus', 180),
                    ('BL-A320-03', 'Airbus A320-200',   'Airbus', 180),
                    ('BL-A320-04', 'Airbus A320-200',   'Airbus', 180),
                    -- Vietravel Airlines (VU): 4 tàu bay (A321-200)
                    ('VU-A321-01', 'Airbus A321-200',   'Airbus', 210),
                    ('VU-A321-02', 'Airbus A321-200',   'Airbus', 210),
                    ('VU-A321-03', 'Airbus A321-200',   'Airbus', 210),
                    ('VU-A321-04', 'Airbus A321-200',   'Airbus', 210)
                ) AS v(code, model, maker, capacity)
                WHERE NOT EXISTS (
                    SELECT 1 FROM aircrafts a WHERE a.aircraft_code = v.code AND a.is_deleted = false
                );
                """);

        // Sinh sơ đồ ghế (aircraft_seats) cho mọi tàu bay chưa có đủ ghế:
        // Hàng 1-3: BUSINESS (BUS)
        // Hàng 4-6: PREMIUM_ECONOMY (PREM_ECO)
        // Hàng 7: ECONOMY Hàng đầu (ECO_FRONT)
        // Hàng 12, 14: ECONOMY Thoát hiểm (ECO_EXIT)
        // Còn lại: Cột A,F = ECO_WIN (cửa sổ), C,D = ECO_AIS (lối đi), B,E = ECO (tiêu chuẩn)
        jdbcTemplate.execute("""
                INSERT INTO aircraft_seats (aircraft_id, seat_type_id, seat_row, seat_column)
                SELECT
                    ac.id,
                    CASE
                        WHEN r.row_num <= 3 THEN (SELECT id FROM seat_types WHERE seat_type_code = 'BUS' AND is_deleted = false LIMIT 1)
                        WHEN r.row_num <= 6 THEN (SELECT id FROM seat_types WHERE seat_type_code = 'PREM_ECO' AND is_deleted = false LIMIT 1)
                        WHEN r.row_num = 7  THEN (SELECT id FROM seat_types WHERE seat_type_code = 'ECO_FRONT' AND is_deleted = false LIMIT 1)
                        WHEN r.row_num IN (12, 14) THEN (SELECT id FROM seat_types WHERE seat_type_code = 'ECO_EXIT' AND is_deleted = false LIMIT 1)
                        WHEN c.col_code IN ('A', 'F') THEN (SELECT id FROM seat_types WHERE seat_type_code = 'ECO_WIN' AND is_deleted = false LIMIT 1)
                        WHEN c.col_code IN ('C', 'D') THEN (SELECT id FROM seat_types WHERE seat_type_code = 'ECO_AIS' AND is_deleted = false LIMIT 1)
                        ELSE (SELECT id FROM seat_types WHERE seat_type_code = 'ECO' AND is_deleted = false LIMIT 1)
                    END AS seat_type_id,
                    r.row_num,
                    c.col_code
                FROM aircrafts ac
                CROSS JOIN LATERAL generate_series(1, ac.seat_capacity / 6) AS r(row_num)
                CROSS JOIN (VALUES ('A'), ('B'), ('C'), ('D'), ('E'), ('F')) AS c(col_code)
                WHERE ac.is_deleted = false
                  AND NOT EXISTS (
                      SELECT 1 FROM aircraft_seats s WHERE s.aircraft_id = ac.id
                  );
                """);
    }

    private void seedPromotionsVouchersAndNews() {
        jdbcTemplate.execute("""
                INSERT INTO promotions (
                    promotion_code, promotion_name, description, badge_label, image_url,
                    discount_type, discount_value, max_discount_amount, min_fare_amount,
                    applicable_trip_type, start_date, end_date, is_active, is_deleted
                )
                SELECT v.code, v.name, v.descr, v.badge, v.img,
                       v.dtype, v.dval, v.max_disc, v.min_fare,
                       v.trip_type, CURRENT_DATE - 15, CURRENT_DATE + 180, true, false
                FROM (VALUES
                    ('VINAFLY_TET2027', 'Mùa lễ hội & Tết 2027 — Giảm 15% toàn mạng bay',
                     'Áp dụng tự động cho các đường bay nội địa của 5 hãng hàng không.', 'Hot',
                     'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80',
                     'PERCENT', 15.00::numeric, 250000.00::numeric, 800000.00::numeric, 'ANY'),
                    ('THUONG_GIA_UU_DAI', 'Trải nghiệm Thương gia & Phổ thông đặc biệt — Giảm 300.000đ',
                     'Giảm trực tiếp vào giá vé cho khách đặt hạng Thương gia và Phổ thông đặc biệt.', 'Nâng hạng',
                     'https://images.unsplash.com/photo-1583417319070-4a69db38a482?auto=format&fit=crop&w=800&q=80',
                     'AMOUNT', 300000.00::numeric, 300000.00::numeric, 1800000.00::numeric, 'ANY'),
                    ('KHU_HOI_TIET_KIEM', 'Bay khứ hồi nội địa — Giảm ngay 10%',
                     'Ưu đãi dành riêng cho hành trình khứ hồi trên mọi tuyến bay nội địa.', 'Khứ hồi',
                     'https://images.unsplash.com/photo-1528181304800-259b08848526?auto=format&fit=crop&w=800&q=80',
                     'PERCENT', 10.00::numeric, 200000.00::numeric, 1000000.00::numeric, 'ROUND_TRIP')
                ) AS v(code, name, descr, badge, img, dtype, dval, max_disc, min_fare, trip_type)
                WHERE NOT EXISTS (
                    SELECT 1 FROM promotions p WHERE p.promotion_code = v.code AND p.is_deleted = false
                );

                INSERT INTO vouchers (
                    voucher_code, voucher_name, description, discount_type, discount_value,
                    max_discount_amount, min_order_amount, applicable_trip_type,
                    distribution_type, claim_limit, claimed_count, start_date, end_date, is_active, is_deleted
                )
                SELECT v.code, v.name, v.descr, v.dtype, v.dval,
                       v.max_disc, v.min_order, v.trip_type,
                       'GLOBAL', v.climit, 0, CURRENT_DATE - 10, CURRENT_DATE + 120, true, false
                FROM (VALUES
                    ('BAYHE2026', 'Giảm 200.000đ chuyến nội địa',
                     'Áp dụng cho đơn từ 1.500.000đ trên toàn bộ 5 hãng nội địa',
                     'AMOUNT', 200000.00::numeric, 200000.00::numeric, 1500000.00::numeric, 'ANY', 1000),
                    ('WELCOME10', 'Chào bạn mới — Giảm 10% tối đa 300.000đ',
                     'Giảm 10% cho đơn đặt vé máy bay nội địa bất kỳ',
                     'PERCENT', 10.00::numeric, 300000.00::numeric, 500000.00::numeric, 'ANY', 2000),
                    ('KHUHOI500K', 'Ưu đãi khứ hồi — Giảm 500.000đ',
                     'Dành cho đơn đặt vé khứ hồi từ 3.500.000đ',
                     'AMOUNT', 500000.00::numeric, 500000.00::numeric, 3500000.00::numeric, 'ROUND_TRIP', 500)
                ) AS v(code, name, descr, dtype, dval, max_disc, min_order, trip_type, climit)
                WHERE NOT EXISTS (
                    SELECT 1 FROM vouchers vc WHERE vc.voucher_code = v.code AND vc.is_deleted = false
                );

                INSERT INTO news_categories (name, description, is_active, is_deleted)
                SELECT v.name, v.descr, true, false
                FROM (VALUES
                    ('Tin hãng hàng không', 'Thông tin lịch bay, đường bay mới của 5 hãng nội địa'),
                    ('Khuyến mãi & Ưu đãi', 'Tổng hợp chương trình ưu đãi giá vé máy bay'),
                    ('Cẩm nang bay nội địa', 'Kinh nghiệm làm thủ tục, hành lý và chọn chỗ ngồi')
                ) AS v(name, descr)
                WHERE NOT EXISTS (
                    SELECT 1 FROM news_categories nc WHERE nc.name = v.name AND nc.is_deleted = false
                );
                """);
    }

    /**
     * Sinh đúng 100.000 chuyến bay thực tế cho 5 hãng hàng không (VN, VJ, QH, BL, VU)
     * kèm ~380.000 dòng giá ({@code flight_fares}) và tồn ghế ({@code flight_seats}) cho khung ngày hiện tại.
     */
    private void seed100kFlightsAndFaresAndSeatInventory() {
        long startFlights = System.currentTimeMillis();
        log.info("Đang sinh 100.000 chuyến bay sát thực tế của 5 hãng (VN 35%, VJ 33%, QH 14%, BL 10%, VU 8%)...");

        // Xóa dữ liệu chuyến cũ (nếu có ít hơn 100k và chưa có booking) để đảm bảo không đụng unique index
        jdbcTemplate.execute("""
                DELETE FROM flight_seats WHERE flight_id NOT IN (SELECT flight_id FROM booking_segments);
                DELETE FROM flight_fares WHERE flight_id NOT IN (SELECT flight_id FROM booking_segments);
                DELETE FROM flights WHERE id NOT IN (SELECT flight_id FROM booking_segments);
                """);

        // Sinh 100.000 chuyến bay bằng CTE với 5 hãng, 42 tuyến bay nội địa thực tế
        // Trải đều từ ngày (CURRENT_DATE - 30) đến ~450 ngày tiếp theo,
        // đảm bảo mỗi ngày có hơn 200 chuyến bay phủ khắp các cặp sân bay lớn (HAN-SGN, HAN-DAD, SGN-DAD, HAN-PQC, SGN-PQC, HAN-CXR...).
        jdbcTemplate.execute("""
                WITH route_catalog AS (
                    SELECT * FROM (VALUES
                        -- Các trục vàng và đường bay phổ biến (dep_id, arr_id, duration_mins, base_price_econ)
                        (0,  1,  2, 125, 1150000::numeric), -- HAN -> SGN
                        (1,  2,  1, 125, 1150000::numeric), -- SGN -> HAN
                        (2,  1,  3,  80,  760000::numeric), -- HAN -> DAD
                        (3,  3,  1,  80,  760000::numeric), -- DAD -> HAN
                        (4,  2,  3,  85,  780000::numeric), -- SGN -> DAD
                        (5,  3,  2,  85,  780000::numeric), -- DAD -> SGN
                        (6,  1,  9, 130, 1350000::numeric), -- HAN -> PQC
                        (7,  9,  1, 130, 1350000::numeric), -- PQC -> HAN
                        (8,  2,  9,  60,  690000::numeric), -- SGN -> PQC
                        (9,  9,  2,  60,  690000::numeric), -- PQC -> SGN
                        (10, 1,  6, 110, 1080000::numeric), -- HAN -> CXR
                        (11, 6,  1, 110, 1080000::numeric), -- CXR -> HAN
                        (12, 2,  6,  65,  650000::numeric), -- SGN -> CXR
                        (13, 6,  2,  65,  650000::numeric), -- CXR -> SGN
                        (14, 1,  7, 110, 1050000::numeric), -- HAN -> DLI
                        (15, 7,  1, 110, 1050000::numeric), -- DLI -> HAN
                        (16, 2,  7,  55,  620000::numeric), -- SGN -> DLI
                        (17, 7,  2,  55,  620000::numeric), -- DLI -> SGN
                        (18, 1,  5,  75,  720000::numeric), -- HAN -> HUI
                        (19, 5,  1,  75,  720000::numeric), -- HUI -> HAN
                        (20, 2,  5,  85,  790000::numeric), -- SGN -> HUI
                        (21, 5,  2,  85,  790000::numeric), -- HUI -> SGN
                        (22, 2,  4, 120, 1120000::numeric), -- SGN -> HPH
                        (23, 4,  2, 120, 1120000::numeric), -- HPH -> SGN
                        (24, 1,  8, 130, 1190000::numeric), -- HAN -> VCA
                        (25, 8,  1, 130, 1190000::numeric), -- VCA -> HAN
                        (26, 1, 10, 105,  980000::numeric), -- HAN -> BMV
                        (27,10,  1, 105,  980000::numeric), -- BMV -> HAN
                        (28, 2, 10,  60,  640000::numeric), -- SGN -> BMV
                        (29,10,  2,  60,  640000::numeric), -- BMV -> SGN
                        (30, 2, 11, 110, 1020000::numeric), -- SGN -> VII
                        (31,11,  2, 110, 1020000::numeric), -- VII -> SGN
                        (32, 2, 12, 115, 1040000::numeric), -- SGN -> THD
                        (33,12,  2, 115, 1040000::numeric), -- THD -> SGN
                        (34, 3,  6,  70,  680000::numeric), -- DAD -> CXR
                        (35, 6,  3,  70,  680000::numeric), -- CXR -> DAD
                        (36, 3,  7,  75,  710000::numeric), -- DAD -> DLI
                        (37, 7,  3,  75,  710000::numeric), -- DLI -> DAD
                        (38, 3,  9, 105,  990000::numeric), -- DAD -> PQC
                        (39, 9,  3, 105,  990000::numeric), -- PQC -> DAD
                        (40, 3,  4,  75,  730000::numeric), -- DAD -> HPH
                        (41, 4,  3,  75,  730000::numeric)  -- HPH -> DAD
                    ) AS r(route_idx, dep_id, arr_id, duration_mins, base_price)
                ),
                airline_fleet AS (
                    SELECT
                        al.id AS airline_id,
                        al.airline_code,
                        ARRAY_AGG(ac.id ORDER BY ac.id) AS aircraft_ids,
                        COUNT(ac.id) AS fleet_size
                    FROM airlines al
                    JOIN aircrafts ac ON left(ac.aircraft_code, 2) = al.airline_code AND ac.is_deleted = false
                    WHERE al.is_deleted = false
                    GROUP BY al.id, al.airline_code
                ),
                generated_series AS (
                    SELECT
                        g AS seq,
                        -- Phân bổ thị phần 5 hãng sát thực tế:
                        -- VN (35%): 0..34, VJ (33%): 35..67, QH (14%): 68..81, BL (10%): 82..91, VU (8%): 92..99
                        CASE
                            WHEN (g % 100) < 35 THEN 'VN'
                            WHEN (g % 100) < 68 THEN 'VJ'
                            WHEN (g % 100) < 82 THEN 'QH'
                            WHEN (g % 100) < 92 THEN 'BL'
                            ELSE 'VU'
                        END AS airline_code,
                        (g % 42) AS route_idx,
                        -- 250 chuyến mỗi ngày -> 100.000 chuyến phủ đúng 400 ngày (từ -15 ngày trước đến +384 ngày tới)
                        ((g - 1) / 250) - 15 AS day_offset,
                        ((g - 1) % 250) AS slot_in_day
                    FROM generate_series(1, 100000) AS g
                )
                INSERT INTO flights (
                    flight_number, airline_id, aircraft_id,
                    departure_airport_id, arrival_airport_id,
                    scheduled_departure, scheduled_arrival,
                    operation_status, sales_status,
                    is_deleted, created_at, updated_at
                )
                SELECT
                    gs.airline_code || (100 + gs.route_idx * 20 + (gs.slot_in_day % 20))::text AS flight_number,
                    af.airline_id,
                    af.aircraft_ids[1 + (gs.seq % af.fleet_size)] AS aircraft_id,
                    rc.dep_id,
                    rc.arr_id,
                    -- Giờ khởi hành trải từ 05:00 đến 22:55, mỗi slot trong ngày lệch phút để không bao giờ trùng (flight_number, scheduled_departure)
                    (CURRENT_DATE + (gs.day_offset || ' days')::interval
                        + ((300 + gs.slot_in_day * 4) || ' minutes')::interval)::timestamptz AS scheduled_departure,
                    (CURRENT_DATE + (gs.day_offset || ' days')::interval
                        + ((300 + gs.slot_in_day * 4 + rc.duration_mins) || ' minutes')::interval)::timestamptz AS scheduled_arrival,
                    CASE
                        WHEN gs.day_offset < 0 THEN 'ARRIVED'
                        WHEN gs.seq % 97 = 0   THEN 'DELAYED'
                        ELSE 'SCHEDULED'
                    END AS operation_status,
                    CASE
                        WHEN gs.day_offset < 0 THEN 'CLOSED'
                        ELSE 'OPEN'
                    END AS sales_status,
                    false,
                    now(),
                    now()
                FROM generated_series gs
                JOIN route_catalog rc ON rc.route_idx = gs.route_idx
                JOIN airline_fleet af ON af.airline_code = gs.airline_code;
                """);

        long flightsDone = System.currentTimeMillis();
        log.info("Đã sinh xong 100.000 chuyến bay trong {} ms. Đang sinh bảng giá (flight_fares) cho 100.000 chuyến...",
                flightsDone - startFlights);

        // Sinh flight_fares cho toàn bộ 100.000 chuyến bay:
        // Mỗi chuyến có từ 3 đến 4 hạng vé của đúng hãng khai thác chuyến đó:
        // ECON_SAVER, ECON_STANDARD, ECON_FLEX, và BIZ_STANDARD (cho VN, QH, VJ)
        jdbcTemplate.execute("""
                INSERT INTO flight_fares (
                    flight_id, fare_class_id, base_fare, tax_fee, currency, effective_from, effective_to, created_at
                )
                SELECT
                    f.id AS flight_id,
                    fc.id AS fare_class_id,
                    -- Giá cơ bản sát thực tế theo thời lượng chuyến bay, hãng hàng không và hạng vé
                    ROUND((
                        (550000 + EXTRACT(EPOCH FROM (f.scheduled_arrival - f.scheduled_departure)) / 60 * 5500)
                        * CASE a.airline_code
                            WHEN 'VN' THEN 1.22
                            WHEN 'QH' THEN 1.12
                            WHEN 'VU' THEN 1.00
                            WHEN 'BL' THEN 0.94
                            ELSE 0.90 -- VJ
                          END
                        * CASE
                            WHEN fc.fare_class_code LIKE '%_ECON_SAVER'    THEN 1.00
                            WHEN fc.fare_class_code LIKE '%_ECON_STANDARD' THEN 1.25
                            WHEN fc.fare_class_code LIKE '%_ECON_FLEX'     THEN 1.50
                            WHEN fc.fare_class_code LIKE '%_BIZ_STANDARD'  THEN 2.65
                            ELSE 1.85
                          END
                    ) / 10000.0) * 10000 AS base_fare,
                    CASE
                        WHEN fc.seat_class = 'BUSINESS' THEN 320000.00::numeric
                        ELSE 160000.00::numeric
                    END AS tax_fee,
                    'VND',
                    '2026-01-01 00:00:00+00'::timestamptz,
                    NULL,
                    now()
                FROM flights f
                JOIN airlines a ON a.id = f.airline_id
                JOIN fare_classes fc ON fc.airline_id = f.airline_id AND fc.is_deleted = false
                WHERE fc.fare_class_code IN (
                    a.airline_code || '_ECON_SAVER',
                    a.airline_code || '_ECON_STANDARD',
                    a.airline_code || '_ECON_FLEX',
                    a.airline_code || '_BIZ_STANDARD'
                );
                """);

        long faresDone = System.currentTimeMillis();
        log.info("Đã sinh xong bảng giá flight_fares (400.000 dòng) trong {} ms. Đang sinh tồn ghế (flight_seats)...",
                faresDone - flightsDone);

        // Sinh flight_seats cho các chuyến bay trong cửa sổ 20 ngày tới (~5.000 chuyến x ~190 ghế = ~950.000 dòng flight_seats)
        // để kiểm thử hiệu năng JOIN/GROUP BY trên hàng triệu bản ghi mà không làm nổ ổ cứng máy dev
        jdbcTemplate.execute("""
                INSERT INTO flight_seats (
                    flight_id, aircraft_seat_id, seat_code, status, seat_surcharge, version
                )
                SELECT
                    f.id,
                    acs.id,
                    acs.seat_row::text || acs.seat_column,
                    CASE
                        WHEN (f.id + acs.seat_row) % 23 = 0 THEN 'BOOKED'
                        WHEN (f.id + acs.seat_row) % 53 = 0 THEN 'BLOCKED'
                        ELSE 'AVAILABLE'
                    END AS status,
                    st.surcharge,
                    0
                FROM flights f
                JOIN aircraft_seats acs ON acs.aircraft_id = f.aircraft_id
                JOIN seat_types st ON st.id = acs.seat_type_id
                WHERE f.scheduled_departure >= CURRENT_DATE
                  AND f.scheduled_departure < CURRENT_DATE + INTERVAL '20 days';
                """);

        long seatsDone = System.currentTimeMillis();
        log.info("Đã sinh xong tồn ghế flight_seats trong {} ms. Đang chạy ANALYZE để PostgreSQL tối ưu query planner...",
                seatsDone - faresDone);

        jdbcTemplate.execute("ANALYZE airlines, airports, aircrafts, aircraft_seats, fare_classes, fare_rules, flights, flight_fares, flight_seats;");
    }

    private void resetAllSequences() {
        jdbcTemplate.execute("""
                SELECT setval(pg_get_serial_sequence('aircrafts', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM aircrafts), 1));
                SELECT setval(pg_get_serial_sequence('aircraft_seats', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM aircraft_seats), 1));
                SELECT setval(pg_get_serial_sequence('flights', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM flights), 1));
                SELECT setval(pg_get_serial_sequence('flight_fares', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM flight_fares), 1));
                SELECT setval(pg_get_serial_sequence('flight_seats', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM flight_seats), 1));
                SELECT setval(pg_get_serial_sequence('promotions', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM promotions), 1));
                SELECT setval(pg_get_serial_sequence('vouchers', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM vouchers), 1));
                SELECT setval(pg_get_serial_sequence('news_categories', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) FROM news_categories), 1));
                """);
    }
}
