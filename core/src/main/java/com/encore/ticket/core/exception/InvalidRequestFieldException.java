package com.encore.ticket.core.exception;

public class InvalidRequestFieldException extends RuntimeException {

    private final String field;

    public InvalidRequestFieldException(String field, String reason) {
        super(reason);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
