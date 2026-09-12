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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void 이미_취소된_결제는_조회하여_환불_완료를_복구한다() {
        server.createContext("/v1/payments/" + PAYMENT_KEY + "/cancel", exchange -> respond(
                exchange, 400,
                "{\"code\":\"ALREADY_CANCELED_PAYMENT\",\"message\":\"이미 취소됨\"}"));
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

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
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
