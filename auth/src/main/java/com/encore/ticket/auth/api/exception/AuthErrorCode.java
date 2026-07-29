package com.encore.ticket.auth.api.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED);

    private final HttpStatus status;

    AuthErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
