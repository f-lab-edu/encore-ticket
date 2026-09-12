package com.encore.ticket.payment.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.core.payment.application.PaymentService;
import com.encore.ticket.core.payment.dto.PaymentConfirmResponse;
import com.encore.ticket.core.payment.dto.PaymentRefundStatus;
import com.encore.ticket.core.payment.dto.PaymentResultResponse;
import com.encore.ticket.core.payment.dto.PaymentStatus;
import com.encore.ticket.core.payment.exception.ReservationNotOwnedException;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;

class PaymentApiControllerTest extends ApiSpecTestSupport {

    private static final String PAYMENT_KEY = "tgen_key";
    private static final String ORDER_ID = "reservation-501-1";
    private static final long AMOUNT = 330_000L;
    private static final long MEMBER_ID = 1L;
    private static final OffsetDateTime APPROVED_AT =
            OffsetDateTime.parse("2026-08-01T20:08:10+09:00");

    private static final List<String> CONFIRM_FIELDS = List.of(
            "paymentKey", "orderId", "paymentStatus", "reservationId",
            "amount", "method", "reservationStatus", "approvedAt",
            "refundStatus", "refundedAt", "refundFailureReason");

    private static final List<String> RESULT_FIELDS = List.of(
            "paymentKey", "orderId", "paymentStatus", "pollAfterSeconds", "reservationId",
            "amount", "method", "reservationStatus", "approvedAt", "holdId", "failReason",
            "refundStatus", "refundedAt", "refundFailureReason");

    @MockitoBean
    PaymentService paymentService;

    @Test
    void 결제와_예매가_완료되면_200과_확정_결과를_반환한다() {
        given(paymentService.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID))
                .willReturn(completedConfirm(null));

        Map<String, Object> body = confirmRequest()
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(CONFIRM_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("paymentStatus")).isEqualTo("COMPLETED");
            softly.assertThat(body.get("reservationStatus")).isEqualTo("CONFIRMED");
            softly.assertThat(body.get("refundStatus")).isNull();
        });
    }

    @Test
    void PG_결과가_불명확한_PENDING은_202를_반환한다() {
        given(paymentService.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID))
                .willReturn(new PaymentConfirmResponse(
                        PAYMENT_KEY, ORDER_ID, PaymentStatus.PENDING,
                        null, null, null, null, null, null, null, null));

        confirmRequest()
                .then()
                    .statusCode(202)
                    .body("paymentStatus", equalTo("PENDING"))
                    .body("refundStatus", equalTo(null));
    }

    @Test
    void 승인됐지만_자동_환불이_PENDING이면_202와_세_상태를_함께_반환한다() {
        given(paymentService.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT, MEMBER_ID))
                .willReturn(new PaymentConfirmResponse(
                        PAYMENT_KEY, ORDER_ID, PaymentStatus.COMPLETED,
                        501L, AMOUNT, "CARD", "EXPIRED", APPROVED_AT,
                        PaymentRefundStatus.PENDING, null, null));

        Map<String, Object> body = confirmRequest()
                .then()
                    .statusCode(202)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.get("paymentStatus")).isEqualTo("COMPLETED");
            softly.assertThat(body.get("reservationStatus")).isEqualTo("EXPIRED");
            softly.assertThat(body.get("refundStatus")).isEqualTo("PENDING");
        });
    }

    @Test
    void 자동_환불까지_끝나면_200과_환불_완료_시각을_반환한다() {
        OffsetDateTime refundedAt = APPROVED_AT.plusMinutes(1);
        given(paymentService.result(ORDER_ID, MEMBER_ID))
                .willReturn(result(PaymentRefundStatus.COMPLETED, refundedAt));

        Map<String, Object> body = resultRequest()
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(RESULT_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("paymentStatus")).isEqualTo("COMPLETED");
            softly.assertThat(body.get("reservationStatus")).isEqualTo("EXPIRED");
            softly.assertThat(body.get("refundStatus")).isEqualTo("COMPLETED");
            softly.assertThat(String.valueOf(body.get("refundedAt"))).matches(KST_DATE_TIME_REGEX);
        });
    }

    @Test
    void 결과_조회에서_자동_환불이_PENDING이면_202를_반환한다() {
        given(paymentService.result(ORDER_ID, MEMBER_ID))
                .willReturn(result(PaymentRefundStatus.PENDING, null));

        resultRequest()
                .then()
                    .statusCode(202)
                    .body("paymentStatus", equalTo("COMPLETED"))
                    .body("refundStatus", equalTo("PENDING"));
    }

    @Test
    void 요청_필드가_유효하지_않으면_400을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(Map.of("paymentKey", "", "orderId", ORDER_ID, "amount", AMOUNT))
                .when()
                    .post("/payments/confirm")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"))
                    .body("errors.field", hasItem("paymentKey"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"paymentKey", "orderId"})
    void DB_저장_길이를_넘는_식별자는_400을_반환한다(String field) {
        var body = new java.util.HashMap<String, Object>(Map.of(
                "paymentKey", PAYMENT_KEY, "orderId", ORDER_ID, "amount", AMOUNT));
        body.put(field, "x".repeat(field.equals("paymentKey") ? 201 : 65));

        RestAssured.given().spec(spec)
                .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .body(body)
                .when().post("/payments/confirm")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"))
                .body("errors.field", hasItem(field));
    }

    @Test
    void 결제_PENDING_조회는_기존_200과_재조회_간격을_유지한다() {
        given(paymentService.result(ORDER_ID, MEMBER_ID)).willReturn(new PaymentResultResponse(
                PAYMENT_KEY, ORDER_ID, PaymentStatus.PENDING, 2,
                null, null, null, null, null, null, null, null, null, null));

        resultRequest().then().statusCode(200)
                .body("paymentStatus", equalTo("PENDING"))
                .body("pollAfterSeconds", equalTo(2));
    }

    @Test
    void 다른_사용자의_결제_결과는_403을_반환한다() {
        given(paymentService.result(ORDER_ID, MEMBER_ID))
                .willThrow(new ReservationNotOwnedException());

        resultRequest()
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("RESERVATION_NOT_OWNED"));
    }

    @Test
    void 결제_API는_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .body(Map.of("paymentKey", PAYMENT_KEY, "orderId", ORDER_ID, "amount", AMOUNT))
                .when()
                    .post("/payments/confirm")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    private PaymentConfirmResponse completedConfirm(PaymentRefundStatus refundStatus) {
        return new PaymentConfirmResponse(
                PAYMENT_KEY, ORDER_ID, PaymentStatus.COMPLETED,
                501L, AMOUNT, "CARD", "CONFIRMED", APPROVED_AT,
                refundStatus, null, null);
    }

    private PaymentResultResponse result(
            PaymentRefundStatus refundStatus, OffsetDateTime refundedAt) {
        return new PaymentResultResponse(
                PAYMENT_KEY, ORDER_ID, PaymentStatus.COMPLETED,
                refundStatus == PaymentRefundStatus.PENDING ? 2 : null,
                501L, AMOUNT, "CARD", "EXPIRED", APPROVED_AT,
                null, null, refundStatus, refundedAt, null);
    }

    private io.restassured.response.Response confirmRequest() {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(Map.of("paymentKey", PAYMENT_KEY, "orderId", ORDER_ID, "amount", AMOUNT))
                .when()
                    .post("/payments/confirm");
    }

    private io.restassured.response.Response resultRequest() {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/payments/{orderId}", ORDER_ID);
    }
}
