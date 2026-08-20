DELETE FROM seat_assignment WHERE schedule_id = 910;
DELETE FROM reservation_seat WHERE schedule_id = 910;
DELETE FROM reservation WHERE schedule_id = 910;
DELETE FROM seat WHERE schedule_id = 910;
DELETE FROM concert_price WHERE concert_id = 900;
DELETE FROM concert_schedule WHERE id = 910;
DELETE FROM concert WHERE id = 900;

INSERT INTO concert (id, title, description, notice, poster_url, venue, status)
VALUES (900, '통합 테스트 콘서트', '설명', NULL, 'https://example.com/poster.png', '테스트홀', 'ON_SALE');

INSERT INTO concert_schedule (id, concert_id, starts_at, ends_at, booking_opens_at, booking_closes_at, status)
VALUES (910, 900, '2030-01-01 19:00:00', '2030-01-01 21:00:00', '2029-12-01 10:00:00', '2029-12-31 23:59:59', 'ON_SALE');

INSERT INTO concert_price (concert_id, grade, price) VALUES (900, 'VIP', 150000);

INSERT INTO seat (id, schedule_id, section_name, row_label, seat_number, grade)
VALUES (9001, 910, 'A구역', '1열', '1번', 'VIP'),
       (9002, 910, 'A구역', '1열', '2번', 'VIP'),
       (9003, 910, 'A구역', '2열', '1번', 'VIP'),
       (9004, 910, 'A구역', '2열', '2번', 'VIP'),
       (9005, 910, 'A구역', '3열', '1번', 'VIP'),
       (9006, 910, 'A구역', '3열', '2번', 'VIP');
