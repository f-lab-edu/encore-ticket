package com.encore.ticket.payment;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.booking.reservation.port.ReservationRepository;
import com.encore.ticket.core.payment.application.PaymentService;
import com.encore.ticket.core.payment.port.PaymentRepository;
import com.encore.ticket.core.payment.port.PaymentStartCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class PaymentRecoveryIntegrationTest extends ApiSpecTestSupport {

    private static final long RESERVATION_ID = 990501L;
    private static final String ORDER = "reservation-990501-1";
    private static final String KEY = "recovery-integration-key";
    private static final long AMOUNT = 30_000L;
    private static final ExecutorService PG_THREADS = Executors.newCachedThreadPool();
    private static final HttpServer PG = startPg();

    @Autowired DataSource dataSource;
    @Autowired PaymentRepository payments;
    @Autowired PaymentService service;
    @Autowired ReservationRepository reservations;

    // 배경 실행을 막고, 테스트에서 실제 스케줄러를 명시적으로 호출한다.
    @MockitoBean PaymentRecoveryScheduler backgroundRecovery;

    private final AtomicReference<String> pgStatus = new AtomicReference<>("IN_PROGRESS");
    private final AtomicInteger approvalCalls = new AtomicInteger();
    private final AtomicInteger cancellationCalls = new AtomicInteger();
    private final AtomicReference<String> cancellationError = new AtomicReference<>("PROVIDER_ERROR");
    private final ConcurrentLinkedQueue<String> idempotencyKeys = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> approvalBodies = new ConcurrentLinkedQueue<>();
    private volatile CountDownLatch queries = new CountDownLatch(1);
    private volatile CountDownLatch approvals = new CountDownLatch(1);

    @DynamicPropertySource
    static void pgProperties(DynamicPropertyRegistry registry) {
        registry.add("ticket.payment.toss.base-url", () -> "http://127.0.0.1:" + PG.getAddress().getPort());
        registry.add("ticket.payment.toss.secret-key", () -> "test-integration-key");
        registry.add("ticket.payment.toss.read-timeout", () -> "10s");
    }

    @BeforeEach
    void seedPayment() throws Exception {
        cleanRows();
        PG.createContext("/v1/payments/" + KEY + "/cancel", exchange -> {
            cancellationCalls.incrementAndGet();
            sendJson(exchange, 400, "{\"code\":\"" + cancellationError.get()
                    + "\",\"message\":\"test cancellation error\"}");
        });
        PG.createContext("/v1/payments/" + KEY, exchange -> {
            awaitBoth(queries);
            respond(exchange, pgStatus.get());
        });
        PG.createContext("/v1/payments/confirm", exchange -> {
            approvalCalls.incrementAndGet();
            idempotencyKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            approvalBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            awaitBoth(approvals);
            respond(exchange, "DONE");
        });
        execute("""
                INSERT INTO reservation (id, member_id, schedule_id, hold_id, amount, status,
                    reserved_at, performance_starts_at, original_expires_at, expires_at, payment_attempt_no)
                VALUES (990501, 1, 990501, 'recovery-hold', 30000, 'PENDING_PAYMENT',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL 1 DAY,
                    CURRENT_TIMESTAMP + INTERVAL 10 MINUTE, CURRENT_TIMESTAMP + INTERVAL 10 MINUTE, 1)
                """);
        execute("INSERT INTO seat_assignment (seat_id, reservation_id, schedule_id) VALUES (990501, 990501, 990501)");
        payments.start(new PaymentStartCommand(KEY, ORDER, AMOUNT, 1L));
        execute("UPDATE payment SET created_at = CURRENT_TIMESTAMP - INTERVAL 2 MINUTE WHERE reservation_id = 990501");
    }

    @AfterEach
    void clean() throws Exception {
        PG.removeContext("/v1/payments/" + KEY + "/cancel");
        PG.removeContext("/v1/payments/" + KEY);
        PG.removeContext("/v1/payments/confirm");
        cleanRows();
    }

    @AfterAll
    static void stopPg() {
        PG.stop(0);
        PG_THREADS.shutdownNow();
    }

    @Test
    void GET과_스케줄러가_동시에_재승인해도_같은_멱등키와_하나의_DB_완료로_수렴한다() throws Exception {
        queries = new CountDownLatch(2);
        approvals = new CountDownLatch(2);
        PaymentRecoveryScheduler scheduler = new PaymentRecoveryScheduler(
                service, clock, java.time.Duration.ofSeconds(70), 20);

        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            var get = callers.submit(() -> result("COMPLETED"));
            var recovery = callers.submit(scheduler::recover);
            get.get(20, TimeUnit.SECONDS);
            recovery.get(20, TimeUnit.SECONDS);
        }

        assertThat(approvalCalls.get()).isEqualTo(2);
        assertThat(idempotencyKeys).containsExactly(KEY, KEY);
        assertThat(approvalBodies).allSatisfy(body -> assertThat(body)
                .contains("\"paymentKey\":\"" + KEY + "\"", "\"orderId\":\"" + ORDER + "\"", "\"amount\":30000"));
        assertThat(value("SELECT COUNT(*) FROM payment WHERE reservation_id = 990501")).isEqualTo("1");
        assertThat(value("SELECT status FROM payment WHERE reservation_id = 990501")).isEqualTo("COMPLETED");
        assertThat(value("SELECT status FROM reservation WHERE id = 990501")).isEqualTo("CONFIRMED");
        assertThat(value("SELECT COUNT(*) FROM seat_assignment WHERE reservation_id = 990501")).isEqualTo("1");
        assertThat(value("SELECT COUNT(*) FROM payment_refund")).isEqualTo("0");
    }

    @Test
    void 유예시간이_끝나도_미확정_좌석을_보호하고_나중에_DONE이면_확정한다() throws Exception {
        execute("""
                UPDATE reservation SET expires_at = CURRENT_TIMESTAMP - INTERVAL 1 MINUTE,
                    payment_starts_at = CURRENT_TIMESTAMP - INTERVAL 11 MINUTE WHERE id = 990501
                """);

        result("PENDING");
        reservations.expireBatch(OffsetDateTime.now(clock), 20);

        assertThat(approvalCalls.get()).isZero();
        assertThat(value("SELECT status FROM reservation WHERE id = 990501")).isEqualTo("PENDING_PAYMENT");
        assertThat(value("SELECT COUNT(*) FROM seat_assignment WHERE reservation_id = 990501")).isEqualTo("1");
        pgStatus.set("DONE");

        result("COMPLETED");

        assertThat(approvalCalls.get()).isZero();
        assertThat(value("SELECT status FROM payment WHERE reservation_id = 990501")).isEqualTo("COMPLETED");
        assertThat(value("SELECT status FROM reservation WHERE id = 990501")).isEqualTo("CONFIRMED");
    }

    @Test
    void 환불_오류_후_GET은_재전송하지_않고_스케줄러가_완료와_확인필요_해제를_반영한다() throws Exception {
        prepareLateApproval();

        result(202, "COMPLETED");

        assertThat(cancellationCalls.get()).isEqualTo(1);
        assertThat(refundValue("status")).isEqualTo("PENDING");
        assertThat(refundValue("attention_reason")).isEqualTo("REEXECUTION_REVIEW_REQUIRED");
        String since = refundValue("attention_since");
        String retryAt = refundValue("next_retry_at");
        String idempotencyKey = refundValue("idempotency_key");
        assertThat(since).isNotNull();
        assertThat(retryAt).isNotNull();
        assertThat(refundValue("request_started_at")).isNotNull();

        result(202, "COMPLETED");

        assertThat(cancellationCalls.get()).isEqualTo(1);
        assertThat(refundValue("retry_count")).isEqualTo("0");
        assertThat(refundValue("next_retry_at")).isEqualTo(retryAt);
        assertThat(refundValue("attention_since")).isEqualTo(since);
        pgStatus.set("CANCELED");
        execute("UPDATE payment_refund SET created_at = CURRENT_TIMESTAMP - INTERVAL 2 MINUTE "
                + "WHERE payment_key = '" + KEY + "'");
        new PaymentRecoveryScheduler(service, clock, java.time.Duration.ofSeconds(70), 20).recover();

        assertThat(refundValue("status")).isEqualTo("COMPLETED");
        assertThat(refundValue("attention_reason")).isNull();
        assertThat(refundValue("attention_since")).isEqualTo(since);
        assertThat(refundValue("attention_resolved_at")).isNotNull();
        assertThat(refundValue("idempotency_key")).isEqualTo(idempotencyKey);
        assertThat(cancellationCalls.get()).isEqualTo(1);
        assertThat(value("SELECT status FROM reservation WHERE id = 990501")).isEqualTo("CANCELLED");
    }

    @Test
    void 수정필요로_FAILED인_환불도_한도와_관계없이_PG_완료를_조회해_복구한다() throws Exception {
        cancellationError.set("INVALID_REQUEST");
        prepareLateApproval();
        result("COMPLETED");
        assertThat(refundValue("status")).isEqualTo("FAILED");
        assertThat(refundValue("attention_reason")).isEqualTo("CORRECTION_OR_REVIEW_REQUIRED");
        // 한도를 소진한 기존 기록도 결과 조회 대상임을 확인한다.
        execute("UPDATE payment_refund SET retry_count = 5 WHERE payment_key = '" + KEY + "'");
        result("COMPLETED");
        assertThat(refundValue("attention_reason")).isEqualTo("RETRY_LIMIT_REACHED");
        assertThat(cancellationCalls.get()).isEqualTo(1);

        pgStatus.set("CANCELED");
        result("COMPLETED");

        assertThat(refundValue("status")).isEqualTo("COMPLETED");
        assertThat(refundValue("retry_count")).isEqualTo("5");
        assertThat(refundValue("attention_reason")).isNull();
        assertThat(refundValue("attention_resolved_at")).isNotNull();
        assertThat(cancellationCalls.get()).isEqualTo(1);
    }

    private void prepareLateApproval() throws Exception {
        execute("UPDATE reservation SET status = 'CANCELLED' WHERE id = 990501");
        execute("DELETE FROM seat_assignment WHERE reservation_id = 990501");
        pgStatus.set("DONE");
    }

    private String refundValue(String column) throws Exception {
        return value("SELECT " + column + " FROM payment_refund WHERE payment_key = '" + KEY + "'");
    }

    private void result(String expected) {
        result(200, expected);
    }

    private void result(int status, String expected) {
        given().port(port).header("Authorization", BEARER_TOKEN)
                .when().get("/payments/{orderId}", ORDER)
                .then().statusCode(status).body("paymentStatus", equalTo(expected));
    }

    private void cleanRows() throws Exception {
        execute("DELETE FROM payment_refund WHERE payment_id IN (SELECT id FROM payment WHERE reservation_id = 990501)");
        execute("DELETE FROM payment WHERE reservation_id = 990501");
        execute("DELETE FROM seat_assignment WHERE reservation_id = 990501");
        execute("DELETE FROM reservation WHERE id = 990501");
    }

    private void execute(String sql) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private String value(String sql) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static void awaitBoth(CountDownLatch latch) throws IOException {
        latch.countDown();
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("동시 PG 요청이 제한 시간 내에 도착하지 않았습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        }
    }

    private static void respond(HttpExchange exchange, String status) throws IOException {
        String body = """
                {"paymentKey":"%s","orderId":"%s","totalAmount":%d,"status":"%s",
                 "method":"CARD","approvedAt":"%s"}
                """.formatted(KEY, ORDER, AMOUNT, status, OffsetDateTime.now(ZoneOffset.UTC));
        if ("CANCELED".equals(status)) {
            body = body.stripTrailing();
            body = body.substring(0, body.length() - 1)
                    + ",\"cancels\":[{\"cancelAmount\":30000,\"cancelStatus\":\"DONE\","
                    + "\"canceledAt\":\"" + OffsetDateTime.now(ZoneOffset.UTC) + "\"}]}";
        }
        sendJson(exchange, 200, body);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }

    private static HttpServer startPg() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(PG_THREADS);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
