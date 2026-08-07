package com.encore.ticket.core.auth;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AuthProvider {

    GOOGLE,
    KAKAO;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AuthProvider> from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.value().equals(value))
                .findFirst();
    }
}
