package com.encore.ticket.booking.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.dto.ReservationStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.SoftAssertions;
import org.springframework.http.HttpHeaders;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class ReservationApiControllerTest extends ApiSpecTestSupport {

    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final long AVAILABLE_SEAT_ID = 2011L;

    private static final long OTHER_AVAILABLE_SEAT_ID = 2014L;

    private static final long HELD_SEAT_ID = 2012L;

    private static final long OTHER_SCHEDULE_SEAT_ID = 2021L;

    private static final long MISSING_SEAT_ID = 9999L;

    private static final long MISSING_SCHEDULE_SEAT_ID = 9991L;

    private static final List<Long> MAX_ALLOWED_SEAT_IDS = List.of(2011L, 2014L, 2015L, 2016L);

    private static final List<String> SPEC_HOLD_FIELDS =
            List.of("holdId", "scheduleId", "seatIds", "totalAmount", "expiresAt");

    private static final List<String> SPEC_CREATE_FIELDS = List.of(
            "reservationId", "orderId", "orderName", "amount", "status", "expiresAt", "originalExpiresAt");

    private static final List<String> SPEC_SUMMARY_FIELDS = List.of(
            "id", "concertTitle", "posterUrl", "startsAt", "venue", "seatCount", "totalAmount", "status");

    private static final List<String> SPEC_DETAIL_FIELDS = List.of(
            "id", "status", "concert", "schedule", "seats",
            "totalAmount", "paymentKey", "orderId", "reservedAt");

    private static final List<String> SPEC_DETAIL_SEAT_FIELDS =
            List.of("id", "section", "row", "number", "grade", "price");

    private static final List<String> SPEC_CANCEL_FIELDS = List.of("id", "status", "cancelledAt");

    private static final List<String> SPEC_RESERVATION_STATUS_NAMES =
            List.of("PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED");

    @Test
    void 좌석을_선점하면_201과_스펙에_정의된_5개_필드를_반환한다() {
        Map<String, Object> body = holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, List.of(AVAILABLE_SEAT_ID))
                .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_HOLD_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("holdId")).isInstanceOf(String.class);
            softly.assertThat(body.get("scheduleId")).isEqualTo((int) StubSchedules.OPEN_SCHEDULE_ID);
            softly.assertThat(body.get("seatIds")).isEqualTo(List.of((int) AVAILABLE_SEAT_ID));
            softly.assertThat(body.get("totalAmount")).isInstanceOf(Integer.class);
        });
    }

    @Test
    void 선점_응답의_totalAmount는_좌석_배치도_가격의_합과_정확히_같다() {
        List<Long> seatIds = List.of(AVAILABLE_SEAT_ID, OTHER_AVAILABLE_SEAT_ID);

        JsonPath seatMap = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        long expected = seatIds.stream()
                .mapToLong(seatId -> seatMap.getLong("seats.find { it.id == %d }.price".formatted(seatId)))
                .sum();

        int totalAmount = holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, seatIds)
                .then().statusCode(201)
                .extract().jsonPath().getInt("totalAmount");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(expected).isPositive();
            softly.assertThat((long) totalAmount).isEqualTo(expected);
        });
    }

    @Test
    void 선점_만료_시각은_ISO_8601_KST_오프셋_형식이다() {
        String expiresAt = holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, List.of(AVAILABLE_SEAT_ID))
                .then().statusCode(201)
                .extract().jsonPath().getString("expiresAt");

        assertThat(expiresAt).matches(KST_DATE_TIME_REGEX);
    }

    @Test
    void 같은_요청_키로_재요청하면_200을_반환한다() {
        holdRequest(StubReservations.REPLAYED_IDEMPOTENCY_KEY, List.of(AVAILABLE_SEAT_ID))
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("holdId", notNullValue());
    }

    @Test
    void 같은_요청_키로_다른_좌석_조합을_요청하면_409와_IDEMPOTENCY_KEY_REUSED를_반환한다() {
        holdRequest(StubReservations.REUSED_IDEMPOTENCY_KEY, List.of(AVAILABLE_SEAT_ID))
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(409))
                    .body("code", equalTo("IDEMPOTENCY_KEY_REUSED"))
                    .body("instance", equalTo("/reservations/holds"));
    }

    @Test
    void 이미_선점된_좌석을_선점하면_409와_SEAT_ALREADY_HELD를_반환한다() {
        holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, List.of(HELD_SEAT_ID))
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("SEAT_ALREADY_HELD"));
    }

    @Test
    void 누적_좌석_한도를_넘으면_409와_PURCHASE_LIMIT_EXCEEDED를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                    .header(IDEMPOTENCY_KEY_HEADER, StubReservations.NEW_IDEMPOTENCY_KEY)
                    .body(Map.of(
                            "scheduleId", StubReservations.PURCHASE_LIMIT_SCHEDULE_ID,
                            "seatIds", List.of(AVAILABLE_SEAT_ID)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("PURCHASE_LIMIT_EXCEEDED"));
    }

    @ParameterizedTest(name = "좌석 {0}개")
    @ValueSource(ints = {0, 5})
    void 선점_좌석_수가_1에서_4를_벗어나면_400과_INVALID_REQUEST를_반환한다(int seatCount) {
        List<Long> seatIds = java.util.stream.LongStream.range(0, seatCount)
                .map(offset -> AVAILABLE_SEAT_ID + offset)
                .boxed()
                .toList();

        holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, seatIds)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("seatIds"));
    }

    @Test
    void 선점_좌석_ID가_중복되면_400과_INVALID_REQUEST를_반환한다() {
        holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, List.of(AVAILABLE_SEAT_ID, AVAILABLE_SEAT_ID))
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("seatIds"))
                    .body("errors.reason", org.hamcrest.Matchers.hasItem("좌석 ID는 중복될 수 없습니다."));
    }

    @Test
    void 다른_회차의_좌석을_선점하면_400과_INVALID_REQUEST를_반환한다() {
        holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, List.of(OTHER_SCHEDULE_SEAT_ID))
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("seatIds"))
                    .body("errors[0].reason", org.hamcrest.Matchers.not(emptyString()));
    }

    @Test
    void 상한인_4좌석을_선점하면_201을_반환한다() {
        holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, MAX_ALLOWED_SEAT_IDS)
                .then()
                    .statusCode(201)
                    .body("seatIds.size()", equalTo(MAX_ALLOWED_SEAT_IDS.size()));
    }

    @Test
    void 없는_회차의_좌석_ID로_선점하면_404를_반환한다() {
        holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, List.of(MISSING_SCHEDULE_SEAT_ID))
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 두_INVALID_REQUEST_경로는_같은_응답_형식을_가진다() {
        JsonPath fromValidation = holdRequest(
                StubReservations.NEW_IDEMPOTENCY_KEY, List.of(AVAILABLE_SEAT_ID, AVAILABLE_SEAT_ID))
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .extract().jsonPath();

        JsonPath fromController = holdRequest(
                StubReservations.NEW_IDEMPOTENCY_KEY, List.of(OTHER_SCHEDULE_SEAT_ID))
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .extract().jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(fromValidation.getMap("$").keySet())
                    .isEqualTo(fromController.getMap("$").keySet());
            softly.assertThat(fromValidation.getString("detail"))
                    .isEqualTo(fromController.getString("detail"));
            softly.assertThat(fromValidation.getString("title"))
                    .isEqualTo(fromController.getString("title"));
            softly.assertThat(fromValidation.getString("code"))
                    .isEqualTo(fromController.getString("code"));
            softly.assertThat(fromValidation.getMap("errors[0]").keySet())
                    .isEqualTo(fromController.getMap("errors[0]").keySet());
        });
    }

    @Test
    void 존재하지_않는_좌석을_선점하면_404를_반환한다() {
        holdRequest(StubReservations.NEW_IDEMPOTENCY_KEY, List.of(MISSING_SEAT_ID))
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(404))
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 입장_허용되지_않은_토큰으로_선점하면_403과_QUEUE_NOT_ADMITTED를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.WAITING_TOKEN)
                    .header(IDEMPOTENCY_KEY_HEADER, StubReservations.NEW_IDEMPOTENCY_KEY)
                    .body(holdBody(List.of(AVAILABLE_SEAT_ID)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("QUEUE_NOT_ADMITTED"));
    }

    @Test
    void 없는_회차에_선점하면_404를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                    .header(IDEMPOTENCY_KEY_HEADER, StubReservations.NEW_IDEMPOTENCY_KEY)
                    .body(Map.of(
                            "scheduleId", StubSchedules.MISSING_SCHEDULE_ID,
                            "seatIds", List.of(AVAILABLE_SEAT_ID)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @ParameterizedTest(name = "{0} 누락")
    @ValueSource(strings = {QUEUE_TOKEN_HEADER, IDEMPOTENCY_KEY_HEADER})
    void 선점에_필수_헤더가_없으면_400을_반환한다(String omittedHeader) {
        var request = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(holdBody(List.of(AVAILABLE_SEAT_ID)));

        if (!QUEUE_TOKEN_HEADER.equals(omittedHeader)) {
            request.header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN);
        }
        if (!IDEMPOTENCY_KEY_HEADER.equals(omittedHeader)) {
            request.header(IDEMPOTENCY_KEY_HEADER, StubReservations.NEW_IDEMPOTENCY_KEY);
        }

        request
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 선점은_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                    .header(IDEMPOTENCY_KEY_HEADER, StubReservations.NEW_IDEMPOTENCY_KEY)
                    .body(holdBody(List.of(AVAILABLE_SEAT_ID)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 예매를_생성하면_201과_스펙에_정의된_7개_필드를_반환한다() {
        Map<String, Object> body = createRequest(StubReservations.OWN_HOLD_ID)
                .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_CREATE_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("status")).isEqualTo("PENDING_PAYMENT");
            softly.assertThat(body.get("reservationId"))
                    .isEqualTo((int) StubReservations.PENDING_RESERVATION_ID);
            softly.assertThat(body.get("orderId"))
                    .isEqualTo("reservation-" + StubReservations.PENDING_RESERVATION_ID + "-1");
            softly.assertThat(body.get("amount")).isInstanceOf(Integer.class);
            softly.assertThat((Integer) body.get("amount")).isPositive();
            softly.assertThat(String.valueOf(body.get("expiresAt"))).matches(KST_DATE_TIME_REGEX);
            softly.assertThat(String.valueOf(body.get("originalExpiresAt"))).matches(KST_DATE_TIME_REGEX);
        });
    }

    @ParameterizedTest(name = "{0} 누락")
    @ValueSource(strings = {"scheduleId", "seatIds"})
    void 선점_요청에_필수_필드가_없으면_400과_INVALID_REQUEST를_반환한다(String omittedField) {
        Map<String, Object> body = new java.util.HashMap<>(holdBody(List.of(AVAILABLE_SEAT_ID)));
        body.remove(omittedField);

        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                    .header(IDEMPOTENCY_KEY_HEADER, StubReservations.NEW_IDEMPOTENCY_KEY)
                    .body(body)
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem(omittedField));
    }

    @Test
    void 예매_생성_요청에_holdId가_없으면_400과_INVALID_REQUEST를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(Map.of())
                .when()
                    .post("/reservations")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("holdId"));
    }

    @Test
    void 취소_요청에_status가_없으면_400과_INVALID_REQUEST를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(Map.of())
                .when()
                    .patch("/reservations/{reservationId}", StubReservations.PENDING_RESERVATION_ID)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("status"));
    }

    @Test
    void 같은_선점으로_재요청하면_200을_반환한다() {
        createRequest(StubReservations.REPLAYED_HOLD_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("status", equalTo("CONFIRMED"));
    }

    @Test
    void 다른_사용자의_선점으로_예매를_생성하면_403과_HOLD_NOT_OWNED를_반환한다() {
        createRequest(StubReservations.OTHER_MEMBER_HOLD_ID)
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("HOLD_NOT_OWNED"));
    }

    @Test
    void 이미_취소된_예매의_선점으로_요청하면_409와_RESERVATION_CANCELLED를_반환한다() {
        createRequest(StubReservations.CANCELLED_HOLD_ID)
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("RESERVATION_CANCELLED"));
    }

    @Test
    void 만료된_선점으로_요청하면_410과_HOLD_EXPIRED를_반환한다() {
        createRequest(StubReservations.EXPIRED_HOLD_ID)
                .then()
                    .statusCode(410)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(410))
                    .body("code", equalTo("HOLD_EXPIRED"));
    }

    @Test
    void 없는_선점으로_요청하면_404를_반환한다() {
        createRequest(StubReservations.MISSING_HOLD_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 예매_생성은_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .body(Map.of("holdId", StubReservations.OWN_HOLD_ID))
                .when()
                    .post("/reservations")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 예매_목록은_기본_size_10으로_페이지_응답을_반환한다() {
        JsonPath body = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/reservations")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getMap("$"))
                    .containsOnlyKeys("content", "page", "size", "totalElements", "totalPages");
            softly.assertThat(body.getInt("page")).isZero();
            softly.assertThat(body.getInt("size")).isEqualTo(10);
            softly.assertThat(body.getList("content")).isNotEmpty();
        });
    }

    @Test
    void 예매_목록의_카드는_스펙에_정의된_8개_필드만_가진다() {
        Map<String, Object> card = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/reservations")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getMap("content[0]");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(card).containsOnlyKeys(SPEC_SUMMARY_FIELDS.toArray(String[]::new));
            softly.assertThat(SPEC_RESERVATION_STATUS_NAMES).contains(String.valueOf(card.get("status")));
            softly.assertThat(card.get("seatCount")).isInstanceOf(Integer.class);
        });
    }

    @Test
    void 예매_목록은_다른_사용자의_예매를_포함하지_않는다() {
        List<Integer> ids = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .queryParam("size", 100)
                .when()
                    .get("/reservations")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getList("content.id", Integer.class);

        assertThat(ids).doesNotContain((int) StubReservations.OTHER_MEMBER_RESERVATION_ID);
    }

    @Test
    void 예매_목록의_페이지는_전체_목록의_해당_구간과_같고_순서가_유지된다() {
        List<Integer> all = reservationIds(0, 100);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(all).hasSizeGreaterThanOrEqualTo(3);
            softly.assertThat(all).isSortedAccordingTo(Comparator.reverseOrder());
            softly.assertThat(reservationIds(0, 2)).isEqualTo(all.subList(0, 2));
            softly.assertThat(reservationIds(1, 2)).isEqualTo(all.subList(2, Math.min(4, all.size())));
        });
    }

    @Test
    void 예매_목록의_totalElements와_totalPages는_실제_건수를_반영한다() {
        JsonPath onePage = reservationPage(0, 100);
        int total = onePage.getList("content").size();

        JsonPath split = reservationPage(0, 2);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(onePage.getInt("totalElements")).isEqualTo(total);
            softly.assertThat(onePage.getInt("totalPages")).isEqualTo(1);
            softly.assertThat(split.getInt("totalElements")).isEqualTo(total);
            softly.assertThat(split.getInt("totalPages")).isEqualTo((total + 1) / 2);
            softly.assertThat(split.getList("content")).hasSize(2);
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"page=-1", "size=0", "size=abc"})
    void 예매_목록의_페이지_파라미터가_유효하지_않으면_400을_반환한다(String query) {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/reservations?" + query)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 예매_목록의_size가_상한을_넘으면_400을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .queryParam("size", 101)
                .when()
                    .get("/reservations")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 예매_목록은_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/reservations")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 예매_내역은_스펙에_정의된_9개_필드를_반환한다() {
        Map<String, Object> body = detailOf(StubReservations.CONFIRMED_RESERVATION_ID).getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_DETAIL_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("status")).isEqualTo("CONFIRMED");
            softly.assertThat(body.get("paymentKey")).isNotNull();
            softly.assertThat(body.get("orderId")).isNotNull();
        });
    }

    @Test
    void 예매_내역의_중첩_객체는_스펙에_정의된_필드만_가진다() {
        JsonPath body = detailOf(StubReservations.CONFIRMED_RESERVATION_ID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getMap("concert")).containsOnlyKeys("id", "title", "posterUrl");
            softly.assertThat(body.getMap("schedule")).containsOnlyKeys("id", "startsAt", "venue");
            softly.assertThat(body.getList("seats")).isNotEmpty();
            softly.assertThat(body.getMap("seats[0]"))
                    .containsOnlyKeys(SPEC_DETAIL_SEAT_FIELDS.toArray(String[]::new));
        });
    }

    @Test
    void 예매_내역의_좌석에는_배치도와_달리_status가_없다() {
        Map<String, Object> seat = detailOf(StubReservations.CONFIRMED_RESERVATION_ID).getMap("seats[0]");

        assertThat(seat).doesNotContainKey("status");
    }

    @Test
    void 결제_전_예매_내역의_paymentKey와_orderId는_null이다() {
        JsonPath body = detailOf(StubReservations.PENDING_RESERVATION_ID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getString("status")).isEqualTo("PENDING_PAYMENT");
            softly.assertThat(body.getString("paymentKey")).isNull();
            softly.assertThat(body.getString("orderId")).isNull();
        });
    }

    @Test
    void 예매_내역의_시각_필드는_ISO_8601_KST_오프셋_형식이다() {
        JsonPath body = detailOf(StubReservations.CONFIRMED_RESERVATION_ID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getString("reservedAt")).matches(KST_DATE_TIME_REGEX);
            softly.assertThat(body.getString("schedule.startsAt")).matches(KST_DATE_TIME_REGEX);
        });
    }

    @Test
    void 다른_사용자의_예매_내역을_조회하면_403을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/reservations/{reservationId}", StubReservations.OTHER_MEMBER_RESERVATION_ID)
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(403))
                    .body("code", equalTo("RESERVATION_NOT_OWNED"));
    }

    @Test
    void 없는_예매_내역을_조회하면_404를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/reservations/{reservationId}", StubReservations.MISSING_RESERVATION_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 예매_내역_조회는_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/reservations/{reservationId}", StubReservations.CONFIRMED_RESERVATION_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 예매를_취소하면_200과_스펙에_정의된_3개_필드를_반환한다() {
        Map<String, Object> body = cancelRequest(
                StubReservations.PENDING_RESERVATION_ID, ReservationStatus.CANCELLED.name())
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_CANCEL_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("id")).isEqualTo((int) StubReservations.PENDING_RESERVATION_ID);
            softly.assertThat(body.get("status")).isEqualTo("CANCELLED");
            softly.assertThat(String.valueOf(body.get("cancelledAt"))).matches(KST_DATE_TIME_REGEX);
        });
    }

    @Test
    void 이미_취소된_예매를_다시_취소하면_204와_빈_바디를_반환한다() {
        String body = cancelRequest(
                StubReservations.ALREADY_CANCELLED_RESERVATION_ID, ReservationStatus.CANCELLED.name())
                .then()
                    .statusCode(204)
                .extract().asString();

        assertThat(body).isEmpty();
    }

    @Test
    void 취소할_수_없는_예매를_취소하면_409와_CANCELLATION_CLOSED를_반환한다() {
        cancelRequest(
                StubReservations.CANCELLATION_CLOSED_RESERVATION_ID, ReservationStatus.CANCELLED.name())
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(409))
                    .body("code", equalTo("CANCELLATION_CLOSED"));
    }

    @Test
    void 결제_처리_중인_예매를_취소하면_409와_PAYMENT_IN_PROGRESS를_반환한다() {
        cancelRequest(
                StubReservations.PAYMENT_IN_PROGRESS_RESERVATION_ID, ReservationStatus.CANCELLED.name())
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(409))
                    .body("code", equalTo("PAYMENT_IN_PROGRESS"));
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(strings = {"CONFIRMED", "PENDING_PAYMENT", "EXPIRED"})
    void 취소_요청의_status가_CANCELLED가_아니면_400을_반환한다(String status) {
        cancelRequest(StubReservations.PENDING_RESERVATION_ID, status)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.size()", equalTo(1))
                    .body("errors[0].field", equalTo("status"))
                    .body("errors[0].reason", org.hamcrest.Matchers.not(emptyString()));
    }

    @Test
    void 다른_사용자의_예매를_취소하면_403을_반환한다() {
        cancelRequest(StubReservations.OTHER_MEMBER_RESERVATION_ID, ReservationStatus.CANCELLED.name())
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("RESERVATION_NOT_OWNED"));
    }

    @Test
    void 없는_예매를_취소하면_404를_반환한다() {
        cancelRequest(StubReservations.MISSING_RESERVATION_ID, ReservationStatus.CANCELLED.name())
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 예매_취소는_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .body(Map.of("status", ReservationStatus.CANCELLED.name()))
                .when()
                    .patch("/reservations/{reservationId}", StubReservations.PENDING_RESERVATION_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 예매_상태_ENUM은_스펙에_적힌_4개_리터럴과_정확히_일치한다() {
        List<String> declared = Arrays.stream(ReservationStatus.values()).map(Enum::name).toList();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SPEC_RESERVATION_STATUS_NAMES).hasSize(4);
            softly.assertThat(declared)
                    .hasSize(SPEC_RESERVATION_STATUS_NAMES.size())
                    .containsExactlyInAnyOrderElementsOf(SPEC_RESERVATION_STATUS_NAMES);
        });
    }

    private JsonPath reservationPage(int page, int size) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .queryParam("page", page)
                    .queryParam("size", size)
                .when()
                    .get("/reservations")
                .then()
                    .statusCode(200)
                .extract().jsonPath();
    }

    private List<Integer> reservationIds(int page, int size) {
        return reservationPage(page, size).getList("content.id", Integer.class);
    }

    private Map<String, Object> holdBody(List<Long> seatIds) {
        return Map.of("scheduleId", StubSchedules.OPEN_SCHEDULE_ID, "seatIds", seatIds);
    }

    private io.restassured.response.Response holdRequest(String idempotencyKey, List<Long> seatIds) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(holdBody(seatIds))
                .when()
                    .post("/reservations/holds");
    }

    private io.restassured.response.Response createRequest(String holdId) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(Map.of("holdId", holdId))
                .when()
                    .post("/reservations");
    }

    private io.restassured.response.Response cancelRequest(long reservationId, String status) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(Map.of("status", status))
                .when()
                    .patch("/reservations/{reservationId}", reservationId);
    }

    private JsonPath detailOf(long reservationId) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/reservations/{reservationId}", reservationId)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath();
    }
}
