package com.encore.ticket.payment;

import com.encore.ticket.core.payment.exception.PaymentGatewayException;
import com.encore.ticket.core.payment.port.PaymentApproval;
import com.encore.ticket.core.payment.port.PaymentCancellation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossPaymentGatewayTest {

    private static final String PAYMENT_KEY = "tgen_key";
    private static final String ORDER_ID = "reservation-501-1";
    private static final long AMOUNT = 330_000L;

    private HttpServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        gateway = new TossPaymentGateway(
                JsonMapper.builder().findAndAddModules().build(),
                "test_sk",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 승인_API에_인증과_멱등키를_보내고_DONE을_승인으로_변환한다() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/payments/confirm", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, donePayment());
        });

        PaymentApproval approval = gateway.approve(PAYMENT_KEY, ORDER_ID, AMOUNT);

        assertThat(approval.state()).isEqualTo(PaymentApproval.State.APPROVED);
        assertThat(approval.orderId()).isEqualTo(ORDER_ID);
        assertThat(approval.amount()).isEqualTo(AMOUNT);
        assertThat(approval.method()).isEqualTo("카드");
        assertThat(idempotencyKey.get()).isEqualTo(PAYMENT_KEY);
        assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("test_sk:".getBytes(StandardCharsets.UTF_8)));
        assertThat(requestBody.get())
                .contains("\"paymentKey\":\"" + PAYMENT_KEY + "\"")
                .contains("\"orderId\":\"" + ORDER_ID + "\"")
                .contains("\"amount\":" + AMOUNT);
    }

    @ParameterizedTest
    @CsvSource({"READY, PENDING", "IN_PROGRESS, AWAITING_APPROVAL"})
    void 조회_결과의_인증_전과_승인_대기_상태를_구분한다(
            String providerStatus, PaymentApproval.State expectedState) {
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange ->
                respond(exchange, 200, unsettledPayment(providerStatus)));

        PaymentApproval approval = gateway.query(PAYMENT_KEY);

        assertThat(approval.state()).isEqualTo(expectedState);
        assertThat(approval.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(approval.orderId()).isEqualTo(ORDER_ID);
        assertThat(approval.amount()).isEqualTo(AMOUNT);
        assertThat(approval.isApproved()).isFalse();
        assertThat(approval.approvedAt()).isNull();
    }

    @Test
    void 알_수_없는_조회_상태를_승인_대기로_취급하지_않는다() {
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange ->
                respond(exchange, 200, unsettledPayment("UNRECOGNIZED")));

        assertThatThrownBy(() -> gateway.query(PAYMENT_KEY))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void 명확한_승인_거절은_DECLINED로_반환한다() {
        server.createContext("/v1/payments/confirm", exchange -> respond(
                exchange,
                400,
                "{\"code\":\"REJECT_CARD_PAYMENT\",\"message\":\"카드 한도 초과\"}"));

        PaymentApproval approval = gateway.approve(PAYMENT_KEY, ORDER_ID, AMOUNT);

        assertThat(approval.state()).isEqualTo(PaymentApproval.State.DECLINED);
        assertThat(approval.failureCode()).isEqualTo("REJECT_CARD_PAYMENT");
        assertThat(approval.failureMessage()).isEqualTo("카드 한도 초과");
    }

    @Test
    void 처리_중_멱등_응답은_실패로_확정하지_않고_불명확_예외를_던진다() {
        server.createContext("/v1/payments/confirm", exchange -> respond(
                exchange,
                409,
                "{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\",\"message\":\"처리 중\"}"));

        assertThatThrownBy(() -> gateway.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void 전액_환불은_별도_멱등키를_보내고_DONE_취소를_완료로_변환한다() {
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        server.createContext("/v1/payments/" + PAYMENT_KEY + "/cancel", exchange -> {
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, 200, canceledPayment());
        });

        PaymentCancellation cancellation = gateway.cancel(
                PAYMENT_KEY, AMOUNT, "예매 확정 불가 자동 환불", "refund-" + PAYMENT_KEY);

        assertThat(cancellation.state()).isEqualTo(PaymentCancellation.State.COMPLETED);
        assertThat(cancellation.canceledAmount()).isEqualTo(AMOUNT);
        assertThat(idempotencyKey.get()).isEqualTo("refund-" + PAYMENT_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALREADY_CANCELED_PAYMENT", "ALREADY_REFUND_PAYMENT"})
    void 이미_취소되거나_환불된_결제는_조회하여_환불_완료를_복구한다(String code) {
        server.createContext("/v1/payments/" + PAYMENT_KEY + "/cancel", exchange -> respond(
                exchange, 400,
                "{\"code\":\"" + code + "\",\"message\":\"이미 취소됨\"}"));
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange ->
                respond(exchange, 200, canceledPayment()));

        PaymentCancellation result = gateway.cancel(PAYMENT_KEY, AMOUNT, "자동 환불", "refund-key");

        assertThat(result.isCompleted()).isTrue();
        assertThat(result.canceledAmount()).isEqualTo(AMOUNT);
        assertThat(result.canceledAt()).isNotNull();
    }

    @Test
    void 이미_취소됨_응답만으로_환불_완료를_단정하지_않는다() {
        server.createContext("/v1/payments/" + PAYMENT_KEY + "/cancel", exchange -> respond(
                exchange, 400,
                "{\"code\":\"ALREADY_CANCELED_PAYMENT\",\"message\":\"이미 취소됨\"}"));
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange ->
                respond(exchange, 200, donePayment()));

        assertThatThrownBy(() -> gateway.cancel(PAYMENT_KEY, AMOUNT, "자동 환불", "refund-key"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void 환불_조회는_GET만_보내고_완료된_금액과_시각을_반환한다() {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicInteger cancelRequests = new AtomicInteger();
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange -> {
            method.set(exchange.getRequestMethod());
            respond(exchange, 200, canceledPayment());
        });
        server.createContext("/v1/payments/" + PAYMENT_KEY + "/cancel", exchange -> {
            cancelRequests.incrementAndGet();
            respond(exchange, 500, "{}");
        });

        PaymentCancellation result = gateway.queryCancellation(PAYMENT_KEY, AMOUNT);

        assertThat(method.get()).isEqualTo("GET");
        assertThat(cancelRequests.get()).isZero();
        assertThat(result.isCompleted()).isTrue();
        assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(result.canceledAmount()).isEqualTo(AMOUNT);
        assertThat(result.canceledAt()).isEqualTo(
                java.time.OffsetDateTime.parse("2026-08-01T20:09:10+09:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DONE", "PARTIAL_CANCELED", "IN_PROGRESS", "UNKNOWN"})
    void 전액_취소가_아닌_조회_결과를_환불_완료나_실패로_단정하지_않는다(String status) {
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange -> respond(
                exchange, 200, canceledPayment().replace("\"CANCELED\"", "\"" + status + "\"")));

        assertThatThrownBy(() -> gateway.queryCancellation(PAYMENT_KEY, AMOUNT))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "paymentKey, tgen_key, other_key",
            "cancelAmount, 330000, 1",
            "cancelStatus, DONE, PENDING",
            "canceledAt, 2026-08-01T20:09:10+09:00, null"
    })
    void 완료_응답이어도_식별자_금액_환불상태_시각을_검증한다(
            String field, String original, String replacement) {
        String body = canceledPayment();
        if (field.equals("cancelAmount")) {
            body = body.replace("\"cancelAmount\": " + original,
                    "\"cancelAmount\": " + replacement);
        } else {
            body = body.replace("\"" + field + "\": \"" + original + "\"",
                    "\"" + field + "\": " + (replacement.equals("null") ? "null" : "\"" + replacement + "\""));
        }
        String response = body;
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange -> respond(exchange, 200, response));

        assertThatThrownBy(() -> gateway.queryCancellation(PAYMENT_KEY, AMOUNT))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"paymentKey\":\"tgen_key\",\"status\":\"CANCELED\",\"cancels\":[]}"})
    void 완료된_환불_기록이_없으면_완료로_인정하지_않는다(String body) {
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange -> respond(exchange, 200, body));

        assertThatThrownBy(() -> gateway.queryCancellation(PAYMENT_KEY, AMOUNT))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404, 429, 500})
    void 조회_HTTP_오류는_환불_실패_결과로_반환하지_않는다(int status) {
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange ->
                respond(exchange, status, "{\"code\":\"QUERY_ERROR\",\"message\":\"조회 오류\"}"));

        assertThatThrownBy(() -> gateway.queryCancellation(PAYMENT_KEY, AMOUNT))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void 통신이_끊기면_환불_결과를_알_수_없는_예외로_남긴다() {
        server.stop(0);

        assertThatThrownBy(() -> gateway.queryCancellation(PAYMENT_KEY, AMOUNT))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null"})
    void 취소_기록이_없는_승인_결제는_미취소_관측으로_반환한다(String cancels) {
        server.createContext("/v1/payments/" + PAYMENT_KEY, exchange ->
                respond(exchange, 200, donePayment().replace("\"cancels\": []", "\"cancels\": " + cancels)));
        assertThat(gateway.queryCancellation(PAYMENT_KEY, AMOUNT).state())
                .isEqualTo(PaymentCancellation.State.NOT_CANCELED);
    }

    @ParameterizedTest
    @CsvSource({
            "400, PROVIDER_ERROR, AUTOMATIC_RECOVERY_CANDIDATE",
            "401, UNAUTHORIZED_KEY, CORRECTION_OR_REVIEW_REQUIRED",
            "500, FAILED_PARTIAL_REFUND, RESULT_CONFIRMATION_REQUIRED",
            "409, IDEMPOTENT_REQUEST_PROCESSING, RESULT_CONFIRMATION_REQUIRED"
    })
    void 환불_HTTP_오류를_복구_분류와_함께_반환한다(int status, String code,
            com.encore.ticket.core.payment.dto.RefundRecoveryCategory category) {
        server.createContext("/v1/payments/" + PAYMENT_KEY + "/cancel", exchange -> respond(exchange,
                status, "{\"code\":\"" + code + "\",\"message\":\"환불 오류\"}"));
        PaymentCancellation result = gateway.cancel(PAYMENT_KEY, AMOUNT, "환불", "same-key");
        assertThat(result.recoveryCategory()).isEqualTo(category);
        assertThat(result.failureCode()).isEqualTo(code);
        assertThat(result.isCompleted()).isFalse();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String unsettledPayment(String status) {
        return """
                {
                  "paymentKey": "%s",
                  "orderId": "%s",
                  "totalAmount": %d,
                  "status": "%s"
                }
                """.formatted(PAYMENT_KEY, ORDER_ID, AMOUNT, status);
    }

    private static String donePayment() {
        return """
                {
                  "paymentKey": "%s",
                  "orderId": "%s",
                  "totalAmount": %d,
                  "status": "DONE",
                  "method": "카드",
                  "approvedAt": "2026-08-01T20:08:10+09:00",
                  "cancels": []
                }
                """.formatted(PAYMENT_KEY, ORDER_ID, AMOUNT);
    }

    private static String canceledPayment() {
        return """
                {
                  "paymentKey": "%s",
                  "orderId": "%s",
                  "totalAmount": %d,
                  "status": "CANCELED",
                  "method": "카드",
                  "approvedAt": "2026-08-01T20:08:10+09:00",
                  "cancels": [{
                    "cancelAmount": %d,
                    "canceledAt": "2026-08-01T20:09:10+09:00",
                    "cancelStatus": "DONE"
                  }]
                }
                """.formatted(PAYMENT_KEY, ORDER_ID, AMOUNT, AMOUNT);
    }
}
