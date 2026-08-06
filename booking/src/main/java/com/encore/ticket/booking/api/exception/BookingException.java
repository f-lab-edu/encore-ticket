package com.encore.ticket.booking.api.exception;

public abstract class BookingException extends RuntimeException {

    private final BookingErrorCode errorCode;

    protected BookingException(BookingErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public BookingErrorCode errorCode() {
        return errorCode;
    }
}
