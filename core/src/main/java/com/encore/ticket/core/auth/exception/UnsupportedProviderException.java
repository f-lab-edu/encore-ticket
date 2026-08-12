package com.encore.ticket.core.auth.exception;

public class UnsupportedProviderException extends AuthException {

    public UnsupportedProviderException(String provider) {
        super(AuthErrorCode.UNSUPPORTED_PROVIDER, "지원하지 않는 provider입니다: " + provider);
    }
}
