package com.encore.ticket.booking.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;
import com.encore.ticket.core.booking.reservation.port.HoldReader;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.port.PaymentRepository;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doAnswer;

@Sql("/sql/reservation-boundary-fixture.sql")
@Sql(statements = "DELETE FROM payment WHERE reservation_id IN (SELECT id FROM reservation WHERE schedule_id = 910)",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ReservationCreateApiControllerTest extends ApiSpecTestSupport {

    private static final long SCHEDULE_ID = 910L;
    private static final long MEMBER_ID = 1L;
    private static final long OTHER_MEMBER_ID = 2L;
    private static final int NO_PURCHASE_LIMIT = 100;

    @Autowired
    private SeatHoldRepository seatHoldRepository;

    @Autowired
    private HoldReader holdReader;

    @MockitoSpyBean
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void 선점을_예매로_생성하면_201과_좌석이_RESERVED로_반영된다() {
        String holdId = holdViaHttp(List.of(9001L));

        JsonPath body = create(holdId)
                .then().statusCode(201).contentType(ContentType.JSON)
                .body("status", equalTo("PENDING_PAYMENT"))
                .extract().jsonPath();

        assertThat(body.getString("orderId")).isEqualTo("reservation-" + body.getLong("reservationId") + "-1");
        assertThat(body.getMap("$")).containsOnlyKeys(
                "reservationId", "orderId", "orderName", "amount", "status", "expiresAt", "originalExpiresAt");
        assertThat(body.getLong("amount")).isEqualTo(150_000L);
        assertThat(body.getString("expiresAt")).matches(KST_DATE_TIME_REGEX);
        assertThat(body.getString("originalExpiresAt")).matches(KST_DATE_TIME_REGEX);

        String queueToken = admittedQueueToken(SCHEDULE_ID);
        RestAssured.given().spec(spec)
                .header("Authorization", BEARER_TOKEN)
                .header("X-Queue-Token", queueToken)
                .when().get("/schedules/{scheduleId}/seats", SCHEDULE_ID)
                .then().statusCode(200)
                .body("seats.find { it.id == 9001 }.status", equalTo("RESERVED"));
    }

    @Test
    void 예매_생성_응답을_잃어도_Redis_선점이_없으면_기존_예매를_200으로_재응답한다() {
        String holdId = holdViaHttp(List.of(9001L));
        JsonPath first = create(holdId).then().statusCode(201).extract().jsonPath();
        assertThat(redisTemplate.delete("seat-hold:hold:" + holdId)).isTrue();
        assertThat(holdReader.findByHoldId(holdId)).isEmpty();

        JsonPath replay = create(holdId).then().statusCode(200).extract().jsonPath();

        assertThat(replay.getLong("reservationId")).isEqualTo(first.getLong("reservationId"));
        assertThat(replay.getString("orderId")).isEqualTo(first.getString("orderId"));
    }

    @Test
    void 다른_사용자의_Redis_선점은_예매로_생성할_수_없다() {
        SeatHold hold = holdOf(OTHER_MEMBER_ID, List.of(9001L));

        create(hold.holdId()).then().statusCode(403)
                .contentType(PROBLEM_JSON).body("code", equalTo("HOLD_NOT_OWNED"));
    }

    @Test
    void 다른_사용자의_기존_예매도_재응답하지_않는다() {
        Reservation reservation = saveReservation("db-owner", OTHER_MEMBER_ID, ReservationStatus.PENDING_PAYMENT,
                OffsetDateTime.now(clock).plusMinutes(10));

        create(reservation.holdId()).then().statusCode(403)
                .contentType(PROBLEM_JSON).body("code", equalTo("HOLD_NOT_OWNED"));
    }

    @Test
    void 없는_선점은_404를_반환한다() {
        create("missing-hold").then().statusCode(404)
                .contentType(PROBLEM_JSON).body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 취소된_기존_예매는_409를_반환한다() {
        Reservation reservation = saveReservation("cancelled-db", MEMBER_ID, ReservationStatus.CANCELLED,
                OffsetDateTime.now(clock).plusMinutes(10));

        create(reservation.holdId()).then().statusCode(409)
                .contentType(PROBLEM_JSON).body("code", equalTo("RESERVATION_CANCELLED"));
    }

    @Test
    void 만료된_기존_예매는_410을_반환한다() {
        Reservation reservation = saveReservation("expired-db", MEMBER_ID, ReservationStatus.PENDING_PAYMENT,
                OffsetDateTime.now(clock).minusMinutes(1));

        create(reservation.holdId()).then().statusCode(410)
                .contentType(PROBLEM_JSON).body("code", equalTo("HOLD_EXPIRED"));
    }

    @Test
    void 결제_실패_예매는_다음_주문번호를_한번만_발급한다() {
        String holdId = holdViaHttp(List.of(9001L));
        JsonPath created = create(holdId).then().statusCode(201).extract().jsonPath();
        long reservationId = created.getLong("reservationId");
        String originalExpiresAt = created.getString("originalExpiresAt");
        String expiresAt = created.getString("expiresAt");
        int amount = created.getInt("amount");
        paymentRepository.save(Payment.builder()
                .paymentKey("failed-key-" + holdId)
                .orderId("reservation-" + reservationId + "-1")
                .amount(150_000L)
                .reservationId(reservationId)
                .memberId(MEMBER_ID)
                .holdId(holdId)
                .status(PaymentStatus.FAILED)
                .build());

        String secondOrder = create(holdId).then().statusCode(200).extract().jsonPath().getString("orderId");
        String replayOrder = create(holdId).then().statusCode(200).extract().jsonPath().getString("orderId");

        assertThat(secondOrder).isEqualTo("reservation-" + reservationId + "-2");
        assertThat(replayOrder).isEqualTo(secondOrder);
        JsonPath replay = create(holdId).then().statusCode(200).extract().jsonPath();
        assertThat(replay.getLong("reservationId")).isEqualTo(reservationId);
        assertThat(replay.getInt("amount")).isEqualTo(amount);
        assertThat(replay.getString("expiresAt")).isEqualTo(expiresAt);
        assertThat(replay.getString("originalExpiresAt")).isEqualTo(originalExpiresAt);
    }

    @Test
    void CONFIRMED_예매는_결제_기한이_지나도_200으로_재응답한다() {
        Reservation reservation = saveReservation("confirmed-db", MEMBER_ID, ReservationStatus.CONFIRMED,
                OffsetDateTime.now(clock).minusMinutes(1));

        create(reservation.holdId()).then().statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    @Test
    void 동시에_처음_예매하면_중복_저장_롤백_후_같은_예매를_재응답한다() throws Exception {
        String holdId = holdViaHttp(List.of(9001L));
        CountDownLatch bothReadEmpty = new CountDownLatch(2);
        doAnswer(invocation -> {
            Optional<Reservation> actual = (Optional<Reservation>) invocation.callRealMethod();
            if (actual.isEmpty()) {
                bothReadEmpty.countDown();
                assertThat(bothReadEmpty.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return actual;
        }).when(reservationRepository).findByHoldId(holdId);

        List<Response> responses = concurrently(() -> create(holdId));

        assertThat(responses).extracting(Response::statusCode).containsExactlyInAnyOrder(201, 200);
        long id = responses.getFirst().jsonPath().getLong("reservationId");
        assertThat(responses.getLast().jsonPath().getLong("reservationId")).isEqualTo(id);
        assertThat(reservationRepository.findByHoldId(holdId)).get()
                .extracting(Reservation::id).isEqualTo(id);
    }

    @Test
    void 동시에_재결제를_준비해도_같은_다음_주문번호를_반환한다() throws Exception {
        String holdId = holdViaHttp(List.of(9001L));
        JsonPath first = create(holdId).then().statusCode(201).extract().jsonPath();
        long id = first.getLong("reservationId");
        paymentRepository.save(Payment.builder()
                .paymentKey("concurrent-" + holdId).orderId(first.getString("orderId"))
                .amount(first.getLong("amount")).reservationId(id).memberId(MEMBER_ID).holdId(holdId)
                .status(PaymentStatus.FAILED).build());

        List<Response> responses = concurrently(() -> create(holdId));

        assertThat(responses).extracting(Response::statusCode).containsOnly(200);
        assertThat(responses).extracting(response -> response.jsonPath().getString("orderId"))
                .containsOnly("reservation-" + id + "-2");
        assertThat(reservationRepository.getById(id).paymentAttemptNo()).isEqualTo(2);
    }

    @Test
    void 다른_예매가_배정한_좌석은_409이며_새_예매를_남기지_않는다() {
        String firstHold = holdViaHttp(List.of(9001L));
        long firstId = create(firstHold).then().statusCode(201).extract().jsonPath().getLong("reservationId");
        // Redis 선점 수명이 끝난 뒤에도 MySQL의 좌석 배정은 남는 상황을 재현한다.
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        String secondHold = holdViaHttp(List.of(9001L));

        create(secondHold).then().statusCode(409).body("code", equalTo("SEAT_ALREADY_HELD"));

        assertThat(reservationRepository.findByHoldId(secondHold)).isEmpty();
        assertThat(reservationRepository.getById(firstId).seatIds()).containsExactly(9001L);
    }

    private List<Response> concurrently(Callable<Response> call) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Response> task = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return call.call();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private Response create(String holdId) {
        // 병렬 요청끼리 RestAssured의 가변 RequestSpecification을 공유하지 않는다.
        return RestAssured.given().port(port).contentType(ContentType.JSON)
                .header("Authorization", BEARER_TOKEN)
                .body(Map.of("holdId", holdId))
                .when().post("/reservations");
    }

    private SeatHold holdOf(long memberId, List<Long> seatIds) {
        SeatHold hold = SeatHold.hold(SCHEDULE_ID, seatIds, memberId, clock);
        assertThat(seatHoldRepository.acquire(hold, NO_PURCHASE_LIMIT,
                "idem-" + hold.holdId(), "fp-" + hold.holdId()).result())
                .isEqualTo(SeatHoldAcquireResult.ACQUIRED);
        return hold;
    }

    private String holdViaHttp(List<Long> seatIds) {
        JsonPath body = RestAssured.given().spec(spec)
                .header("Authorization", BEARER_TOKEN)
                .header("X-Queue-Token", admittedQueueToken(SCHEDULE_ID))
                .header("Idempotency-Key", "http-" + System.nanoTime())
                .body(Map.of("scheduleId", SCHEDULE_ID, "seatIds", seatIds))
                .when().post("/reservations/holds")
                .then().statusCode(201).extract().jsonPath();
        return body.getString("holdId");
    }

    private Reservation saveReservation(String holdId, long memberId, ReservationStatus status,
                                        OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return reservationRepository.save(Reservation.builder()
                .holdId(holdId)
                .memberId(memberId)
                .scheduleId(SCHEDULE_ID)
                .seatIds(List.of(9001L))
                .amount(150_000L)
                .originalExpiresAt(expiresAt)
                .expiresAt(expiresAt)
                .performanceStartsAt(now.plusDays(1))
                .reservedAt(now)
                .status(status)
                .paymentAttemptNo(1)
                .build());
    }
}
