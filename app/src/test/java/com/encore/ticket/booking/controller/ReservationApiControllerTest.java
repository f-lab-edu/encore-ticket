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
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class ReservationApiControllerTest extends ApiSpecTestSupport {

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
    void 예매_생성은_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .body(Map.of("holdId", "missing-hold"))
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
