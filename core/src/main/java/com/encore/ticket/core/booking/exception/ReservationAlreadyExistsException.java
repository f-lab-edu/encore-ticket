package com.encore.ticket.core.booking.exception;

/**
 * 같은 holdId의 예매가 먼저 저장되었음을 알린다.
 * 실패한 저장 트랜잭션이 종료된 뒤 기존 예매를 재조회하는 데 사용한다.
 */
public class ReservationAlreadyExistsException extends RuntimeException {

    public ReservationAlreadyExistsException(Throwable cause) {
        super("같은 선점으로 생성된 예매가 이미 존재합니다.", cause);
    }
}
