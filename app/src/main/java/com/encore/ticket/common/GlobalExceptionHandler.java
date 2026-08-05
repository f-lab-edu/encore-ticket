package com.encore.ticket.common;

import java.util.List;
import java.util.Map;

import com.encore.ticket.auth.api.exception.AuthErrorCode;
import com.encore.ticket.auth.api.exception.AuthException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, "요청 값이 유효하지 않습니다.");
        problemDetail.setProperty("code", "INVALID_REQUEST");
        problemDetail.setProperty("errors", toFieldErrorDetails(ex));

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    @ExceptionHandler(AuthException.class)
    protected ResponseEntity<Object> handleAuthException(AuthException ex, WebRequest request) {

        HttpStatus status = statusOf(ex.errorCode());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setProperty("code", ex.errorCode().name());

        return handleExceptionInternal(ex, problemDetail, new HttpHeaders(), status, request);
    }

    private HttpStatus statusOf(AuthErrorCode errorCode) {
        return switch (errorCode) {
            case UNSUPPORTED_PROVIDER -> HttpStatus.BAD_REQUEST;
            case INVALID_REFRESH_TOKEN -> HttpStatus.UNAUTHORIZED;
        };
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        if (body instanceof ProblemDetail problemDetail) {
            Map<String, Object> properties = problemDetail.getProperties();
            if (properties == null || !properties.containsKey("code")) {
                problemDetail.setProperty("code", defaultCode(statusCode));
            }
        }

        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private String defaultCode(HttpStatusCode statusCode) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return resolved != null ? resolved.name() : "HTTP_" + statusCode.value();
    }

    private List<FieldErrorDetail> toFieldErrorDetails(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorDetail)
                .toList();
    }

    private FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
        String reason = fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "유효하지 않은 값입니다.";
        return new FieldErrorDetail(fieldError.getField(), reason);
    }

    private record FieldErrorDetail(String field, String reason) {
    }

}