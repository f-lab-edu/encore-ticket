package com.encore.ticket.core.booking.exception;

public class ReservationConcurrentModificationException extends BookingException {

    public ReservationConcurrentModificationException(Throwable cause) {
        super(BookingErrorCode.RESERVATION_CONCURRENTLY_MODIFIED,
                "예매 상태가 동시에 변경되었습니다. 다시 시도해 주세요.", cause);
    }
}
