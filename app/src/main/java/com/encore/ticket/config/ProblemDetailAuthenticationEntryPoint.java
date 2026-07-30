package com.encore.ticket.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final HttpStatus STATUS = HttpStatus.UNAUTHORIZED;
    private static final String CODE = "UNAUTHORIZED";
    private static final String DETAIL = "인증이 필요합니다.";

    private final ObjectMapper objectMapper;

    ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(STATUS, DETAIL);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("code", CODE);

        response.setStatus(STATUS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
