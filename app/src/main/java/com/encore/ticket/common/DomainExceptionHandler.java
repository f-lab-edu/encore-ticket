package com.encore.ticket.common;

import com.encore.ticket.booking.api.exception.BookingErrorCode;
import com.encore.ticket.booking.api.exception.BookingException;

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

    private HttpStatus statusOf(BookingErrorCode errorCode) {
        return switch (errorCode) {
            case QUEUE_NOT_ADMITTED -> HttpStatus.FORBIDDEN;
        };
    }
}
