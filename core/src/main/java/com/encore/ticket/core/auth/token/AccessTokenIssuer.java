package com.encore.ticket.core.auth.token;

interface AccessTokenIssuer {
    String issue(Long userId);
}
