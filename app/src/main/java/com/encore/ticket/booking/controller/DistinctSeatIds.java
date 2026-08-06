package com.encore.ticket.booking.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.TYPE;

@Documented
@Target(TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistinctSeatIdsValidator.class)
@interface DistinctSeatIds {

    String message() default "좌석 ID는 중복될 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
