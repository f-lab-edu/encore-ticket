package com.encore.ticket.booking.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.hold.domain.SeatHold;
import com.encore.ticket.core.booking.hold.port.SeatHoldAcquireResult;
import com.encore.ticket.core.booking.hold.port.SeatHoldRepository;

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
import org.springframework.test.context.jdbc.Sql;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Sql(scripts = "/sql/seat-hold-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SeatHoldApiControllerTest extends ApiSpecTestSupport {

    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final long SCHEDULE_ID = 930L;
    private static final long OTHER_SCHEDULE_ID = 931L;
    private static final long MISSING_SCHEDULE_ID = 9999L;

    private static final long SEAT_1 = 9301L;
    private static final long SEAT_2 = 9302L;
    private static final long SEAT_3 = 9303L;
    private static final long SEAT_4 = 9304L;
    private static final long SEAT_5 = 9305L;
    private static final long OTHER_SCHEDULE_SEAT = 9311L;
    private static final long MISSING_SEAT = 9999L;

    private static final long SEAT_PRICE = 150_000L;
    private static final long OTHER_MEMBER_ID = 2L;

    private static final List<String> SPEC_HOLD_FIELDS =
            List.of("holdId", "scheduleId", "seatIds", "totalAmount", "expiresAt");

    @Autowired
    SeatHoldRepository seatHoldRepository;

    @Test
    void 좌석을_선점하면_201과_스펙에_정의된_5개_필드를_반환한다() {
        Map<String, Object> body = hold("idem-1", List.of(SEAT_1))
                .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_HOLD_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("holdId")).isInstanceOf(String.class);
            softly.assertThat(body.get("scheduleId")).isEqualTo((int) SCHEDULE_ID);
            softly.assertThat(body.get("seatIds")).isEqualTo(List.of((int) SEAT_1));
        });
    }

    @Test
    void 선점_응답의_totalAmount는_좌석_가격의_합이다() {
        int totalAmount = hold("idem-1", List.of(SEAT_1, SEAT_2))
                .then().statusCode(201)
                .extract().jsonPath().getInt("totalAmount");

        assertThat((long) totalAmount).isEqualTo(SEAT_PRICE * 2);
    }

    @Test
    void 선점_만료_시각은_ISO_8601_KST_오프셋_형식이다() {
        String expiresAt = hold("idem-1", List.of(SEAT_1))
                .then().statusCode(201)
                .extract().jsonPath().getString("expiresAt");

        assertThat(expiresAt).matches(KST_DATE_TIME_REGEX);
    }

    @Test
    void 같은_요청_키로_같은_좌석을_다시_요청하면_200과_같은_선점을_반환한다() {
        JsonPath first = hold("idem-1", List.of(SEAT_1, SEAT_2))
                .then().statusCode(201)
                .extract().jsonPath();

        JsonPath replayed = hold("idem-1", List.of(SEAT_1, SEAT_2))
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("holdId", notNullValue())
                .extract().jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(replayed.getString("holdId"))
                    .isEqualTo(first.getString("holdId"));
            softly.assertThat(replayed.getString("expiresAt"))
                    .isEqualTo(first.getString("expiresAt"));
        });
    }

    @Test
    void 좌석_순서만_다른_재요청도_같은_선점으로_본다() {
        String holdId = hold("idem-1", List.of(SEAT_1, SEAT_2))
                .then().statusCode(201)
                .extract().jsonPath().getString("holdId");

        hold("idem-1", List.of(SEAT_2, SEAT_1))
                .then()
                    .statusCode(200)
                    .body("holdId", equalTo(holdId));
    }

    @Test
    void 같은_요청_키로_다른_좌석_조합을_요청하면_409와_IDEMPOTENCY_KEY_REUSED를_반환한다() {
        hold("idem-1", List.of(SEAT_1)).then().statusCode(201);

        hold("idem-1", List.of(SEAT_2))
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(409))
                    .body("code", equalTo("IDEMPOTENCY_KEY_REUSED"))
                    .body("instance", equalTo("/reservations/holds"));
    }

    @Test
    void 다른_사용자가_선점한_좌석이면_409와_SEAT_ALREADY_HELD를_반환한다() {
        givenHeldByOtherMember(SEAT_1);

        hold("idem-1", List.of(SEAT_1))
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("SEAT_ALREADY_HELD"));
    }

    @Test
    void 한_좌석이라도_겹치면_나머지_좌석도_선점되지_않는다() {
        givenHeldByOtherMember(SEAT_2);

        hold("idem-1", List.of(SEAT_1, SEAT_2, SEAT_3))
                .then()
                    .statusCode(409)
                    .body("code", equalTo("SEAT_ALREADY_HELD"));

        assertThat(seatHoldRepository.holdExpiryBySeatId(SCHEDULE_ID))
                .containsOnlyKeys(SEAT_2);
    }

    @Test
    void 상한인_4좌석을_선점하면_201을_반환한다() {
        hold("idem-1", List.of(SEAT_1, SEAT_2, SEAT_3, SEAT_4))
                .then()
                    .statusCode(201)
                    .body("seatIds.size()", equalTo(4));
    }

    @Test
    void 누적_좌석_한도를_넘으면_409와_PURCHASE_LIMIT_EXCEEDED를_반환한다() {
        hold("idem-1", List.of(SEAT_1, SEAT_2, SEAT_3, SEAT_4)).then().statusCode(201);

        hold("idem-2", List.of(SEAT_5))
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("PURCHASE_LIMIT_EXCEEDED"));
    }

    @ParameterizedTest(name = "좌석 {0}개")
    @ValueSource(ints = {0, 5})
    void 선점_좌석_수가_1에서_4를_벗어나면_400과_INVALID_REQUEST를_반환한다(int seatCount) {
        List<Long> seatIds = java.util.stream.LongStream.range(0, seatCount)
                .map(offset -> SEAT_1 + offset)
                .boxed()
                .toList();

        hold("idem-1", seatIds)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("seatIds"));
    }

    @Test
    void 선점_좌석_ID가_중복되면_400과_INVALID_REQUEST를_반환한다() {
        hold("idem-1", List.of(SEAT_1, SEAT_1))
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("seatIds"))
                    .body("errors.reason", org.hamcrest.Matchers.hasItem("좌석 ID는 중복될 수 없습니다."));
    }

    @Test
    void 다른_회차의_좌석을_선점하면_400과_INVALID_REQUEST를_반환한다() {
        hold("idem-1", List.of(OTHER_SCHEDULE_SEAT))
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", org.hamcrest.Matchers.hasItem("seatIds"))
                    .body("errors[0].reason", org.hamcrest.Matchers.not(emptyString()));
    }

    @Test
    void 두_INVALID_REQUEST_경로는_같은_응답_형식을_가진다() {
        JsonPath fromValidation = hold("idem-1", List.of(SEAT_1, SEAT_1))
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .extract().jsonPath();

        JsonPath fromService = hold("idem-2", List.of(OTHER_SCHEDULE_SEAT))
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .extract().jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(fromValidation.getMap("$").keySet())
                    .isEqualTo(fromService.getMap("$").keySet());
            softly.assertThat(fromValidation.getString("detail"))
                    .isEqualTo(fromService.getString("detail"));
            softly.assertThat(fromValidation.getString("title"))
                    .isEqualTo(fromService.getString("title"));
            softly.assertThat(fromValidation.getString("code"))
                    .isEqualTo(fromService.getString("code"));
            softly.assertThat(fromValidation.getMap("errors[0]").keySet())
                    .isEqualTo(fromService.getMap("errors[0]").keySet());
        });
    }

    @Test
    void 존재하지_않는_좌석을_선점하면_404를_반환한다() {
        hold("idem-1", List.of(MISSING_SEAT))
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(404))
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 없는_회차에_선점하면_404를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, "q_not_checked")
                    .header(IDEMPOTENCY_KEY_HEADER, "idem-1")
                    .body(Map.of("scheduleId", MISSING_SCHEDULE_ID, "seatIds", List.of(SEAT_1)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 입장_허용되지_않은_토큰으로_선점하면_403과_QUEUE_NOT_ADMITTED를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, "q_waiting")
                    .header(IDEMPOTENCY_KEY_HEADER, "idem-1")
                    .body(holdBody(List.of(SEAT_1)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("QUEUE_NOT_ADMITTED"));
    }

    @Test
    void Queue_authorization_뒤_좌석_선점이_실패해도_lease는_갱신된다() {
        givenHeldByOtherMember(SEAT_1);
        String queueToken = admittedQueueToken(
                SCHEDULE_ID, 1L, OffsetDateTime.now(clock).minusMinutes(1));
        long before = admittedUntil(SCHEDULE_ID, queueToken);

        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, queueToken)
                    .header(IDEMPOTENCY_KEY_HEADER, "idem-1")
                    .body(holdBody(List.of(SEAT_1)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(409)
                    .body("code", equalTo("SEAT_ALREADY_HELD"));

        assertThat(admittedUntil(SCHEDULE_ID, queueToken)).isGreaterThan(before);
    }

    @ParameterizedTest(name = "{0} 누락")
    @ValueSource(strings = {QUEUE_TOKEN_HEADER, IDEMPOTENCY_KEY_HEADER})
    void 선점에_필수_헤더가_없으면_400을_반환한다(String omittedHeader) {
        var request = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(holdBody(List.of(SEAT_1)));

        if (!QUEUE_TOKEN_HEADER.equals(omittedHeader)) {
            request.header(QUEUE_TOKEN_HEADER, admittedQueueToken(SCHEDULE_ID));
        }
        if (!IDEMPOTENCY_KEY_HEADER.equals(omittedHeader)) {
            request.header(IDEMPOTENCY_KEY_HEADER, "idem-1");
        }

        request
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @ParameterizedTest(name = "{0} 누락")
    @ValueSource(strings = {"scheduleId", "seatIds"})
    void 선점_요청에_필수_필드가_없으면_400과_INVALID_REQUEST를_반환한다(String omittedField) {
        Map<String, Object> body = new HashMap<>(holdBody(List.of(SEAT_1)));
        body.remove(omittedField);

        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, admittedQueueToken(SCHEDULE_ID))
                    .header(IDEMPOTENCY_KEY_HEADER, "idem-1")
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
    void 선점은_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .header(QUEUE_TOKEN_HEADER, "q_not_checked")
                    .header(IDEMPOTENCY_KEY_HEADER, "idem-1")
                    .body(holdBody(List.of(SEAT_1)))
                .when()
                    .post("/reservations/holds")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    private Map<String, Object> holdBody(List<Long> seatIds) {
        return Map.of("scheduleId", SCHEDULE_ID, "seatIds", seatIds);
    }

    private Response hold(String idempotencyKey, List<Long> seatIds) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, admittedQueueToken(SCHEDULE_ID))
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(Map.of("scheduleId", SCHEDULE_ID, "seatIds", seatIds))
                .when()
                    .post("/reservations/holds");
    }

    private void givenHeldByOtherMember(long seatId) {
        SeatHold hold = SeatHold.hold(SCHEDULE_ID, List.of(seatId), OTHER_MEMBER_ID, clock);
        assertThat(seatHoldRepository.acquire(
                hold, 4, "idem-other-" + seatId, "fp-other-" + seatId).result())
                .isEqualTo(SeatHoldAcquireResult.ACQUIRED);
    }

    private long admittedUntil(long scheduleId, String queueToken) {
        Object value = redisTemplate.opsForHash().get(
                "queue:{%d}:token:%s".formatted(scheduleId, queueToken), "admittedUntil");
        return Long.parseLong(String.valueOf(value));
    }
}
