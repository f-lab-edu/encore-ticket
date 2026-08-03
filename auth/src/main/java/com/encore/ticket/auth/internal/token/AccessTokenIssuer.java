package com.encore.ticket.auth.internal.token;

interface AccessTokenIssuer {
    String issue(Long userId);
}
