package com.encore.ticket.booking.controller;

import com.encore.ticket.booking.api.dto.ReservationStatus;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class CancelStatusOnlyValidator implements ConstraintValidator<CancelStatusOnly, ReservationCancelRequest> {

    private static final String FIELD = "status";

    @Override
    public boolean isValid(ReservationCancelRequest request, ConstraintValidatorContext context) {
        if (request == null || request.status() == null || request.status() == ReservationStatus.CANCELLED) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(FIELD)
                .addConstraintViolation();
        return false;
    }
}
