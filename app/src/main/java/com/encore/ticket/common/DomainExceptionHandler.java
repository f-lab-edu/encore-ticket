package com.encore.ticket.common;

import java.util.List;

import com.encore.ticket.booking.api.exception.BookingErrorCode;
import com.encore.ticket.booking.api.exception.BookingException;
import com.encore.ticket.payment.api.exception.PaymentErrorCode;
import com.encore.ticket.payment.api.exception.PaymentException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(BookingException.class)
    ResponseEntity<ProblemDetail> handleBookingException(BookingException ex) {

        HttpStatus status = statusOf(ex.errorCode());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setProperty("code", ex.errorCode().name());

        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ProblemDetail> handlePaymentException(PaymentException ex) {

        HttpStatus status = statusOf(ex.errorCode());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setProperty("code", ex.errorCode().name());

        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(InvalidRequestFieldException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequestField(InvalidRequestFieldException ex) {

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다.");
        problemDetail.setProperty("code", "INVALID_REQUEST");
        problemDetail.setProperty("errors", List.of(new FieldError(ex.field(), ex.getMessage())));

        return ResponseEntity.badRequest().body(problemDetail);
    }

    private record FieldError(String field, String reason) {
    }

    private HttpStatus statusOf(PaymentErrorCode errorCode) {
        return switch (errorCode) {
            case AMOUNT_MISMATCH -> HttpStatus.BAD_REQUEST;
            case RESERVATION_NOT_OWNED -> HttpStatus.FORBIDDEN;
            case PAYMENT_KEY_REUSED, ORDER_ID_ALREADY_BOUND,
                 STALE_PAYMENT_ATTEMPT, RESERVATION_CANCELLED -> HttpStatus.CONFLICT;
            case HOLD_EXPIRED -> HttpStatus.GONE;
        };
    }

    private HttpStatus statusOf(BookingErrorCode errorCode) {
        return switch (errorCode) {
            case QUEUE_NOT_ADMITTED, HOLD_NOT_OWNED, RESERVATION_NOT_OWNED -> HttpStatus.FORBIDDEN;
            case SEAT_ALREADY_HELD, IDEMPOTENCY_KEY_REUSED,
                 PURCHASE_LIMIT_EXCEEDED, RESERVATION_CANCELLED,
                 CANCELLATION_CLOSED, PAYMENT_IN_PROGRESS -> HttpStatus.CONFLICT;
            case HOLD_EXPIRED, QUEUE_TOKEN_EXPIRED -> HttpStatus.GONE;
        };
    }
}
