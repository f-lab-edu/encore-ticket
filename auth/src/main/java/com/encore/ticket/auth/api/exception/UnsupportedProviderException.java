package com.encore.ticket.auth.api.exception;

public class UnsupportedProviderException extends AuthException {

    public UnsupportedProviderException(String provider) {
        super(AuthErrorCode.UNSUPPORTED_PROVIDER, "지원하지 않는 provider입니다: " + provider);
    }
}
