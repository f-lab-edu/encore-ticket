package com.encore.ticket.auth.api.exception;

public abstract class AuthException extends RuntimeException {

    private final AuthErrorCode errorCode;

    protected AuthException(AuthErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public AuthErrorCode errorCode() {
        return errorCode;
    }
}
