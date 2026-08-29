package com.encore.ticket.booking.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import com.encore.ticket.core.booking.reservation.domain.Reservation;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.booking.seat.port.SeatAssignmentReader;
import com.encore.ticket.core.payment.domain.Payment;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.port.PaymentRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doAnswer;

@Sql("/sql/reservation-boundary-fixture.sql")
class ReservationApiControllerTest extends ApiSpecTestSupport {

    private static final long SCHEDULE_ID = 910L;
    private static final long MEMBER_ID = 1L;
    private static final long OTHER_MEMBER_ID = 2L;
    private static final long MISSING_RESERVATION_ID = 999_999L;

    private static final List<String> SUMMARY_FIELDS = List.of(
            "id", "concertTitle", "posterUrl", "startsAt", "venue", "seatCount", "totalAmount", "status");
    private static final List<String> DETAIL_FIELDS = List.of(
            "id", "status", "concert", "schedule", "seats",
            "totalAmount", "paymentKey", "orderId", "reservedAt");
    private static final List<String> DETAIL_SEAT_FIELDS =
            List.of("id", "section", "row", "number", "grade", "price");

    @MockitoSpyBean
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SeatAssignmentReader seatAssignmentReader;

    @Test
    void 예매_목록은_본인의_실제_DB_예매를_ID_역순으로_반환한다() {
        Reservation oldest = saveReservation(MEMBER_ID, 9001L, ReservationStatus.CONFIRMED);
        Reservation middle = saveCancelled(MEMBER_ID, 9002L);
        Reservation newest = saveReservation(MEMBER_ID, 9003L, ReservationStatus.PENDING_PAYMENT);
        Reservation other = saveReservation(OTHER_MEMBER_ID, 9004L, ReservationStatus.CONFIRMED);

        JsonPath body = reservationPage(0, 10);

        assertThat(body.getList("content.id", Long.class))
                .containsExactly(newest.id(), middle.id(), oldest.id())
                .doesNotContain(other.id());
        assertThat(body.getInt("totalElements")).isEqualTo(3);
        assertThat(body.getInt("totalPages")).isEqualTo(1);
        assertThat(body.getMap("content[0]")).containsOnlyKeys(SUMMARY_FIELDS.toArray(String[]::new));
        assertThat(body.getString("content[0].startsAt")).matches(KST_DATE_TIME_REGEX);
    }

    @Test
    void 예매_목록은_실제_건수로_페이지를_나눈다() {
        Reservation first = saveReservation(MEMBER_ID, 9001L, ReservationStatus.CONFIRMED);
        Reservation second = saveReservation(MEMBER_ID, 9002L, ReservationStatus.CONFIRMED);
        Reservation third = saveReservation(MEMBER_ID, 9003L, ReservationStatus.PENDING_PAYMENT);

        JsonPath firstPage = reservationPage(0, 2);
        JsonPath secondPage = reservationPage(1, 2);

        assertThat(firstPage.getList("content.id", Long.class)).containsExactly(third.id(), second.id());
        assertThat(secondPage.getList("content.id", Long.class)).containsExactly(first.id());
        assertThat(firstPage.getInt("totalElements")).isEqualTo(3);
        assertThat(firstPage.getInt("totalPages")).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"page=-1", "size=0", "size=abc", "size=101"})
    void 예매_목록의_페이지_파라미터가_유효하지_않으면_400을_반환한다(String query) {
        RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().get("/reservations?" + query)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 완료된_결제의_실제_예매_상세는_콘서트_회차_좌석과_결제_정보를_합쳐_반환한다() {
        Reservation confirmed = saveReservation(MEMBER_ID, 9001L, ReservationStatus.CONFIRMED);
        paymentRepository.save(Payment.builder()
                .paymentKey("payment-key-" + confirmed.id())
                .orderId(confirmed.currentOrderId())
                .amount(confirmed.amount())
                .reservationId(confirmed.id())
                .memberId(MEMBER_ID)
                .holdId(confirmed.holdId())
                .status(PaymentStatus.COMPLETED)
                .build());

        JsonPath body = detailOf(confirmed.id());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getMap("$")).containsOnlyKeys(DETAIL_FIELDS.toArray(String[]::new));
            softly.assertThat(body.getString("status")).isEqualTo("CONFIRMED");
            softly.assertThat(body.getString("paymentKey")).isEqualTo("payment-key-" + confirmed.id());
            softly.assertThat(body.getString("orderId")).isEqualTo(confirmed.currentOrderId());
            softly.assertThat(body.getMap("concert")).containsOnlyKeys("id", "title", "posterUrl");
            softly.assertThat(body.getMap("schedule")).containsOnlyKeys("id", "startsAt", "venue");
            softly.assertThat(body.getMap("seats[0]")).containsOnlyKeys(DETAIL_SEAT_FIELDS.toArray(String[]::new));
            softly.assertThat(body.getMap("seats[0]")).doesNotContainKey("status");
            softly.assertThat(body.getString("reservedAt")).matches(KST_DATE_TIME_REGEX);
            softly.assertThat(body.getString("schedule.startsAt")).matches(KST_DATE_TIME_REGEX);
        });
    }

    @Test
    void 결제_전_예매_상세의_paymentKey와_orderId는_null이다() {
        Reservation pending = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);

        JsonPath body = detailOf(pending.id());

        assertThat(body.getString("status")).isEqualTo("PENDING_PAYMENT");
        assertThat(body.getString("paymentKey")).isNull();
        assertThat(body.getString("orderId")).isNull();
    }

    @Test
    void 다른_사용자의_예매_상세는_403이고_없는_예매는_404다() {
        Reservation other = saveReservation(OTHER_MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);

        RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().get("/reservations/{reservationId}", other.id())
                .then().statusCode(403).body("code", equalTo("RESERVATION_NOT_OWNED"));
        RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().get("/reservations/{reservationId}", MISSING_RESERVATION_ID)
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void PENDING_PAYMENT_예매를_취소하면_상태와_좌석_배정을_함께_변경한다() {
        Reservation pending = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);

        JsonPath body = cancel(pending.id(), ReservationStatus.CANCELLED.name())
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();

        assertThat(body.getMap("$")).containsOnlyKeys("id", "status", "cancelledAt");
        assertThat(body.getString("status")).isEqualTo("CANCELLED");
        assertThat(body.getString("cancelledAt")).matches(KST_DATE_TIME_REGEX);
        assertThat(reservationRepository.getById(pending.id()).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).doesNotContain(9001L);
    }

    @Test
    void 만료_시각이_지났지만_아직_PENDING_PAYMENT인_예매는_이번_PR에서_취소한다() {
        Reservation pending = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT,
                OffsetDateTime.now(clock).minusMinutes(1), null, OffsetDateTime.now(clock).plusDays(1));

        cancel(pending.id(), ReservationStatus.CANCELLED.name())
                .then().statusCode(200).body("status", equalTo("CANCELLED"));
    }

    @Test
    void 이미_취소된_예매를_다시_취소하면_204다() {
        Reservation cancelled = saveCancelled(MEMBER_ID, 9001L);

        assertThat(cancel(cancelled.id(), ReservationStatus.CANCELLED.name())
                .then().statusCode(204).extract().asString()).isEmpty();
    }

    @Test
    void CONFIRMED_예매는_환불_연동_전까지_취소하지_않는다() {
        Reservation confirmed = saveReservation(MEMBER_ID, 9001L, ReservationStatus.CONFIRMED);

        cancel(confirmed.id(), ReservationStatus.CANCELLED.name())
                .then().statusCode(409).body("code", equalTo("CANCELLATION_CLOSED"));

        assertThat(reservationRepository.getById(confirmed.id()).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).contains(9001L);
    }

    @Test
    void 공연이_시작됐거나_결제_처리_중인_예매는_취소하지_않는다() {
        Reservation closed = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT,
                OffsetDateTime.now(clock).plusMinutes(10), null, OffsetDateTime.now(clock));
        Reservation paying = saveReservation(MEMBER_ID, 9002L, ReservationStatus.PENDING_PAYMENT,
                OffsetDateTime.now(clock).plusMinutes(10), OffsetDateTime.now(clock), OffsetDateTime.now(clock).plusDays(1));

        cancel(closed.id(), ReservationStatus.CANCELLED.name())
                .then().statusCode(409).body("code", equalTo("CANCELLATION_CLOSED"));
        cancel(paying.id(), ReservationStatus.CANCELLED.name())
                .then().statusCode(409).body("code", equalTo("PAYMENT_IN_PROGRESS"));
    }

    @Test
    void 다른_사용자의_예매는_403이고_없는_예매는_404다() {
        Reservation other = saveReservation(OTHER_MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);

        cancel(other.id(), ReservationStatus.CANCELLED.name())
                .then().statusCode(403).body("code", equalTo("RESERVATION_NOT_OWNED"));
        cancel(MISSING_RESERVATION_ID, ReservationStatus.CANCELLED.name())
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(strings = {"CONFIRMED", "PENDING_PAYMENT", "EXPIRED"})
    void 취소_요청의_status가_CANCELLED가_아니면_400이다(String status) {
        Reservation pending = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);

        cancel(pending.id(), status)
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .body("code", equalTo("INVALID_REQUEST"))
                .body("errors[0].field", equalTo("status"))
                .body("errors[0].reason", org.hamcrest.Matchers.not(emptyString()));
    }

    @Test
    void 동시에_취소하면_한_요청은_200이고_다른_요청은_204로_수렴한다() throws Exception {
        Reservation pending = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);
        CountDownLatch bothReadPending = new CountDownLatch(2);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            Object actual = invocation.callRealMethod();
            if (reads.incrementAndGet() <= 2) {
                bothReadPending.countDown();
                assertThat(bothReadPending.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return actual;
        }).when(reservationRepository).findById(pending.id());

        List<Response> responses = concurrently(() -> cancel(pending.id(), ReservationStatus.CANCELLED.name()));

        assertThat(responses).extracting(Response::statusCode).containsExactlyInAnyOrder(200, 204);
        assertThat(reservationRepository.getById(pending.id()).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).doesNotContain(9001L);
    }

    @Test
    void 취소_중_재결제_준비로_버전만_바뀌면_최신_상태를_읽고_한번_재시도한다() throws Exception {
        Reservation pending = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);
        CountDownLatch cancellationRead = new CountDownLatch(1);
        CountDownLatch versionChanged = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            Object actual = invocation.callRealMethod();
            if (reads.incrementAndGet() == 1) {
                cancellationRead.countDown();
                assertThat(versionChanged.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return actual;
        }).when(reservationRepository).findById(pending.id());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = executor.submit(() -> cancel(pending.id(), ReservationStatus.CANCELLED.name()));
            assertThat(cancellationRead.await(10, TimeUnit.SECONDS)).isTrue();
            reservationRepository.save(pending.startNextPaymentAttempt());
            versionChanged.countDown();

            assertThat(response.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }

        Reservation cancelled = reservationRepository.getById(pending.id());
        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.paymentAttemptNo()).isEqualTo(2);
        assertThat(seatAssignmentReader.assignedSeatIdsOf(SCHEDULE_ID)).doesNotContain(9001L);
    }

    @Test
    void 예매_목록_상세_취소는_인증이_필요하다() {
        RestAssured.given().spec(spec).when().get("/reservations")
                .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
        RestAssured.given().spec(spec).when().get("/reservations/{reservationId}", MISSING_RESERVATION_ID)
                .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
        RestAssured.given().spec(spec).body(Map.of("status", "CANCELLED"))
                .when().patch("/reservations/{reservationId}", MISSING_RESERVATION_ID)
                .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 취소_요청에_status가_없으면_400이다() {
        Reservation pending = saveReservation(MEMBER_ID, 9001L, ReservationStatus.PENDING_PAYMENT);

        RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN).body(Map.of())
                .when().patch("/reservations/{reservationId}", pending.id())
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .body("code", equalTo("INVALID_REQUEST"))
                .body("errors.field", org.hamcrest.Matchers.hasItem("status"));
    }

    private JsonPath reservationPage(int page, int size) {
        return RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .queryParam("page", page).queryParam("size", size)
                .when().get("/reservations")
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();
    }

    private JsonPath detailOf(long reservationId) {
        return RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().get("/reservations/{reservationId}", reservationId)
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();
    }

    private Response cancel(long reservationId, String status) {
        return RestAssured.given().port(port).contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .body(Map.of("status", status))
                .when().patch("/reservations/{reservationId}", reservationId);
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

    private Reservation saveCancelled(long memberId, long seatId) {
        Reservation pending = saveReservation(memberId, seatId, ReservationStatus.PENDING_PAYMENT);
        return reservationRepository.saveCancelled(pending.cancel(clock));
    }

    private Reservation saveReservation(long memberId, long seatId, ReservationStatus status) {
        return saveReservation(memberId, seatId, status,
                OffsetDateTime.now(clock).plusMinutes(10), null, OffsetDateTime.now(clock).plusDays(1));
    }

    private Reservation saveReservation(
            long memberId,
            long seatId,
            ReservationStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime paymentStartsAt,
            OffsetDateTime performanceStartsAt) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return reservationRepository.saveIssued(Reservation.builder()
                .holdId("query-cancel-" + System.nanoTime())
                .memberId(memberId)
                .scheduleId(SCHEDULE_ID)
                .seatIds(List.of(seatId))
                .amount(150_000L)
                .originalExpiresAt(expiresAt)
                .expiresAt(expiresAt)
                .performanceStartsAt(performanceStartsAt)
                .reservedAt(now)
                .status(status)
                .paymentAttemptNo(1)
                .paymentStartsAt(paymentStartsAt)
                .build());
    }
}
