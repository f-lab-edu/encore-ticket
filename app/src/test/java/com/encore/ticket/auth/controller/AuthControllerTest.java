package com.encore.ticket.auth.controller;

import com.encore.ticket.config.SecurityConfig;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    private static final String CALLBACK_RESPONSE = """
            {
              "accessToken": "access-token",
              "tokenType": "Bearer",
              "expiresIn": 1800,
              "user": {
                "id": 1,
                "name": "홍길동",
                "provider": "%s",
                "isNewUser": false
              }
            }
            """;

    private static final String REFRESH_RESPONSE = """
            {
              "accessToken": "new-access-token",
              "tokenType": "Bearer",
              "expiresIn": 1800
            }
            """;

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "google, https://accounts.google.com/",
            "kakao,  https://kauth.kakao.com/"
    })
    void 인증요청하면_302와_해당_제공자의_인증페이지_주소를_반환한다(String provider, String expectedPrefix) throws Exception {
        mockMvc.perform(get("/auth/oauth/{provider}/authorize", provider))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith(expectedPrefix)))
                .andExpect(content().string(""));
    }

    @Test
    void 인증요청은_state_검증용_쿠키를_내려준다() throws Exception {
        mockMvc.perform(get("/auth/oauth/google/authorize"))
                .andExpect(cookie().exists("oauthState"))
                .andExpect(cookie().value("oauthState", not(emptyString())))
                .andExpect(cookie().httpOnly("oauthState", true))
                .andExpect(cookie().secure("oauthState", true))
                .andExpect(cookie().path("oauthState", "/"));
    }

    @Test
    void 인증요청의_Location에_담긴_state는_oauthState_쿠키_값과_같다() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/oauth/google/authorize"))
                .andExpect(status().isFound())
                .andExpect(cookie().exists("oauthState"))
                .andReturn();

        String cookieState = result.getResponse().getCookie("oauthState").getValue();
        String locationState = UriComponentsBuilder
                .fromUriString(result.getResponse().getHeader("Location"))
                .build()
                .getQueryParams()
                .getFirst("state");

        assertThat(locationState).isNotBlank();
        assertThat(locationState).isEqualTo(cookieState);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "facebook"})
    void 지원하지_않는_provider로_인증요청하면_400을_반환한다(String provider) throws Exception {
        mockMvc.perform(get("/auth/oauth/{provider}/authorize", provider))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "facebook"})
    void 지원하지_않는_provider로_콜백을_호출하면_400을_반환한다(String provider) throws Exception {
        mockMvc.perform(get("/auth/oauth/{provider}/callback", provider)
                        .param("code", "4/0AY0e-g7")
                        .param("state", "f3a1c9"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GOOGLE", "Google", "KAKAO", "Kakao"})
    void provider의_대소문자가_다르면_인증요청은_400을_반환한다(String provider) throws Exception {
        mockMvc.perform(get("/auth/oauth/{provider}/authorize", provider))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GOOGLE", "Google", "KAKAO", "Kakao"})
    void provider의_대소문자가_다르면_콜백은_400을_반환한다(String provider) throws Exception {
        mockMvc.perform(get("/auth/oauth/{provider}/callback", provider)
                        .param("code", "4/0AY0e-g7")
                        .param("state", "f3a1c9"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"google", "kakao"})
    void 콜백_성공시_200과_액세스토큰_사용자정보를_반환한다(String provider) throws Exception {
        mockMvc.perform(get("/auth/oauth/{provider}/callback", provider)
                        .param("code", "4/0AY0e-g7")
                        .param("state", "f3a1c9"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(CALLBACK_RESPONSE.formatted(provider), JsonCompareMode.STRICT));
    }

    @Test
    void 콜백_응답은_refreshToken을_HttpOnly_쿠키로_내려준다() throws Exception {
        mockMvc.perform(get("/auth/oauth/google/callback")
                        .param("code", "4/0AY0e-g7")
                        .param("state", "f3a1c9"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().value("refreshToken", not(emptyString())))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/"));
    }

    @Test
    void 콜백에_code가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/auth/oauth/google/callback")
                        .param("state", "f3a1c9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 콜백에_state가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/auth/oauth/google/callback")
                        .param("code", "4/0AY0e-g7"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 콜백의_code가_빈_문자열이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/auth/oauth/google/callback")
                        .param("code", "")
                        .param("state", "f3a1c9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 콜백의_state가_공백이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/auth/oauth/google/callback")
                        .param("code", "4/0AY0e-g7")
                        .param("state", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshToken_쿠키로_재발급_요청하면_200과_새_액세스토큰을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(REFRESH_RESPONSE, JsonCompareMode.STRICT));
    }

    @Test
    void refreshToken_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_쿠키가_공백이면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", "   ")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%zz", "%20%20", "a+b"})
    void refreshToken_쿠키에_특수문자가_있어도_500이나_401이_아니다(String cookieValue) throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", cookieValue)))
                .andExpect(status().isOk());
    }

    @Test
    void 로그아웃하면_204와_만료된_refreshToken_쿠키를_반환한다() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().path("refreshToken", "/"))
                .andExpect(content().string(""));
    }

    @Test
    void 이미_로그아웃된_상태에서_재요청해도_204를_반환한다() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

}
