package com.encore.ticket.booking.controller;

import com.encore.ticket.ApiSpecTestSupport;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class QueueApiControllerTest extends ApiSpecTestSupport {

    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    private static final int MAX_LAPSES = 2;

    private static final List<String> SPEC_TOKEN_FIELDS = List.of(
            "queueToken", "scheduleId", "status", "position",
            "estimatedWaitSeconds", "pollAfterSeconds", "resumed", "lapsesRemaining");

    private static final List<String> SPEC_STATUS_FIELDS = List.of(
            "status", "position", "estimatedWaitSeconds", "pollAfterSeconds", "admittedUntil");

    @Test
    void 대기열에_진입하면_200과_스펙에_정의된_8개_필드를_반환한다() {
        Map<String, Object> body = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .post("/queue/{scheduleId}/tokens", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_TOKEN_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("scheduleId")).isEqualTo((int) StubSchedules.OPEN_SCHEDULE_ID);
            softly.assertThat(body.get("status")).isEqualTo("WAITING");
            softly.assertThat(body.get("queueToken")).isInstanceOf(String.class);
            softly.assertThat(body.get("position")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("resumed")).isInstanceOf(Boolean.class);
            softly.assertThat(body.get("lapsesRemaining")).isInstanceOf(Integer.class);
            softly.assertThat((Integer) body.get("lapsesRemaining")).isBetween(0, MAX_LAPSES);
        });
    }

    @Test
    void 대기열_진입은_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .post("/queue/{scheduleId}/tokens", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 없는_회차에_진입하면_404와_NOT_FOUND를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .post("/queue/{scheduleId}/tokens", StubSchedules.MISSING_SCHEDULE_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(404))
                    .body("code", equalTo("NOT_FOUND"))
                    .body("instance", equalTo("/queue/" + StubSchedules.MISSING_SCHEDULE_ID + "/tokens"));
    }

    @Test
    void 예매_기간이_아닌_회차에_진입하면_409를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .post("/queue/{scheduleId}/tokens", StubSchedules.BOOKING_CLOSED_SCHEDULE_ID)
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(409))
                    .body("code", equalTo("CONFLICT"));
    }

    @Test
    void 대기_중_토큰의_상태를_조회하면_WAITING_형식을_반환한다() {
        Map<String, Object> body = statusBodyOf(StubQueue.WAITING_TOKEN);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_STATUS_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("status")).isEqualTo("WAITING");
            softly.assertThat(body.get("position")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("estimatedWaitSeconds")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("pollAfterSeconds")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("admittedUntil")).isNull();
        });
    }

    @Test
    void 입장_허용_토큰의_상태를_조회하면_ADMITTED_형식을_반환한다() {
        Map<String, Object> body = statusBodyOf(StubQueue.ADMITTED_TOKEN);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_STATUS_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("status")).isEqualTo("ADMITTED");
            softly.assertThat(body.get("position")).isEqualTo(0);
            softly.assertThat(body.get("admittedUntil")).isNotNull();
            softly.assertThat(body.get("estimatedWaitSeconds")).isNull();
            softly.assertThat(body.get("pollAfterSeconds")).isNull();
        });
    }

    @Test
    void 두_대기열_상태_응답은_같은_키_집합을_가진다() {
        Map<String, Object> waiting = statusBodyOf(StubQueue.WAITING_TOKEN);
        Map<String, Object> admitted = statusBodyOf(StubQueue.ADMITTED_TOKEN);

        assertThat(waiting.keySet()).isEqualTo(admitted.keySet());
    }

    @Test
    void ADMITTED의_admittedUntil은_ISO_8601_KST_오프셋_형식이다() {
        JsonPath body = RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.ADMITTED_TOKEN)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        assertThat(body.getString("admittedUntil")).matches(KST_DATE_TIME_REGEX);
    }

    @Test
    void 상태_조회에_X_Queue_Token_헤더가_없으면_400을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 없는_토큰으로_상태를_조회하면_404를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.UNKNOWN_TOKEN)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 만료된_토큰으로_상태를_조회하면_410과_QUEUE_TOKEN_EXPIRED를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.EXPIRED_TOKEN)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(410)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(410))
                    .body("code", equalTo("QUEUE_TOKEN_EXPIRED"));
    }

    @Test
    void 없는_회차의_상태를_조회하면_404를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.WAITING_TOKEN)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.MISSING_SCHEDULE_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 상태_조회는_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .header(QUEUE_TOKEN_HEADER, StubQueue.WAITING_TOKEN)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 상태_조회는_인증이_없으면_헤더_누락보다_401을_먼저_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"))
                    .body("detail", equalTo("인증이 필요합니다."))
                    .body("instance", equalTo("/queue/" + StubSchedules.OPEN_SCHEDULE_ID + "/status"));
    }

    private Map<String, Object> statusBodyOf(String queueToken) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .header(QUEUE_TOKEN_HEADER, queueToken)
                .when()
                    .get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");
    }
}
