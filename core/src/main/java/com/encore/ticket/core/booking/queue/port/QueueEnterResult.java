package com.encore.ticket.core.booking.queue.port;

import com.encore.ticket.core.booking.queue.domain.QueueToken;

/**
 * {@code created} 는 이 호출이 새 토큰과 새 순번을 발급했는지만 말한다.
 * lapse 를 소비했는지는 담지 않는다.
 */
public record QueueEnterResult(QueueToken token, boolean created) {
}
