package com.encore.ticket.core.booking.queue.port;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.encore.ticket.core.booking.queue.domain.QueueToken;
import com.encore.ticket.core.exception.NotFoundException;

public interface QueueRepository {

    /**
     * 활성 토큰 조회·순번 발급·만료 정리를 한 번에 끝낸다.
     * 순번을 밖에서 세어 넘기면 같은 값을 두 요청이 읽는다.
     */
    QueueEnterResult enterOrResume(Long scheduleId, Long memberId, OffsetDateTime now);

    Optional<QueueToken> findByToken(Long scheduleId, String queueToken);

    default QueueToken getByToken(Long scheduleId, String queueToken) {
        return findByToken(scheduleId, queueToken)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 대기열 토큰입니다."));
    }

    /**
     * 소유자 확인과 마지막 폴링 갱신을 한 번에 끝낸다.
     * 읽어 온 토큰을 고쳐 다시 저장하는 경로를 두지 않는다.
     */
    QueuePollResult recordPoll(Long scheduleId, String queueToken, Long memberId, OffsetDateTime now);
}
