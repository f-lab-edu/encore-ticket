package com.encore.ticket.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import com.encore.ticket.ApiSpecTestSupport;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;

@TestPropertySource(properties = "ticket.queue.admission.scheduler-interval=20ms")
class QueueAdmissionSchedulerApiTest extends ApiSpecTestSupport {

    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void flushQueue() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void production_scheduler가_WAITING을_ADMITTED로_전환한다() throws InterruptedException {
        String queueToken = RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when().post("/queue/{scheduleId}/tokens", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(200)
                .extract().jsonPath().getString("queueToken");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonPath body = status(queueToken);
            if ("ADMITTED".equals(body.getString("status"))) {
                assertThat(body.getString("admittedUntil")).isNotBlank();
                return;
            }
            Thread.sleep(20);
        }

        fail("scheduler가 제한 시간 안에 WAITING token을 ADMITTED로 전환하지 못했습니다.");
    }

    private JsonPath status(String queueToken) {
        return RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .header(QUEUE_TOKEN_HEADER, queueToken)
                .when().get("/queue/{scheduleId}/status", StubSchedules.OPEN_SCHEDULE_ID)
                .then().statusCode(200)
                .extract().jsonPath();
    }
}
