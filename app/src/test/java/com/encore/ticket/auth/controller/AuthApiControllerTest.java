package com.encore.ticket.auth.controller;

import com.encore.ticket.ApiSpecTestSupport;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.SoftAssertions;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * API 스펙 계약 검증. 실제 서버를 랜덤 포트로 띄우고 진짜 HTTP로 호출해,
 * 포스트맨으로 클릭해가며 확인하던 것을 대체한다.
 * 슬라이스 단위 검증은 {@link AuthControllerTest}가 담당한다.
 */
class AuthApiControllerTest extends ApiSpecTestSupport {

    private static final String OAUTH_STATE_COOKIE = "oauthState";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/auth";

    @Test
    void 인증요청하면_인증페이지로_리다이렉트하고_state쿠키를_내려준다() {
        Response response = RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                .when()
                    .get("/auth/oauth/google/authorize")
                .then()
                    .statusCode(302)
                .extract().response();

        Cookie cookie = response.getDetailedCookie(OAUTH_STATE_COOKIE);
        assertThat(cookie).isNotNull();

        URI location = URI.create(response.getHeader(HttpHeaders.LOCATION));
        Map<String, String> query = UriComponentsBuilder.fromUri(location)
                .build()
                .getQueryParams()
                .toSingleValueMap();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(location.getScheme()).isEqualTo("https");
            softly.assertThat(location.getHost()).isEqualTo("accounts.google.com");
            softly.assertThat(location.getPath()).isEqualTo("/o/oauth2/v2/auth");
            softly.assertThat(query).containsEntry("response_type", "code");
            softly.assertThat(query).containsKeys("client_id", "redirect_uri", "scope", "state");

            softly.assertThat(cookie.isHttpOnly()).isTrue();
            softly.assertThat(cookie.isSecured()).isTrue();
            softly.assertThat(cookie.getSameSite()).isEqualTo("Lax");
            softly.assertThat(cookie.getPath()).isEqualTo("/");

            softly.assertThat(cookie.getMaxAge()).isEqualTo(600);

            softly.assertThat(query.get("state"))
                    .isNotBlank()
                    .isEqualTo(cookie.getValue());
        });
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "google, accounts.google.com, /o/oauth2/v2/auth",
            "kakao,  kauth.kakao.com,     /oauth/authorize"
    })
    void 제공자별로_해당_인증페이지로_리다이렉트한다(String provider, String expectedHost, String expectedPath) {
        String location = RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                .when()
                    .get("/auth/oauth/{provider}/authorize", provider)
                .then()
                    .statusCode(302)
                .extract().header(HttpHeaders.LOCATION);

        URI uri = URI.create(location);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(uri.getScheme()).isEqualTo("https");
            softly.assertThat(uri.getHost()).isEqualTo(expectedHost);
            softly.assertThat(uri.getPath()).isEqualTo(expectedPath);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"GOOGLE", "Google", "KAKAO"})
    void provider의_대소문자가_다르면_400을_반환한다(String provider) {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                .when()
                    .get("/auth/oauth/{provider}/authorize", provider)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNSUPPORTED_PROVIDER"));
    }

    @Test
    void 콜백_성공시_스펙대로_로그인_응답을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("code", "4/0AY0e-g7")
                    .queryParam("state", "f3a1c9")
                .when()
                    .get("/auth/oauth/google/callback")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("accessToken", not(emptyString()))
                    .body("tokenType", equalTo("Bearer"))
                    .body("expiresIn", equalTo(900))

                    .body("user.id", notNullValue())
                    .body("user.name", not(emptyString()))

                    .body("user.provider", equalTo("google"))
                    .body("user.isNewUser", equalTo(false));
    }


    @ParameterizedTest
    @ValueSource(strings = {"google", "kakao"})
    void 콜백_응답의_provider는_요청한_제공자와_같은_소문자다(String provider) {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("code", "4/0AY0e-g7")
                    .queryParam("state", "f3a1c9")
                .when()
                    .get("/auth/oauth/{provider}/callback", provider)
                .then()
                    .statusCode(200)
                    .body("user.provider", equalTo(provider));
    }

    @Test
    void state_없이_콜백을_호출하면_400을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("code", "4/0AY0e-g7")
                .when()
                    .get("/auth/oauth/google/callback")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @ParameterizedTest(name = "code=\"{0}\" state=\"{1}\"")
    @CsvSource(value = {
            "'',        f3a1c9",
            "'   ',     f3a1c9",
            "4/0AY0e-g7, ''",
            "4/0AY0e-g7, '   '"
    })
    void 콜백의_code나_state가_비어있으면_400을_반환한다(String code, String state) {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("code", code)
                    .queryParam("state", state)
                .when()
                    .get("/auth/oauth/google/callback")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)

                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 지원하지_않는_제공자로_콜백을_호출하면_400과_UNSUPPORTED_PROVIDER를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("code", "4/0AY0e-g7")
                    .queryParam("state", "f3a1c9")
                .when()
                    .get("/auth/oauth/unknown/callback")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("UNSUPPORTED_PROVIDER"));
    }

    @Test
    void 재발급_성공시_스펙대로_토큰_응답을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .cookie(REFRESH_TOKEN_COOKIE, "rft_old")
                .when()
                    .post("/auth/refresh")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("accessToken", not(emptyString()))
                    .body("tokenType", equalTo("Bearer"))
                    .body("expiresIn", equalTo(900));
    }


    @Test
    void 콜백은_refreshToken을_auth_경로_쿠키로_발급한다() {
        Response response = RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("code", "4/0AY0e-g7")
                    .queryParam("state", "f3a1c9")
                .when()
                    .get("/auth/oauth/google/callback")
                .then()
                    .statusCode(200)
                .extract().response();

        Cookie issued = response.getDetailedCookie(REFRESH_TOKEN_COOKIE);
        assertThat(issued).isNotNull();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(issued.getValue()).isNotBlank();
            softly.assertThat(issued.getPath()).isEqualTo(REFRESH_TOKEN_COOKIE_PATH);
            softly.assertThat(issued.isHttpOnly()).isTrue();
            softly.assertThat(issued.isSecured()).isTrue();
            softly.assertThat(issued.getSameSite()).isEqualTo("Lax");
        });
    }

    @Test
    void 재발급에_성공하면_보낸_것과_다른_refreshToken_쿠키를_내려준다() {
        String sent = "rft_old";

        Response response = RestAssured
                .given().spec(spec)
                    .cookie(REFRESH_TOKEN_COOKIE, sent)
                .when()
                    .post("/auth/refresh")
                .then()
                    .statusCode(200)
                .extract().response();

        Cookie rotated = response.getDetailedCookie(REFRESH_TOKEN_COOKIE);
        assertThat(rotated).isNotNull();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(sent);
            softly.assertThat(rotated.getPath()).isEqualTo(REFRESH_TOKEN_COOKIE_PATH);
            softly.assertThat(rotated.isHttpOnly()).isTrue();
            softly.assertThat(rotated.isSecured()).isTrue();
            softly.assertThat(rotated.getSameSite()).isEqualTo("Lax");
        });
    }

    @Test
    void 로그아웃하면_204와_만료된_refreshToken_쿠키를_내려준다() {
        Response response = RestAssured
                .given().spec(spec)
                    .cookie(REFRESH_TOKEN_COOKIE, "rft_whatever")
                .when()
                    .post("/auth/logout")
                .then()
                    .statusCode(204)
                .extract().response();

        Cookie expired = response.getDetailedCookie(REFRESH_TOKEN_COOKIE);
        assertThat(expired).isNotNull();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(expired.getMaxAge()).isZero();
            softly.assertThat(expired.getPath()).isEqualTo(REFRESH_TOKEN_COOKIE_PATH);
        });
    }

    @Test
    void 쿠키가_없어도_로그아웃은_204로_멱등하게_동작한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .post("/auth/logout")
                .then()
                    .statusCode(204);
    }


    @Test
    void 콜백이_발급한_쿠키와_로그아웃이_만료시키는_쿠키는_같은_경로다() {
        Cookie issued = RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("code", "4/0AY0e-g7")
                    .queryParam("state", "f3a1c9")
                .when()
                    .get("/auth/oauth/google/callback")
                .then()
                    .statusCode(200)
                .extract().response()
                .getDetailedCookie(REFRESH_TOKEN_COOKIE);

        Cookie expired = RestAssured
                .given().spec(spec)
                    .cookie(REFRESH_TOKEN_COOKIE, issued.getValue())
                .when()
                    .post("/auth/logout")
                .then()
                    .statusCode(204)
                .extract().response()
                .getDetailedCookie(REFRESH_TOKEN_COOKIE);

        assertThat(expired.getPath()).isEqualTo(issued.getPath());
    }

    @Test
    void 지원하지_않는_제공자로_요청하면_400과_UNSUPPORTED_PROVIDER를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                .when()
                    .get("/auth/oauth/unknown/authorize")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(400))
                    .body("code", equalTo("UNSUPPORTED_PROVIDER"))
                    .body("detail", equalTo("지원하지 않는 provider입니다: unknown"))
                    .body("instance", equalTo("/auth/oauth/unknown/authorize"));
    }


    @Test
    void code_없이_콜백을_호출하면_400과_상태코드명_기본_에러코드를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .redirects().follow(false)
                    .queryParam("state", "f3a1c9")
                .when()
                    .get("/auth/oauth/google/callback")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void refreshToken_쿠키가_없으면_401과_INVALID_REFRESH_TOKEN을_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .post("/auth/refresh")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(401))
                    .body("code", equalTo("INVALID_REFRESH_TOKEN"))
                    .body("detail", equalTo("Refresh Token이 유효하지 않습니다."))
                    .body("instance", equalTo("/auth/refresh"));
    }

    @Test
    void refreshToken_쿠키가_공백이어도_같은_401_응답으로_통일된다() {
        RestAssured
                .given().spec(spec)
                    .cookie(REFRESH_TOKEN_COOKIE, "   ")
                .when()
                    .post("/auth/refresh")
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("INVALID_REFRESH_TOKEN"))
                    .body("detail", equalTo("Refresh Token이 유효하지 않습니다."));
    }
}
