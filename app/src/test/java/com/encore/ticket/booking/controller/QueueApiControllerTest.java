package com.encore.ticket.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

@TestPropertySource(properties = "ticket.queue.admission.scheduler-interval=1h")
class QueueApiControllerTest extends ApiSpecTestSupport {

    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";
    private static final long AUTHENTICATED_MEMBER_ID = 1L;
    private static final int MAX_LAPSES = 2;

    private static final List<String> SPEC_TOKEN_FIELDS = List.of(
            "queueToken", "scheduleId", "status", "position",
            "estimatedWaitSeconds", "pollAfterSeconds", "resumed", "lapsesRemaining");

    private static final List<String> SPEC_STATUS_FIELDS = List.of(
            "status", "position", "estimatedWaitSeconds", "pollAfterSeconds", "admittedUntil");

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    QueueRepository queueRepository;

    @Autowired
    QueueAdmissionPolicy admissionPolicy;

    @BeforeEach
    void flushQueue() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void 실제_대기열에_진입하면_WAITING_토큰을_반환한다() {
        Map<String, Object> body = enter();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_TOKEN_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("scheduleId")).isEqualTo((int) StubSchedules.OPEN_SCHEDULE_ID);
            softly.assertThat(body.get("status")).isEqualTo("WAITING");
            softly.assertThat(body.get("queueToken")).isInstanceOf(String.class);
            softly.assertThat(body.get("position")).isEqualTo(1);
            softly.assertThat(body.get("resumed")).isEqualTo(false);
            softly.assertThat((Integer) body.get("lapsesRemaining")).isBetween(0, MAX_LAPSES);
        });
    }

    @Test
    void 같은_회원이_다시_진입하면_기존_토큰을_반환한다() {
        Map<String, Object> first = enter();
        Map<String, Object> resumed = enter();

        assertThat(resumed.get("queueToken")).isEqualTo(first.get("queueToken"));
        assertThat(resumed.get("resumed")).isEqualTo(true);
    }

    @Test
    void 대기열_진입은_인증이_필요하다() {
        RestAssured.given().spec(spec)
                .when().post("/queue/{scheduleId}/tokens", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(401).contentType(PROBLEM_JSON)
                .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 없는_회차에_진입하면_404를_반환한다() {
        RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().post("/queue/{scheduleId}/tokens", StubSchedules.MISSING_SCHEDULE_ID)
                .then().statusCode(404).contentType(PROBLEM_JSON)
                .body("status", equalTo(404))
                .body("code", equalTo("NOT_FOUND"))
                .body("instance", equalTo(
                        "/queue/" + StubSchedules.MISSING_SCHEDULE_ID + "/tokens"));
    }

    @Test
    void 예매_기간이_아닌_회차에_진입하면_409를_반환한다() {
        RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().post("/queue/{scheduleId}/tokens", StubSchedules.BOOKING_CLOSED_SCHEDULE_ID)
                .then().statusCode(409).contentType(PROBLEM_JSON)
                .body("status", equalTo(409))
                .body("code", equalTo("CONFLICT"));
    }

    @Test
    void 실제_토큰을_polling하면_WAITING_형식을_반환한다() {
        String token = enterToken();

        Map<String, Object> body = statusBodyOf(token);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_STATUS_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("status")).isEqualTo("WAITING");
            softly.assertThat(body.get("position")).isEqualTo(1);
            softly.assertThat(body.get("estimatedWaitSeconds")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("pollAfterSeconds")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("admittedUntil")).isNull();
        });
    }

    @Test
    void scheduler_Admission_후_같은_토큰은_ADMITTED_형식을_반환한다() {
        String token = enterToken();
        queueRepository.admit(OffsetDateTime.now(ZoneOffset.UTC), admissionPolicy);

        Map<String, Object> body = statusBodyOf(token);

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
    void ADMITTED의_admittedUntil은_ISO_8601_KST_오프셋_형식이다() {
        String token = enterToken();
        queueRepository.admit(OffsetDateTime.now(ZoneOffset.UTC), admissionPolicy);

        JsonPath body = statusResponse(token).extract().jsonPath();

        assertThat(body.getString("admittedUntil")).matches(KST_DATE_TIME_REGEX);
    }

    @Test
    void 최초_lease가_만료된_ADMITTED_토큰은_410을_반환한다() {
        OffsetDateTime sixMinutesAgo = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(6);
        QueueEnterResult entered = queueRepository.enterOrResume(
                StubSchedules.OPEN_SCHEDULE_ID, AUTHENTICATED_MEMBER_ID, sixMinutesAgo);
        queueRepository.admit(sixMinutesAgo, admissionPolicy);

        RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .header(QUEUE_TOKEN_HEADER, entered.token().token())
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(410).contentType(PROBLEM_JSON)
                .body("status", equalTo(410))
                .body("code", equalTo("QUEUE_TOKEN_EXPIRED"));
    }

    @Test
    void 상태_조회에_Queue_Token_헤더가_없으면_400을_반환한다() {
        RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 없는_토큰으로_상태를_조회하면_404를_반환한다() {
        RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .header(QUEUE_TOKEN_HEADER, "q_unknown")
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(404).contentType(PROBLEM_JSON)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 다른_사용자의_실제_토큰으로_상태를_조회하면_403을_반환한다() {
        QueueEnterResult other = queueRepository.enterOrResume(
                StubSchedules.OPEN_SCHEDULE_ID, 999L, OffsetDateTime.now(ZoneOffset.UTC));

        RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .header(QUEUE_TOKEN_HEADER, other.token().token())
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(403).contentType(PROBLEM_JSON)
                .body("status", equalTo(403))
                .body("code", equalTo("QUEUE_TOKEN_NOT_OWNED"));
    }

    @Test
    void 없는_회차의_상태를_조회하면_404를_반환한다() {
        RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .header(QUEUE_TOKEN_HEADER, "q_unknown")
                .when().get("/queue/{scheduleId}/status", StubSchedules.MISSING_SCHEDULE_ID)
                .then().statusCode(404).contentType(PROBLEM_JSON)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 상태_조회는_인증이_필요하다() {
        RestAssured.given().spec(spec).header(QUEUE_TOKEN_HEADER, "q_unknown")
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(401).contentType(PROBLEM_JSON)
                .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 상태_조회는_인증이_없으면_헤더_누락보다_401을_먼저_반환한다() {
        RestAssured.given().spec(spec)
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(401).contentType(PROBLEM_JSON)
                .body("code", equalTo("UNAUTHORIZED"))
                .body("detail", equalTo("인증이 필요합니다."))
                .body("instance", equalTo(
                        "/queue/" + StubSchedules.OPEN_SCHEDULE_ID + "/status"));
    }

    private Map<String, Object> enter() {
        return RestAssured.given().spec(spec).header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().post("/queue/{scheduleId}/tokens", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(200).contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");
    }

    private String enterToken() {
        return String.valueOf(enter().get("queueToken"));
    }

    private Map<String, Object> statusBodyOf(String queueToken) {
        return statusResponse(queueToken).extract().jsonPath().getMap("$");
    }

    private io.restassured.response.ValidatableResponse statusResponse(String queueToken) {
        return RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .header(QUEUE_TOKEN_HEADER, queueToken)
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(200).contentType(ContentType.JSON);
    }
}
