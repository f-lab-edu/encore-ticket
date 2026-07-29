package com.encore.ticket.auth.api.exception;

import org.springframework.web.ErrorResponseException;

public class UnsupportedProviderException extends ErrorResponseException {

    public UnsupportedProviderException(String provider) {
        super(AuthErrorCode.UNSUPPORTED_PROVIDER.status());
        getBody().setDetail("지원하지 않는 provider입니다: " + provider);
        getBody().setProperty("code", AuthErrorCode.UNSUPPORTED_PROVIDER.name());
    }
}
