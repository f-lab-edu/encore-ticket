package com.encore.ticket.core.auth.token.port;

public interface AccessTokenIssuer {
    public String issue(Long userId);
}
