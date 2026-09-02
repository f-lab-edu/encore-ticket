DELETE FROM seat_assignment WHERE schedule_id IN (930, 931);
DELETE FROM seat WHERE schedule_id IN (930, 931);
DELETE FROM concert_price WHERE concert_id = 930;
DELETE FROM concert_schedule WHERE id IN (930, 931);
DELETE FROM concert WHERE id = 930;

INSERT INTO concert (id, title, description, notice, poster_url, venue, status)
VALUES (930, '좌석 선점 통합 테스트 공연', '설명', NULL, 'https://example.com/poster.png', '테스트홀', 'ON_SALE');

INSERT INTO concert_schedule (id, concert_id, starts_at, ends_at, booking_opens_at, booking_closes_at, status)
VALUES (930, 930, '2030-02-01 19:00:00', '2030-02-01 21:00:00', '2029-12-01 10:00:00', '2029-12-31 23:59:59', 'ON_SALE'),
       (931, 930, '2030-02-02 19:00:00', '2030-02-02 21:00:00', '2029-12-01 10:00:00', '2029-12-31 23:59:59', 'ON_SALE');

INSERT INTO concert_price (concert_id, grade, price)
VALUES (930, 'VIP', 150000);

INSERT INTO seat (id, schedule_id, section_name, row_label, seat_number, grade)
VALUES (9301, 930, 'A구역', '1열', '1번', 'VIP'),
       (9302, 930, 'A구역', '1열', '2번', 'VIP'),
       (9303, 930, 'A구역', '1열', '3번', 'VIP'),
       (9304, 930, 'A구역', '1열', '4번', 'VIP'),
       (9305, 930, 'A구역', '1열', '5번', 'VIP'),
       (9306, 930, 'A구역', '1열', '6번', 'VIP'),
       (9311, 931, 'B구역', '1열', '1번', 'VIP');
