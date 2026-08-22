package com.encore.ticket.storage.redis.booking.queue;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.encore.ticket.core.booking.queue.domain.QueueAdmissionPolicy;
import com.encore.ticket.core.booking.queue.port.QueueAdmissionResult;
import com.encore.ticket.core.booking.queue.port.QueueRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private final QueueRepository queueRepository;
    private final QueueAdmissionPolicy policy;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${ticket.queue.admission.scheduler-interval:1s}")
    public void admit() {
        admitAt(OffsetDateTime.now(clock));
    }

    QueueAdmissionResult admitAt(OffsetDateTime now) {
        return queueRepository.admit(now, policy);
    }
}
