package com.encore.ticket.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import com.encore.ticket.core.exception.NotFoundException;

class DomainExceptionHandlerNotFoundTest {

    private final DomainExceptionHandler handler = new DomainExceptionHandler();

    @Test
    void 코어_NotFoundException_은_404와_NOT_FOUND_코드가_된다() {
        ResponseEntity<ProblemDetail> response =
                handler.handleNotFound(new NotFoundException("존재하지 않는 회차입니다: 1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(response.getBody().getDetail()).isEqualTo("존재하지 않는 회차입니다: 1");
    }
}
