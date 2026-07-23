package com.encore.ticket.common;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다.");
        problemDetail.setProperty("code", "INVALID_REQUEST");
        problemDetail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "reason", fieldError.getDefaultMessage()))
                .toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    // TODO: 도메인 예외는 서비스/리포지토리 구현 PR에서 예외 클래스와 함께 @ExceptionHandler 추가 예정
}