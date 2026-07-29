package com.encore.ticket.auth.api.exception;

import org.springframework.web.ErrorResponseException;


public class InvalidRefreshTokenException extends ErrorResponseException {

    public InvalidRefreshTokenException() {
        super(AuthErrorCode.INVALID_REFRESH_TOKEN.status());
        getBody().setDetail("Refresh Token이 유효하지 않습니다.");
        getBody().setProperty("code", AuthErrorCode.INVALID_REFRESH_TOKEN.name());
    }
}
