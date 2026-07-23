package com.encore.ticket.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// TODO: auth 모듈에 실제 JWT 검증 로직이 생기면 이 스텁을 교체한다.
// 지금은 "Bearer " 뒤에 아무 값이나 있으면 무조건 STUB_MEMBER_ID로 인증 처리 —
// 토큰 유효성 자체는 검증하지 않음(인가 규칙만 먼저 검증하기 위한 의도적 단순화).
class StubBearerTokenFilter extends OncePerRequestFilter {

    private static final long STUB_MEMBER_ID = 1L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && !header.substring("Bearer ".length()).isBlank()) {
            var authentication = new UsernamePasswordAuthenticationToken(STUB_MEMBER_ID, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}