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

    private void givenConcert() {
        given(concertRepository.getById(CONCERT_ID)).willReturn(Concert.builder().id(CONCERT_ID).build());
    }

    @Test
    void 처음_좋아요하면_기록을_저장하고_신규로_표시된다() {
        givenConcert();
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(false);
        given(concertLikeRepository.count(CONCERT_ID)).willReturn(128);

        ConcertLikeResult result = service.like(CONCERT_ID, MEMBER_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.response().concertId()).isEqualTo(CONCERT_ID);
        assertThat(result.response().liked()).isTrue();
        assertThat(result.response().likeCount()).isEqualTo(128);

        verify(concertLikeRepository).save(CONCERT_ID, MEMBER_ID);
    }

    @Test
    void 이미_좋아요한_상태에서_다시_요청하면_기록을_저장하지_않는다() {
        givenConcert();
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(true);
        given(concertLikeRepository.count(CONCERT_ID)).willReturn(128);

        ConcertLikeResult result = service.like(CONCERT_ID, MEMBER_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.response().liked()).isTrue();
        assertThat(result.response().likeCount()).isEqualTo(128);

        verify(concertLikeRepository, never()).save(anyLong(), anyLong());
    }

    @Test
    void 좋아요를_취소하면_기록을_지우고_liked는_거짓이다() {
        givenConcert();
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(true);
        given(concertLikeRepository.count(CONCERT_ID)).willReturn(127);

        ConcertLikeResponse response = service.unlike(CONCERT_ID, MEMBER_ID);

        assertThat(response.concertId()).isEqualTo(CONCERT_ID);
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(127);

        verify(concertLikeRepository).delete(CONCERT_ID, MEMBER_ID);
    }

    @Test
    void 좋아요한_적_없는_상태에서_취소해도_기록을_지우지_않는다() {
        givenConcert();
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(false);
        given(concertLikeRepository.count(CONCERT_ID)).willReturn(127);

        ConcertLikeResponse response = service.unlike(CONCERT_ID, MEMBER_ID);

        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(127);

        verify(concertLikeRepository, never()).delete(anyLong(), anyLong());
    }

    @Test
    void 좋아요_수는_저장된_기록_개수를_그대로_돌려준다() {
        givenConcert();
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(true);
        given(concertLikeRepository.count(CONCERT_ID)).willReturn(0);

        ConcertLikeResponse response = service.unlike(CONCERT_ID, MEMBER_ID);

        assertThat(response.likeCount()).isZero();
    }

    @Test
    void 좋아요는_콘서트_행을_쓰지_않는다() {
        givenConcert();
        given(concertLikeRepository.exists(CONCERT_ID, MEMBER_ID)).willReturn(false);

        service.like(CONCERT_ID, MEMBER_ID);

        verify(concertLikeRepository).save(CONCERT_ID, MEMBER_ID);
        verify(concertRepository, never()).save(any());
    }
}
