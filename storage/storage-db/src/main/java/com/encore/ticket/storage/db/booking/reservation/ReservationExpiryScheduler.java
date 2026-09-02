package com.encore.ticket.storage.db.booking.reservation;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.encore.ticket.core.booking.reservation.port.ReservationRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final Clock clock;
    private final int batchSize;

    public ReservationExpiryScheduler(
            ReservationRepository reservationRepository,
            Clock clock,
            @Value("${ticket.reservation.expiry.batch-size:100}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("만료 batch 크기는 1 이상이어야 합니다: " + batchSize);
        }
        this.reservationRepository = reservationRepository;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ticket.reservation.expiry.scheduler-interval:1s}")
    public void expire() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            int expiredCount = expireAt(now);
            if (expiredCount > 0) {
                log.info("event=reservation_expiry_batch_completed expiredCount={} batchSize={} cutoff={}",
                        expiredCount, batchSize, now);
            }
        } catch (RuntimeException exception) {
            log.error("event=reservation_expiry_batch_failed batchSize={} cutoff={} errorType={}",
                    batchSize, now, exception.getClass().getSimpleName(), exception);
        }
    }

    int expireAt(OffsetDateTime now) {
        return reservationRepository.expireBatch(now, batchSize);
    }
}
