package com.encore.ticket.core.booking.exception;

import lombok.Getter;

@Getter
public abstract class BookingException extends RuntimeException {

    private final BookingErrorCode errorCode;

    protected BookingException(BookingErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
