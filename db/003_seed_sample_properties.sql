-- Local development seed data only.
-- These rows are synthetic samples for API smoke tests and must not be treated as live market data.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

INSERT INTO properties (
    property_id,
    property_type,
    name,
    sido,
    sigungu,
    legal_dong,
    road_address,
    jibun_address,
    latitude,
    longitude,
    built_year,
    household_count,
    source_system
) VALUES
    (
        1001,
        'APARTMENT',
        '샘플 역삼아파트',
        '서울특별시',
        '강남구',
        '역삼동',
        '서울특별시 강남구 테헤란로 123',
        '서울특별시 강남구 역삼동 12-3',
        37.5001000,
        127.0364000,
        2010,
        500,
        'LOCAL_SEED'
    ),
    (
        1002,
        'APARTMENT',
        '샘플 해운대아파트',
        '부산광역시',
        '해운대구',
        '우동',
        '부산광역시 해운대구 해운대로 101',
        '부산광역시 해운대구 우동 1400',
        35.1631000,
        129.1636000,
        2018,
        780,
        'LOCAL_SEED'
    ),
    (
        1003,
        'APARTMENT',
        '샘플 서면아파트',
        '부산광역시',
        '부산진구',
        '부전동',
        '부산광역시 부산진구 중앙대로 700',
        '부산광역시 부산진구 부전동 240',
        35.1579000,
        129.0592000,
        2009,
        420,
        'LOCAL_SEED'
    ),
    (
        1901,
        'OFFICETEL',
        '샘플 역삼오피스텔',
        '서울특별시',
        '강남구',
        '역삼동',
        '서울특별시 강남구 논현로 321',
        '서울특별시 강남구 역삼동 88-1',
        37.4988000,
        127.0349000,
        2016,
        240,
        'LOCAL_SEED'
    )
ON DUPLICATE KEY UPDATE
    property_type = VALUES(property_type),
    name = VALUES(name),
    sido = VALUES(sido),
    sigungu = VALUES(sigungu),
    legal_dong = VALUES(legal_dong),
    road_address = VALUES(road_address),
    jibun_address = VALUES(jibun_address),
    latitude = VALUES(latitude),
    longitude = VALUES(longitude),
    built_year = VALUES(built_year),
    household_count = VALUES(household_count),
    source_system = VALUES(source_system),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO property_transactions (
    transaction_id,
    property_id,
    transaction_type,
    exclusive_area_m2,
    floor,
    deal_amount_krw,
    deposit_amount_krw,
    monthly_rent_krw,
    deal_date,
    source_system,
    source_transaction_id
) VALUES
    (5001, 1001, 'SALE', 84.9500, 15, 1250000000, NULL, NULL, '2026-05-20', 'LOCAL_SEED', 'LOCAL-SEED-5001'),
    (5002, 1001, 'JEONSE', 84.9500, 9, NULL, 780000000, 0, '2026-03-15', 'LOCAL_SEED', 'LOCAL-SEED-5002'),
    (5003, 1002, 'SALE', 59.9000, 22, 870000000, NULL, NULL, '2026-04-10', 'LOCAL_SEED', 'LOCAL-SEED-5003'),
    (5004, 1002, 'MONTHLY_RENT', 59.9000, 14, NULL, 120000000, 1500000, '2026-02-12', 'LOCAL_SEED', 'LOCAL-SEED-5004'),
    (5005, 1003, 'SALE', 74.3000, 11, 610000000, NULL, NULL, '2026-01-25', 'LOCAL_SEED', 'LOCAL-SEED-5005'),
    (5006, 1003, 'JEONSE', 74.3000, 8, NULL, 390000000, 0, '2025-12-02', 'LOCAL_SEED', 'LOCAL-SEED-5006'),
    (5901, 1901, 'SALE', 38.2000, 12, 420000000, NULL, NULL, '2026-05-05', 'LOCAL_SEED', 'LOCAL-SEED-5901')
ON DUPLICATE KEY UPDATE
    property_id = VALUES(property_id),
    transaction_type = VALUES(transaction_type),
    exclusive_area_m2 = VALUES(exclusive_area_m2),
    floor = VALUES(floor),
    deal_amount_krw = VALUES(deal_amount_krw),
    deposit_amount_krw = VALUES(deposit_amount_krw),
    monthly_rent_krw = VALUES(monthly_rent_krw),
    deal_date = VALUES(deal_date),
    source_system = VALUES(source_system),
    source_transaction_id = VALUES(source_transaction_id);
