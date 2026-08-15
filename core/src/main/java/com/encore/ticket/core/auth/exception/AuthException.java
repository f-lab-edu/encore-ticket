package com.encore.ticket.core.auth.exception;

import lombok.Getter;

@Getter
public abstract class AuthException extends RuntimeException {

    private final AuthErrorCode errorCode;

    protected AuthException(AuthErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
