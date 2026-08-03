package com.encore.ticket.catalog.internal.concert;

import com.encore.ticket.catalog.api.dto.ConcertRankingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConcertRankingServiceTest {

    private static final OffsetDateTime SNAPSHOT_AT = OffsetDateTime.parse("2026-07-15T18:00:00Z");
    private static final int LIMIT = 10;

    @Mock ConcertRankingRepository concertRankingRepository;

    ConcertRankingService service;

    @BeforeEach
    void setUp() {
        service = new ConcertRankingService(concertRankingRepository);
    }

    private ConcertScore score(long concertId, String title, int viewCount, int likeCount) {
        return new ConcertScore(concertId, title, "https://example.com/poster%d.jpg".formatted(concertId),
                viewCount, likeCount);
    }

    @Test
    void 점수는_조회_1점과_좋아요_3점을_합친_값이다() {
        given(concertRankingRepository.latestSnapshotAt()).willReturn(Optional.of(SNAPSHOT_AT));
        given(concertRankingRepository.scoresAt(SNAPSHOT_AT, LIMIT))
                .willReturn(List.of(score(1L, "2026 아이유 콘서트", 300, 14)));

        ConcertRankingResponse response = service.ranking(LIMIT);

        ConcertRankingResponse.Item item = response.items().getFirst();
        assertThat(item.rank()).isEqualTo(1);
        assertThat(item.concertId()).isEqualTo(1L);
        assertThat(item.title()).isEqualTo("2026 아이유 콘서트");
        assertThat(item.posterUrl()).isEqualTo("https://example.com/poster1.jpg");
        assertThat(item.score()).isEqualTo(342);
    }

    @Test
    void 점수가_높은_순으로_순위를_매긴다() {
        given(concertRankingRepository.latestSnapshotAt()).willReturn(Optional.of(SNAPSHOT_AT));
        given(concertRankingRepository.scoresAt(SNAPSHOT_AT, LIMIT)).willReturn(List.of(
                score(1L, "조회만 많은 콘서트", 100, 0),
                score(2L, "좋아요가 많은 콘서트", 10, 50),
                score(3L, "둘 다 적은 콘서트", 20, 5)));

        ConcertRankingResponse response = service.ranking(LIMIT);

        assertThat(response.items())
                .extracting(ConcertRankingResponse.Item::rank, ConcertRankingResponse.Item::concertId,
                        ConcertRankingResponse.Item::score)
                .containsExactly(
                        tuple(1, 2L, 160),
                        tuple(2, 1L, 100),
                        tuple(3, 3L, 35));
    }

    @Test
    void 스냅샷이_있으면_기준_시각을_그대로_돌려준다() {
        given(concertRankingRepository.latestSnapshotAt()).willReturn(Optional.of(SNAPSHOT_AT));
        given(concertRankingRepository.scoresAt(SNAPSHOT_AT, LIMIT))
                .willReturn(List.of(score(1L, "2026 아이유 콘서트", 300, 14)));

        ConcertRankingResponse response = service.ranking(LIMIT);

        assertThat(response.asOf()).isEqualTo(SNAPSHOT_AT);

        verify(concertRankingRepository, never()).bookingOpenSoon(anyInt());
    }

    @Test
    void 스냅샷이_없으면_기준_시각은_null이고_예매_오픈_임박순으로_대체한다() {
        given(concertRankingRepository.latestSnapshotAt()).willReturn(Optional.empty());
        given(concertRankingRepository.bookingOpenSoon(LIMIT)).willReturn(List.of(
                score(7L, "곧 여는 콘서트", 0, 0),
                score(8L, "그다음 콘서트", 0, 0)));

        ConcertRankingResponse response = service.ranking(LIMIT);

        assertThat(response.asOf()).isNull();
        assertThat(response.items())
                .extracting(ConcertRankingResponse.Item::rank, ConcertRankingResponse.Item::concertId)
                .containsExactly(tuple(1, 7L), tuple(2, 8L));

        verify(concertRankingRepository, never()).scoresAt(any(), anyInt());
    }

    @Test
    void 요청한_개수를_저장소에_그대로_넘긴다() {
        given(concertRankingRepository.latestSnapshotAt()).willReturn(Optional.of(SNAPSHOT_AT));
        given(concertRankingRepository.scoresAt(SNAPSHOT_AT, 50)).willReturn(List.of());

        service.ranking(50);

        verify(concertRankingRepository).scoresAt(SNAPSHOT_AT, 50);
    }

    @Test
    void 순위가_비어_있어도_빈_목록을_돌려준다() {
        given(concertRankingRepository.latestSnapshotAt()).willReturn(Optional.of(SNAPSHOT_AT));
        given(concertRankingRepository.scoresAt(SNAPSHOT_AT, LIMIT)).willReturn(List.of());

        ConcertRankingResponse response = service.ranking(LIMIT);

        assertThat(response.asOf()).isEqualTo(SNAPSHOT_AT);
        assertThat(response.items()).isEmpty();
    }
}
