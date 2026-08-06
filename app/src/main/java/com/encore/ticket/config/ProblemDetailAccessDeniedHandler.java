package com.encore.ticket.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;
    private static final String CODE = "FORBIDDEN";
    private static final String DETAIL = "접근 권한이 없습니다.";

    private final ObjectMapper objectMapper;

    ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(STATUS, DETAIL);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("code", CODE);

        response.setStatus(STATUS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
