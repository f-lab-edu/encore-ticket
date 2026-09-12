package com.encore.ticket.payment;

import com.encore.ticket.core.payment.exception.PaymentGatewayException;
import com.encore.ticket.core.payment.port.PaymentApproval;
import com.encore.ticket.core.payment.port.PaymentCancellation;
import com.encore.ticket.core.payment.port.PaymentGateway;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TossPaymentGateway implements PaymentGateway {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final boolean configured;

    public TossPaymentGateway(
            ObjectMapper objectMapper,
            @Value("${ticket.payment.toss.secret-key:}") String secretKey,
            @Value("${ticket.payment.toss.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${ticket.payment.toss.connect-timeout:3s}") Duration connectTimeout,
            @Value("${ticket.payment.toss.read-timeout:60s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.objectMapper = objectMapper;
        this.configured = !secretKey.isBlank();
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorization)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    @Override
    public PaymentApproval approve(String paymentKey, String orderId, Long amount) {
        requireConfigured();
        try {
            TossPayment response = client.post()
                    .uri("/v1/payments/confirm")
                    .header(IDEMPOTENCY_KEY, paymentKey)
                    .body(new ConfirmBody(paymentKey, orderId, amount))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwHttpError)
                    .body(TossPayment.class);
            PaymentApproval approval = toApproval(response);
            validateApprovalIdentity(approval, paymentKey, orderId, amount);
            return approval;
        } catch (TossHttpException exception) {
            if (!isDefinitiveApprovalFailure(exception)) {
                throw new PaymentGatewayException("Toss 승인 결과를 확인할 수 없습니다", exception);
            }
            return PaymentApproval.declined(
                    paymentKey, orderId, amount, exception.code, exception.getMessage());
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Toss 승인 결과를 확인할 수 없습니다", exception);
        }
    }

    @Override
    public PaymentApproval query(String paymentKey) {
        requireConfigured();
        try {
            TossPayment response = client.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwHttpError)
                    .body(TossPayment.class);
            PaymentApproval approval = toApproval(response);
            if (!paymentKey.equals(approval.paymentKey())) {
                throw new PaymentGatewayException("Toss 조회 결과의 paymentKey가 요청과 다릅니다");
            }
            return approval;
        } catch (TossHttpException | RestClientException exception) {
            throw new PaymentGatewayException("Toss 결제 상태를 확인할 수 없습니다", exception);
        }
    }

    @Override
    public PaymentCancellation cancel(
            String paymentKey, Long amount, String reason, String idempotencyKey) {
        requireConfigured();
        try {
            TossPayment response = client.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header(IDEMPOTENCY_KEY, idempotencyKey)
                    .body(new CancelBody(reason))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwHttpError)
                    .body(TossPayment.class);
            return toCancellation(response, paymentKey, amount);
        } catch (TossHttpException exception) {
            if (exception.code.contains("ALREADY_CANCELED")) {
                return queryCancellation(paymentKey, amount);
            }
            if (isIndeterminate(exception)) {
                throw new PaymentGatewayException("Toss 환불 결과를 확인할 수 없습니다", exception);
            }
            return PaymentCancellation.failed(paymentKey, exception.code, exception.getMessage());
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Toss 환불 결과를 확인할 수 없습니다", exception);
        }
    }

    private PaymentCancellation queryCancellation(String paymentKey, Long amount) {
        try {
            TossPayment response = client.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwHttpError)
                    .body(TossPayment.class);
            return toCancellation(response, paymentKey, amount);
        } catch (TossHttpException | RestClientException exception) {
            throw new PaymentGatewayException("Toss 환불 상태를 확인할 수 없습니다", exception);
        }
    }

    private void throwHttpError(
            org.springframework.http.HttpRequest request,
            org.springframework.http.client.ClientHttpResponse response) throws IOException {
        TossError error;
        try {
            error = objectMapper.readValue(response.getBody(), TossError.class);
        } catch (RuntimeException exception) {
            error = null;
        }
        String code = error == null || error.code() == null
                ? "HTTP_" + response.getStatusCode().value()
                : error.code();
        String message = error == null || error.message() == null
                ? "Toss API 오류: " + response.getStatusCode().value()
                : error.message();
        throw new TossHttpException(response.getStatusCode().value(), code, message);
    }

    private static boolean isIndeterminate(TossHttpException exception) {
        return exception.status == 401
                || exception.status == 409
                || exception.status == 429
                || exception.status >= 500
                || exception.code.contains("API_KEY")
                || exception.code.contains("ALREADY_CANCELED")
                || "IDEMPOTENT_REQUEST_PROCESSING".equals(exception.code);
    }

    private static boolean isDefinitiveApprovalFailure(TossHttpException exception) {
        if (exception.status < 400 || exception.status >= 500 || isIndeterminate(exception)) {
            return false;
        }
        return exception.code.startsWith("REJECT_")
                || exception.code.startsWith("EXCEED_")
                || exception.code.startsWith("INVALID_")
                || exception.code.startsWith("NOT_SUPPORTED_")
                || "NOT_FOUND_PAYMENT_SESSION".equals(exception.code);
    }

    private void requireConfigured() {
        if (!configured) {
            throw new PaymentGatewayException("ticket.payment.toss.secret-key 설정이 필요합니다");
        }
    }

    private static PaymentApproval toApproval(TossPayment payment) {
        if (payment == null || payment.status() == null) {
            throw new PaymentGatewayException("Toss가 유효한 결제 결과를 반환하지 않았습니다");
        }
        return switch (payment.status()) {
            case "DONE" -> PaymentApproval.approved(
                    payment.paymentKey(), payment.orderId(), payment.totalAmount(),
                    payment.method(), payment.approvedAt());
            case "ABORTED", "EXPIRED" -> PaymentApproval.declined(
                    payment.paymentKey(), payment.orderId(), payment.totalAmount(),
                    payment.status(), payment.status());
            case "READY", "IN_PROGRESS" -> PaymentApproval.pending(
                    payment.paymentKey(), payment.orderId(), payment.totalAmount(), payment.status());
            case "CANCELED" -> PaymentApproval.canceled(
                    payment.paymentKey(), payment.orderId(), payment.totalAmount(), payment.status());
            default -> throw new PaymentGatewayException(
                    "지원하지 않는 Toss 결제 상태입니다: " + payment.status());
        };
    }

    private static PaymentCancellation toCancellation(
            TossPayment payment, String paymentKey, Long expectedAmount) {
        if (payment == null || !paymentKey.equals(payment.paymentKey())) {
            throw new PaymentGatewayException("Toss 환불 결과의 paymentKey가 요청과 다릅니다");
        }
        if (!"CANCELED".equals(payment.status()) || payment.cancels() == null) {
            throw new PaymentGatewayException("Toss 전액 환불이 완료되지 않았습니다");
        }
        TossCancel completed = payment.cancels().stream()
                .filter(cancel -> "DONE".equals(cancel.cancelStatus()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new PaymentGatewayException("완료된 Toss 환불 거래가 없습니다"));
        if (!expectedAmount.equals(completed.cancelAmount())) {
            throw new PaymentGatewayException("Toss 환불 금액이 요청 금액과 다릅니다");
        }
        if (completed.canceledAt() == null) {
            throw new PaymentGatewayException("Toss 환불 결과에 완료 시각이 없습니다");
        }
        return PaymentCancellation.completed(
                paymentKey, completed.cancelAmount(), completed.canceledAt());
    }

    private static void validateApprovalIdentity(
            PaymentApproval approval, String paymentKey, String orderId, Long amount) {
        if (!paymentKey.equals(approval.paymentKey())
                || !orderId.equals(approval.orderId())
                || !amount.equals(approval.amount())) {
            throw new PaymentGatewayException("Toss 승인 결과가 요청 결제 정보와 다릅니다");
        }
    }

    private record ConfirmBody(String paymentKey, String orderId, Long amount) {
    }

    private record CancelBody(String cancelReason) {
    }

    private record TossError(String code, String message) {
    }

    private record TossPayment(
            String paymentKey,
            String orderId,
            Long totalAmount,
            String status,
            String method,
            OffsetDateTime approvedAt,
            List<TossCancel> cancels) {
    }

    private record TossCancel(
            Long cancelAmount,
            OffsetDateTime canceledAt,
            String cancelStatus) {
    }

    private static final class TossHttpException extends RuntimeException {
        private final int status;
        private final String code;

        private TossHttpException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
