package com.encore.ticket.core.auth.exception;


public class InvalidRefreshTokenException extends AuthException {

    public InvalidRefreshTokenException() {
        super(AuthErrorCode.INVALID_REFRESH_TOKEN, "Refresh Token이 유효하지 않습니다.");
    }
}
