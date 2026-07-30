package com.encore.ticket.booking.controller;

import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class DistinctSeatIdsValidator implements ConstraintValidator<DistinctSeatIds, SeatHoldRequest> {

    private static final String FIELD = "seatIds";

    @Override
    public boolean isValid(SeatHoldRequest request, ConstraintValidatorContext context) {
        if (request == null || request.seatIds() == null) {
            return true;
        }
        if (request.seatIds().size() == Set.copyOf(request.seatIds()).size()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(FIELD)
                .addConstraintViolation();
        return false;
    }
}
