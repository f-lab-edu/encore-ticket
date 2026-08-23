package com.encore.ticket;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.port.QueueEnterResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;
import com.encore.ticket.support.ContainersConfig;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainersConfig.class)
public abstract class ApiSpecTestSupport {

    protected static final String PROBLEM_JSON = "application/problem+json";

    protected static final String BEARER_TOKEN = "Bearer test-token";

    protected static final String KST_OFFSET = "+09:00";

    protected static final String KST_DATE_TIME_REGEX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}" + Pattern.quote(KST_OFFSET);

    @LocalServerPort
    protected int port;

    protected RequestSpecification spec;

    @Autowired
    QueueRepository queueRepository;

    @Autowired
    QueueAdmissionPolicy queueAdmissionPolicy;

    @Autowired
    protected Clock clock;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeAll
    static void registerProblemJsonParser() {
        RestAssured.registerParser(PROBLEM_JSON, Parser.JSON);
    }

    @BeforeEach
    void setUpSpec() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        spec = new RequestSpecBuilder()
                .setPort(port)
                .setContentType(ContentType.JSON)
                .addFilter(new RequestLoggingFilter(LogDetail.ALL))
                .addFilter(new ResponseLoggingFilter(LogDetail.ALL))
                .build();
    }

    protected String admittedQueueToken(long scheduleId) {
        return admittedQueueToken(scheduleId, 1L);
    }

    protected String admittedQueueToken(long scheduleId, long memberId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return admittedQueueToken(scheduleId, memberId, now);
    }

    protected String admittedQueueToken(long scheduleId, long memberId, OffsetDateTime admittedAt) {
        QueueEnterResult entered = queueRepository.enterOrResume(scheduleId, memberId, admittedAt);
        if (!entered.token().isAdmitted()) {
            queueRepository.admit(admittedAt, queueAdmissionPolicy);
        }
        return queueRepository.findByToken(scheduleId, entered.token().token())
                .filter(token -> token.isAdmitted() && !token.isAdmissionExpired(clock))
                .orElseThrow(() -> new IllegalStateException("통합 테스트용 ADMITTED 토큰 생성 실패"))
                .token();
    }

    protected String expiredQueueToken(long scheduleId) {
        OffsetDateTime admittedAt = OffsetDateTime.now(clock).minusMinutes(6);
        QueueEnterResult entered = queueRepository.enterOrResume(scheduleId, 1L, admittedAt);
        queueRepository.admit(admittedAt, queueAdmissionPolicy);
        return entered.token().token();
    }
}
