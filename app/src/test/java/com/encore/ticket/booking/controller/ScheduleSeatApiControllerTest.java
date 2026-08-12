package com.encore.ticket.booking.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.dto.SeatStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.springframework.http.HttpHeaders;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class ScheduleSeatApiControllerTest extends ApiSpecTestSupport {

    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    private static final List<String> SPEC_SEAT_FIELDS = List.of(
            "id", "section", "row", "number", "grade", "price", "status");

    private static final List<String> SPEC_SEAT_STATUS_NAMES =
            List.of("AVAILABLE", "HELD", "RESERVED");

    private static final List<String> SEAT_STATUS_NAMES = Arrays.stream(SeatStatus.values())
            .map(Enum::name)
            .toList();

    @Test
    void 좌석_배치도를_조회하면_200과_스펙에_정의된_2개_필드를_반환한다() {
        Map<String, Object> body = admittedSeatMap().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys("scheduleId", "seats");
            softly.assertThat(body.get("scheduleId")).isEqualTo((int) StubSchedules.OPEN_SCHEDULE_ID);
        });
    }

    @Test
    void 좌석은_스펙에_정의된_7개_필드만_가진다() {
        JsonPath body = admittedSeatMap();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getList("seats")).isNotEmpty();
            softly.assertThat(body.getMap("seats[0]"))
                    .containsOnlyKeys(SPEC_SEAT_FIELDS.toArray(String[]::new));
        });
    }

    @Test
    void 좌석_상태_ENUM은_스펙에_적힌_3개_리터럴과_정확히_일치한다() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SPEC_SEAT_STATUS_NAMES).hasSize(3);
            softly.assertThat(SEAT_STATUS_NAMES)
                    .hasSize(SPEC_SEAT_STATUS_NAMES.size())
                    .containsExactlyInAnyOrderElementsOf(SPEC_SEAT_STATUS_NAMES);
        });
    }

    @Test
    void 모든_좌석의_status는_스펙에_정의된_3개_값_중_하나다() {
        List<String> statuses = admittedSeatMap().getList("seats.status", String.class);

        assertThat(statuses)
                .isNotEmpty()
                .allSatisfy(status -> assertThat(SPEC_SEAT_STATUS_NAMES).contains(status));
    }

    @Test
    void 좌석의_id와_price는_문자열이_아닌_JSON_number다() {
        Map<String, Object> seat = admittedSeatMap().getMap("seats[0]");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(seat.get("id")).isInstanceOf(Integer.class);
            softly.assertThat(seat.get("price")).isInstanceOf(Integer.class);
            softly.assertThat(seat.get("section")).isInstanceOf(String.class);
            softly.assertThat(seat.get("row")).isInstanceOf(String.class);
            softly.assertThat(seat.get("number")).isInstanceOf(String.class);
        });
    }

    @Test
    void 좌석_ID는_회차마다_다르다() {
        long firstSeatId = admittedSeatMap().getLong("seats[0].id");

        long otherSeatId = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.OPEN_SCHEDULE_ID + 1)
                .then()
                    .statusCode(200)
                .extract().jsonPath().getLong("seats[0].id");

        assertThat(firstSeatId).isNotEqualTo(otherSeatId);
    }

    @Test
    void 입장_허용되지_않은_토큰으로_조회하면_403과_QUEUE_NOT_ADMITTED를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.WAITING_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(403))
                    .body("code", equalTo("QUEUE_NOT_ADMITTED"))
                    .body("detail", equalTo("입장이 허용된 대기열 토큰이 아닙니다."))
                    .body("instance", equalTo("/schedules/" + StubSchedules.OPEN_SCHEDULE_ID + "/seats"));
    }

    @Test
    void 없는_토큰으로_조회하면_403을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.UNKNOWN_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("QUEUE_NOT_ADMITTED"));
    }

    @Test
    void X_Queue_Token_헤더가_없으면_400을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 없는_회차의_좌석을_조회하면_404를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.MISSING_SCHEDULE_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(404))
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 좌석_조회는_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    private JsonPath admittedSeatMap() {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                .when()
                    .get("/schedules/{scheduleId}/seats", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath();
    }
}
