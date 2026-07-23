package com.encore.ticket.common;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    // TODO: 도메인 예외는 서비스/리포지토리 구현 PR에서 예외 클래스와 함께 @ExceptionHandler 추가 예정
}