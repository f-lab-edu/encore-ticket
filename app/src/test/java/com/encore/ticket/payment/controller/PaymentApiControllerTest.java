package com.encore.ticket.payment.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.payment.api.dto.PaymentStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.springframework.http.HttpHeaders;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class PaymentApiControllerTest extends ApiSpecTestSupport {

    private static final List<String> SPEC_CONFIRM_FIELDS = List.of(
            "paymentKey", "orderId", "paymentStatus", "reservationId",
            "amount", "method", "reservationStatus", "approvedAt");

    private static final List<String> SPEC_RESULT_FIELDS = List.of(
            "paymentKey", "orderId", "paymentStatus", "pollAfterSeconds", "reservationId",
            "amount", "method", "reservationStatus", "approvedAt", "holdId", "failReason");

    private static final List<String> SPEC_PAYMENT_STATUS_NAMES =
            List.of("PENDING", "COMPLETED", "FAILED");

    private static final List<String> SPEC_RESERVATION_STATUS_NAMES =
            List.of("PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED");

    @Test
    void 결제_승인을_처음_요청하면_202와_스펙에_정의된_8개_필드를_반환한다() {
        Map<String, Object> body = confirmRequest(
                StubPayments.ACCEPTED_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then()
                    .statusCode(202)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_CONFIRM_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("paymentStatus")).isEqualTo("PENDING");
            softly.assertThat(body.get("orderId")).isEqualTo(StubPayments.ACCEPTED_ORDER_ID);
            softly.assertThat(body.get("paymentKey")).isEqualTo(StubPayments.PAYMENT_KEY);
            softly.assertThat(body.get("reservationId")).isNull();
            softly.assertThat(body.get("amount")).isNull();
            softly.assertThat(body.get("method")).isNull();
            softly.assertThat(body.get("reservationStatus")).isNull();
            softly.assertThat(body.get("approvedAt")).isNull();
        });
    }

    @Test
    void 이미_처리된_동일_요청은_200과_모든_필드를_반환한다() {
        Map<String, Object> body = confirmRequest(
                StubPayments.COMPLETED_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_CONFIRM_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("paymentStatus")).isEqualTo("COMPLETED");
            softly.assertThat(body.get("reservationStatus")).isEqualTo("CONFIRMED");
            softly.assertThat(body.get("reservationId")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("amount")).isEqualTo((int) StubPayments.EXPECTED_AMOUNT);
            softly.assertThat(body.get("method")).isInstanceOf(String.class);
            softly.assertThat(String.valueOf(body.get("approvedAt"))).matches(KST_DATE_TIME_REGEX);
        });
    }

    @Test
    void 두_결제_승인_응답은_같은_키_집합을_가진다() {
        Map<String, Object> accepted = confirmRequest(
                StubPayments.ACCEPTED_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then().statusCode(202).extract().jsonPath().getMap("$");

        Map<String, Object> completed = confirmRequest(
                StubPayments.COMPLETED_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then().statusCode(200).extract().jsonPath().getMap("$");

        assertThat(accepted.keySet()).isEqualTo(completed.keySet());
    }

    @Test
    void 요청_금액이_예매_금액과_다르면_400을_반환한다() {
        confirmRequest(StubPayments.ACCEPTED_ORDER_ID, StubPayments.EXPECTED_AMOUNT - 1)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(400))
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 취소된_예매를_결제_승인하면_409와_RESERVATION_CANCELLED를_반환한다() {
        confirmRequest(StubPayments.CANCELLED_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then()
                    .statusCode(409)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(409))
                    .body("code", equalTo("RESERVATION_CANCELLED"))
                    .body("instance", equalTo("/payments/confirm"));
    }

    @Test
    void 만료된_예매를_결제_승인하면_410과_HOLD_EXPIRED를_반환한다() {
        confirmRequest(StubPayments.EXPIRED_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then()
                    .statusCode(410)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(410))
                    .body("code", equalTo("HOLD_EXPIRED"));
    }

    @Test
    void 다른_사용자의_예매를_결제_승인하면_403을_반환한다() {
        confirmRequest(StubPayments.OTHER_MEMBER_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void 없는_주문을_결제_승인하면_404를_반환한다() {
        confirmRequest(StubPayments.MISSING_ORDER_ID, StubPayments.EXPECTED_AMOUNT)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 결제_승인_요청_바디가_비면_400과_INVALID_REQUEST를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(Map.of("paymentKey", "", "orderId", "", "amount", 0))
                .when()
                    .post("/payments/confirm")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REQUEST"));
    }

    @Test
    void 결제_승인은_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                    .body(confirmBody(StubPayments.ACCEPTED_ORDER_ID, StubPayments.EXPECTED_AMOUNT))
                .when()
                    .post("/payments/confirm")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 처리_중인_결제_결과는_200과_PENDING_형식을_반환한다() {
        Map<String, Object> body = resultOf(StubPayments.ACCEPTED_ORDER_ID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_RESULT_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("paymentStatus")).isEqualTo("PENDING");
            softly.assertThat(body.get("pollAfterSeconds")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("reservationId")).isNull();
            softly.assertThat(body.get("amount")).isNull();
            softly.assertThat(body.get("failReason")).isNull();
        });
    }

    @Test
    void 완료된_결제_결과는_200과_COMPLETED_형식을_반환한다() {
        Map<String, Object> body = resultOf(StubPayments.COMPLETED_ORDER_ID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_RESULT_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("paymentStatus")).isEqualTo("COMPLETED");
            softly.assertThat(body.get("reservationStatus")).isEqualTo("CONFIRMED");
            softly.assertThat(body.get("amount")).isEqualTo((int) StubPayments.EXPECTED_AMOUNT);
            softly.assertThat(String.valueOf(body.get("approvedAt"))).matches(KST_DATE_TIME_REGEX);
            softly.assertThat(body.get("pollAfterSeconds")).isNull();
            softly.assertThat(body.get("failReason")).isNull();
            softly.assertThat(body.get("holdId")).isNull();
        });
    }

    @Test
    void 실패한_결제_결과는_200과_FAILED_형식을_반환한다() {
        Map<String, Object> body = resultOf(StubPayments.FAILED_ORDER_ID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(SPEC_RESULT_FIELDS.toArray(String[]::new));
            softly.assertThat(body.get("paymentStatus")).isEqualTo("FAILED");
            softly.assertThat(body.get("failReason")).isInstanceOf(String.class);
            softly.assertThat(body.get("holdId")).isInstanceOf(String.class);
            softly.assertThat(body.get("reservationId")).isInstanceOf(Integer.class);
            softly.assertThat(body.get("amount")).isNull();
            softly.assertThat(body.get("approvedAt")).isNull();
        });
    }

    @Test
    void 세_결제_결과_응답은_모두_같은_키_집합을_가진다() {
        Map<String, Object> pending = resultOf(StubPayments.ACCEPTED_ORDER_ID);
        Map<String, Object> completed = resultOf(StubPayments.COMPLETED_ORDER_ID);
        Map<String, Object> failed = resultOf(StubPayments.FAILED_ORDER_ID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(pending.keySet()).isEqualTo(completed.keySet());
            softly.assertThat(completed.keySet()).isEqualTo(failed.keySet());
        });
    }

    @Test
    void 결제_결과의_paymentStatus는_스펙에_정의된_3개_값_중_하나다() {
        List<String> statuses = List.of(
                String.valueOf(resultOf(StubPayments.ACCEPTED_ORDER_ID).get("paymentStatus")),
                String.valueOf(resultOf(StubPayments.COMPLETED_ORDER_ID).get("paymentStatus")),
                String.valueOf(resultOf(StubPayments.FAILED_ORDER_ID).get("paymentStatus")));

        assertThat(statuses)
                .hasSize(3)
                .allSatisfy(status -> assertThat(SPEC_PAYMENT_STATUS_NAMES).contains(status));
    }

    @Test
    void 다른_사용자의_결제_결과를_조회하면_403을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/payments/{orderId}", StubPayments.OTHER_MEMBER_ORDER_ID)
                .then()
                    .statusCode(403)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void 없는_주문의_결제_결과를_조회하면_404를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/payments/{orderId}", StubPayments.MISSING_ORDER_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 결제_결과_조회는_인증이_필요하다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/payments/{orderId}", StubPayments.COMPLETED_ORDER_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void 결제_상태_ENUM은_스펙에_적힌_3개_리터럴과_정확히_일치한다() {
        List<String> declared = Arrays.stream(PaymentStatus.values()).map(Enum::name).toList();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SPEC_PAYMENT_STATUS_NAMES).hasSize(3);
            softly.assertThat(declared)
                    .hasSize(SPEC_PAYMENT_STATUS_NAMES.size())
                    .containsExactlyInAnyOrderElementsOf(SPEC_PAYMENT_STATUS_NAMES);
        });
    }

    @Test
    void 결제_응답의_reservationStatus는_예매_상태_문자열이다() {
        String reservationStatus = String.valueOf(
                resultOf(StubPayments.COMPLETED_ORDER_ID).get("reservationStatus"));

        assertThat(SPEC_RESERVATION_STATUS_NAMES).contains(reservationStatus);
    }

    private Map<String, Object> confirmBody(String orderId, long amount) {
        return Map.of(
                "paymentKey", StubPayments.PAYMENT_KEY,
                "orderId", orderId,
                "amount", amount);
    }

    private Response confirmRequest(String orderId, long amount) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                    .body(confirmBody(orderId, amount))
                .when()
                    .post("/payments/confirm");
    }

    private Map<String, Object> resultOf(String orderId) {
        return RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/payments/{orderId}", orderId)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");
    }
}
