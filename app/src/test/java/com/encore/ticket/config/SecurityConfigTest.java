package com.encore.ticket.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void permitAll_경로는_인증_없이_시큐리티를_통과한다() throws Exception {
        mockMvc.perform(get("/concerts"))
                .andExpect(status().isOk());
    }

    @Test
    void permitAll_경로는_ranking처럼_구체적인_규칙도_인증_없이_통과한다() throws Exception {
        mockMvc.perform(get("/concerts/ranking"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticated_경로는_헤더_없으면_401() throws Exception {
        mockMvc.perform(post("/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_경로는_Bearer_헤더가_있으면_통과한다() throws Exception {
        mockMvc.perform(post("/reservations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticated_경로는_공백만_있는_Bearer_토큰이면_401() throws Exception {
        mockMvc.perform(post("/reservations").header("Authorization", "Bearer    "))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/concerts")
        void concerts() {
        }

        @GetMapping("/concerts/ranking")
        void concertsRanking() {
        }

        @PostMapping("/reservations")
        void reservations() {
        }
    }
}