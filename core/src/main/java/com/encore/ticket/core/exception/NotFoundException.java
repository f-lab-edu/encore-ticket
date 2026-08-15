package com.encore.ticket.core.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String detail) {
        super(detail);
    }
}
