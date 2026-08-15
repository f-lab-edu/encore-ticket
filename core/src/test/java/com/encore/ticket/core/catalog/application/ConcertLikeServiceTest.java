package com.encore.ticket.core.catalog.application;

import com.encore.ticket.core.catalog.dto.ConcertLikeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.encore.ticket.core.catalog.domain.Concert;
import com.encore.ticket.core.catalog.port.ConcertLikeRepository;
import com.encore.ticket.core.catalog.port.ConcertRepository;

@ExtendWith(MockitoExtension.class)
class ConcertLikeServiceTest {

    private static final long CONCERT_ID = 1L;
    private static final long MEMBER_ID = 100L;

    @Mock ConcertRepository concertRepository;
    @Mock ConcertLikeRepository concertLikeRepository;

    ConcertLikeService service;

    @BeforeEach
    void setUp() {
        service = new ConcertLikeService(concertRepository, concertLikeRepository);
    }

    private Concert concertWith(int likeCount) {
        Concert concert = Concert.builder()
                .id(CONCERT_ID)
                .likeCount(likeCount)
                .build();
        given(concertRepository.getById(CONCERT_ID)).willReturn(concert);
        return concert;
    }

    @Test
    void 처음_좋아요하면_좋아요_수가_1_늘고_신규로_표시된다() {
        Concert concert = concertWith(127);
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(false);

        ConcertLikeResult result = service.like(CONCERT_ID, MEMBER_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.response().concertId()).isEqualTo(CONCERT_ID);
        assertThat(result.response().liked()).isTrue();
        assertThat(result.response().likeCount()).isEqualTo(128);
        assertThat(concert.likeCount()).isEqualTo(128);

        verify(concertLikeRepository).save(CONCERT_ID, MEMBER_ID);
        verify(concertRepository).save(concert);
    }

    @Test
    void 이미_좋아요한_상태에서_다시_요청하면_좋아요_수가_늘지_않는다() {
        Concert concert = concertWith(128);
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(true);

        ConcertLikeResult result = service.like(CONCERT_ID, MEMBER_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.response().liked()).isTrue();
        assertThat(result.response().likeCount()).isEqualTo(128);
        assertThat(concert.likeCount()).isEqualTo(128);

        verify(concertLikeRepository, never()).save(anyLong(), anyLong());
        verify(concertRepository, never()).save(any());
    }

    @Test
    void 좋아요를_취소하면_좋아요_수가_1_줄고_liked는_거짓이다() {
        Concert concert = concertWith(128);
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(true);

        ConcertLikeResponse response = service.unlike(CONCERT_ID, MEMBER_ID);

        assertThat(response.concertId()).isEqualTo(CONCERT_ID);
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(127);
        assertThat(concert.likeCount()).isEqualTo(127);

        verify(concertLikeRepository).delete(CONCERT_ID, MEMBER_ID);
        verify(concertRepository).save(concert);
    }

    @Test
    void 좋아요한_적_없는_상태에서_취소해도_좋아요_수가_변하지_않는다() {
        Concert concert = concertWith(127);
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(false);

        ConcertLikeResponse response = service.unlike(CONCERT_ID, MEMBER_ID);

        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(127);
        assertThat(concert.likeCount()).isEqualTo(127);

        verify(concertLikeRepository, never()).delete(anyLong(), anyLong());
        verify(concertRepository, never()).save(any());
    }

    @Test
    void 좋아요_수는_0_아래로_내려가지_않는다() {
        Concert concert = concertWith(0);
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(true);

        ConcertLikeResponse response = service.unlike(CONCERT_ID, MEMBER_ID);

        assertThat(response.likeCount()).isZero();
        assertThat(concert.likeCount()).isZero();
    }

    @Test
    void 좋아요는_기록과_콘서트를_함께_저장한다() {
        Concert concert = concertWith(0);
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(false);

        service.like(CONCERT_ID, MEMBER_ID);

        verify(concertLikeRepository).save(CONCERT_ID, MEMBER_ID);
        verify(concertRepository).save(concert);
    }
}
