DELETE FROM seat_assignment WHERE schedule_id IN (920, 921);
DELETE FROM seat WHERE schedule_id IN (920, 921);
DELETE FROM concert_price WHERE concert_id = 920;
DELETE FROM concert_schedule WHERE id IN (920, 921);
DELETE FROM concert WHERE id = 920;

INSERT INTO concert (id, title, description, notice, poster_url, venue, status)
VALUES (920, '좌석맵 통합 테스트 공연', '설명', NULL, 'https://example.com/poster.png', '테스트홀', 'ON_SALE');

INSERT INTO concert_schedule (id, concert_id, starts_at, ends_at, booking_opens_at, booking_closes_at, status)
VALUES (920, 920, '2030-01-01 19:00:00', '2030-01-01 21:00:00', '2029-12-01 10:00:00', '2029-12-31 23:59:59', 'ON_SALE'),
       (921, 920, '2030-01-02 19:00:00', '2030-01-02 21:00:00', '2029-12-01 10:00:00', '2029-12-31 23:59:59', 'ON_SALE');

INSERT INTO concert_price (concert_id, grade, price)
VALUES (920, 'VIP', 150000);

INSERT INTO seat (id, schedule_id, section_name, row_label, seat_number, grade)
VALUES (9201, 920, 'A구역', '1열', '1번', 'VIP'),
       (9202, 920, 'A구역', '1열', '2번', 'VIP'),
       (9203, 920, 'A구역', '1열', '3번', 'VIP'),
       (9211, 921, 'B구역', '1열', '1번', 'VIP');

INSERT INTO seat_assignment (seat_id, reservation_id, schedule_id)
VALUES (9203, 92003, 920);
