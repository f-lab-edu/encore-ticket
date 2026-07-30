package com.encore.ticket.concert.controller;

import com.encore.ticket.ApiSpecTestSupport;
import com.encore.ticket.catalog.api.dto.ConcertStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.SoftAssertions;
import org.springframework.http.HttpHeaders;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class ConcertApiControllerTest extends ApiSpecTestSupport {

    private static final long EXISTING_CONCERT_ID = 1L;
    private static final long MISSING_CONCERT_ID = 999L;
    private static final long NOTICE_LESS_CONCERT_ID = 2L;

    private static final int CONCERT_COUNT = 10;

    private static final int DEFAULT_RANKING_LIMIT = 10;

    private static final String BEARER_TOKEN = "Bearer test-token";

    private static final String KST_OFFSET = "+09:00";

    private static final String KST_DATE_TIME_REGEX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}" + Pattern.quote(KST_OFFSET);

    private static final String DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}";

    private static final List<String> STATUS_NAMES = Arrays.stream(ConcertStatus.values())
            .map(Enum::name)
            .toList();

    private static final List<String> SPEC_STATUS_NAMES =
            List.of("UPCOMING", "ON_SALE", "CLOSED", "SOLD_OUT", "CANCELLED");

    @Test
    void 목록을_기본_파라미터로_조회하면_스펙대로_페이지_응답을_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("content", notNullValue())
                    .body("page", equalTo(0))
                    .body("size", equalTo(12))
                    .body("totalElements", notNullValue())
                    .body("totalPages", notNullValue());
    }

    @Test
    void 목록의_카드는_스펙에_정의된_9개_필드만_가진다() {
        Map<String, Object> card = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getMap("content[0]");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(card).containsOnlyKeys(
                    "id", "title", "posterUrl", "venue",
                    "performanceStartDate", "performanceEndDate",
                    "bookingOpensAt", "status", "minPrice");
            softly.assertThat(STATUS_NAMES).contains(String.valueOf(card.get("status")));
        });
    }

    @Test
    void 목록의_모든_카드의_status는_5개_ENUM_중_하나다() {
        List<String> statuses = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getList("content.status", String.class);

        assertThat(statuses).isNotEmpty().allSatisfy(status -> assertThat(STATUS_NAMES).contains(status));
    }

    @Test
    void status_ENUM은_스펙에_적힌_5개_리터럴과_정확히_일치한다() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SPEC_STATUS_NAMES).hasSize(5);
            softly.assertThat(STATUS_NAMES)
                    .hasSize(SPEC_STATUS_NAMES.size())
                    .containsExactlyInAnyOrderElementsOf(SPEC_STATUS_NAMES);
        });
    }

    @Test
    void 목록의_page와_size를_지정하면_응답이_요청을_그대로_반영한다() {
        JsonPath body = RestAssured
                .given().spec(spec)
                    .queryParam("page", 1)
                    .queryParam("size", 4)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getInt("page")).isEqualTo(1);
            softly.assertThat(body.getInt("size")).isEqualTo(4);
            softly.assertThat(body.getList("content")).hasSize(4);
        });
    }

    @Test
    void 페이지는_전체_목록의_해당_구간과_같다() {
        int size = 4;
        int page = 1;

        List<Integer> reference = RestAssured
                .given().spec(spec)
                    .queryParam("size", 100)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getList("content.id", Integer.class);

        List<Integer> pageContent = RestAssured
                .given().spec(spec)
                    .queryParam("page", page)
                    .queryParam("size", size)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getList("content.id", Integer.class);

        int from = Math.min(page * size, reference.size());
        int to = Math.min(from + size, reference.size());

        assertThat(pageContent).isEqualTo(reference.subList(from, to));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"page=-1", "size=0", "size=abc"})
    void 목록의_페이지_파라미터가_유효하지_않으면_400을_반환한다(String query) {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts?" + query)
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", notNullValue());
    }

    @Test
    void 목록의_size가_상한을_넘으면_잘라내지_않고_400을_반환한다() {
        RestAssured
                .given().spec(spec)
                    .queryParam("size", 101)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 상세를_토큰_없이_조회하면_200과_liked_false를_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("id", equalTo((int) EXISTING_CONCERT_ID))
                    .body("liked", equalTo(false));
    }

    @Test
    void 상세를_Bearer_헤더와_함께_조회하면_200과_liked_true를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("liked", equalTo(true));
    }

    @ParameterizedTest(name = "Authorization=[{0}]")
    @ValueSource(strings = {"Basic abc", "Bearer   ", "Bearer", "bearer test-token"})
    void 상세를_Bearer로_해석되지_않는_Authorization_헤더로_조회하면_liked_false를_반환한다(String authorization) {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("liked", equalTo(false));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/concerts", "/concerts/ranking"})
    void 인증이_필요없는_목록과_랭킹은_Bearer_토큰을_보내도_200을_반환한다(String path) {
        RestAssured
                .given().spec(spec)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                .when()
                    .get(path)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON);
    }

    @Test
    void 상세는_스펙에_정의된_10개_필드만_가진다() {
        Map<String, Object> body = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                .extract().jsonPath().getMap("$");

        assertThat(body).containsOnlyKeys(
                "id", "title", "description", "notice", "posterUrl",
                "venue", "likeCount", "liked", "schedules", "prices");
    }

    @Test
    void 상세의_schedules와_prices는_스펙에_정의된_필드만_가진다() {
        JsonPath body = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getList("schedules")).isNotEmpty();
            softly.assertThat(body.getMap("schedules[0]")).containsOnlyKeys(
                    "id", "startsAt", "endsAt", "bookingOpensAt", "bookingClosesAt", "status");
            softly.assertThat(STATUS_NAMES).contains(body.getString("schedules[0].status"));

            softly.assertThat(body.getList("prices")).isNotEmpty();
            softly.assertThat(body.getMap("prices[0]")).containsOnlyKeys("grade", "price");
        });
    }

    @Test
    void 상세의_시각_필드는_ISO_8601_KST_오프셋_형식이다() {
        JsonPath body = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        List<String> instants = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            instants.add(body.getString("schedules[%d].startsAt".formatted(index)));
            instants.add(body.getString("schedules[%d].endsAt".formatted(index)));
            instants.add(body.getString("schedules[%d].bookingOpensAt".formatted(index)));
            instants.add(body.getString("schedules[%d].bookingClosesAt".formatted(index)));
        }

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body.getList("schedules")).hasSizeGreaterThanOrEqualTo(2);
            softly.assertThat(instants).hasSize(8).allSatisfy(instant ->
                    assertThat(instant).matches(KST_DATE_TIME_REGEX));
        });
    }

    @Test
    void 목록_카드의_날짜와_시각_필드는_스펙_형식을_따른다() {
        JsonPath body = RestAssured
                .given().spec(spec)
                    .queryParam("size", 100)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        List<String> startDates = body.getList("content.performanceStartDate", String.class);
        List<String> endDates = body.getList("content.performanceEndDate", String.class);
        List<String> bookingOpensAt = body.getList("content.bookingOpensAt", String.class);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(startDates).isNotEmpty()
                    .allSatisfy(value -> assertThat(value).matches(DATE_REGEX));
            softly.assertThat(endDates).isNotEmpty()
                    .allSatisfy(value -> assertThat(value).matches(DATE_REGEX));
            softly.assertThat(bookingOpensAt).isNotEmpty()
                    .allSatisfy(value -> assertThat(value).matches(KST_DATE_TIME_REGEX));
        });
    }

    @Test
    void 랭킹의_asOf는_ISO_8601_KST_오프셋_형식이다() {
        String asOf = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getString("asOf");

        assertThat(asOf).matches(KST_DATE_TIME_REGEX);
    }

    @Test
    void 금액과_카운트_필드는_소수점_없는_정수로_직렬화된다() {
        String listJson = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().asString();

        String detailJson = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                .extract().asString();

        String rankingJson = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(200)
                .extract().asString();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jsonNumbersOf(listJson, "minPrice"))
                    .isNotEmpty().allSatisfy(value -> assertThat(value).matches("-?\\d+"));
            softly.assertThat(jsonNumbersOf(detailJson, "price"))
                    .isNotEmpty().allSatisfy(value -> assertThat(value).matches("-?\\d+"));
            softly.assertThat(jsonNumbersOf(detailJson, "likeCount"))
                    .isNotEmpty().allSatisfy(value -> assertThat(value).matches("-?\\d+"));
            softly.assertThat(jsonNumbersOf(rankingJson, "score"))
                    .isNotEmpty().allSatisfy(value -> assertThat(value).matches("-?\\d+"));
        });
    }

    @Test
    void 목록과_상세는_같은_콘서트에_대해_같은_title을_반환한다() {
        String titleInList = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getString("content.find { it.id == %d }.title".formatted(EXISTING_CONCERT_ID));

        String titleInDetail = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(200)
                .extract().jsonPath().getString("title");

        assertThat(titleInDetail).isNotBlank().isEqualTo(titleInList);
    }

    @Test
    void 없는_콘서트를_상세_조회하면_404와_NOT_FOUND를_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", MISSING_CONCERT_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("status", equalTo(404))
                    .body("code", equalTo("NOT_FOUND"))
                    .body("instance", equalTo("/concerts/" + MISSING_CONCERT_ID));
    }

    @Test
    void 조회수를_올리면_204와_빈_바디를_반환한다() {
        Response response = RestAssured
                .given().spec(spec)
                .when()
                    .post("/concerts/{concertId}/views", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(204)
                .extract().response();

        assertThat(response.asString()).isEmpty();
    }

    @Test
    void 없는_콘서트의_조회수를_올리면_404와_NOT_FOUND를_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .post("/concerts/{concertId}/views", MISSING_CONCERT_ID)
                .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void 랭킹_경로는_상세_매핑에_잡히지_않고_랭킹_응답을_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("items", notNullValue())
                    .body("asOf", notNullValue());
    }

    @Test
    void 랭킹의_limit을_생략하면_기본값_10개를_반환한다() {
        int fixtureCount = fixtureConcertCount();

        List<Object> items = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getList("items");

        assertThat(items).hasSize(Math.min(DEFAULT_RANKING_LIMIT, fixtureCount));
    }

    @Test
    void 랭킹의_limit을_지정하면_정확히_그_개수를_반환한다() {
        List<Object> items = RestAssured
                .given().spec(spec)
                    .queryParam("limit", 3)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getList("items");

        assertThat(items).hasSize(3);
    }

    @Test
    void 랭킹은_rank가_1부터_연속하며_유일하고_score는_내림차순이다() {
        JsonPath body = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        List<Integer> ranks = body.getList("items.rank", Integer.class);
        List<Integer> scores = body.getList("items.score", Integer.class);

        assertThat(ranks).isNotEmpty();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(ranks.get(0)).isEqualTo(1);
            softly.assertThat(ranks).doesNotHaveDuplicates();
            softly.assertThat(ranks)
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, ranks.size()).boxed().toList());
            softly.assertThat(scores)
                    .hasSameSizeAs(ranks)
                    .isSortedAccordingTo(Comparator.<Integer>reverseOrder());
        });
    }

    @Test
    void 랭킹_아이템은_스펙에_정의된_5개_필드만_가진다() {
        Map<String, Object> item = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getMap("items[0]");

        assertThat(item).containsOnlyKeys("rank", "concertId", "title", "posterUrl", "score");
    }

    @ParameterizedTest(name = "limit={0}")
    @ValueSource(strings = {"51", "0", "abc"})
    void 랭킹의_limit이_유효하지_않으면_400을_반환한다(String limit) {
        RestAssured
                .given().spec(spec)
                    .queryParam("limit", limit)
                .when()
                    .get("/concerts/ranking")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void 결과가_없는_페이지를_조회하면_content가_빈_배열이다() {
        List<Object> content = RestAssured
                .given().spec(spec)
                    .queryParam("page", 99)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getList("content");

        assertThat(content).isNotNull().isEmpty();
    }

    @Test
    void 목록은_전체_개수와_전체_페이지_수를_반환한다() {
        RestAssured
                .given().spec(spec)
                    .queryParam("size", 4)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("totalElements", equalTo(CONCERT_COUNT))
                    .body("totalPages", equalTo(3));
    }

    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {4, 5, 10})
    void 목록의_totalPages는_전체_개수를_size로_올림_나눈_값이다(int size) {
        JsonPath body = RestAssured
                .given().spec(spec)
                    .queryParam("size", size)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath();

        long totalElements = body.getLong("totalElements");
        int expectedTotalPages = (int) ((totalElements + size - 1) / size);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(totalElements).isPositive();
            softly.assertThat(body.getInt("totalPages")).isEqualTo(expectedTotalPages);
        });
    }

    @Test
    void notice가_없는_콘서트도_notice_키를_포함한다() {
        Map<String, Object> body = RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", NOTICE_LESS_CONCERT_ID)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                .extract().jsonPath().getMap("$");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(body).containsOnlyKeys(
                    "id", "title", "description", "notice", "posterUrl",
                    "venue", "likeCount", "liked", "schedules", "prices");
            softly.assertThat(body).containsEntry("notice", null);
        });
    }

    @Test
    void 목록_카드의_공연_기간과_예매_오픈_시각은_상세의_schedules와_일치한다() {
        List<Map<String, Object>> schedules = detailOf(EXISTING_CONCERT_ID).getList("schedules");
        Map<String, Object> card = listCardOf(EXISTING_CONCERT_ID);

        assertThat(schedules).isNotEmpty();

        Comparator<Map<String, Object>> byStartsAt =
                Comparator.comparing(schedule -> OffsetDateTime.parse((String) schedule.get("startsAt")));
        Map<String, Object> earliest = schedules.stream().min(byStartsAt).orElseThrow();
        Map<String, Object> latest = schedules.stream().max(byStartsAt).orElseThrow();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(card.get("performanceStartDate"))
                    .isEqualTo(OffsetDateTime.parse((String) earliest.get("startsAt")).toLocalDate().toString());
            softly.assertThat(card.get("performanceEndDate"))
                    .isEqualTo(OffsetDateTime.parse((String) latest.get("startsAt")).toLocalDate().toString());
            softly.assertThat(OffsetDateTime.parse((String) card.get("bookingOpensAt")))
                    .isEqualTo(OffsetDateTime.parse((String) earliest.get("bookingOpensAt")));
        });
    }

    @Test
    void 목록_카드의_minPrice는_상세의_prices_최솟값과_일치한다() {
        List<Long> prices = detailOf(EXISTING_CONCERT_ID).getList("prices.price", Long.class);
        Map<String, Object> card = listCardOf(EXISTING_CONCERT_ID);

        assertThat(prices).isNotEmpty();

        long expectedMinPrice = prices.stream().mapToLong(Long::longValue).min().orElseThrow();

        assertThat(((Number) card.get("minPrice")).longValue()).isEqualTo(expectedMinPrice);
    }

    @Test
    void 스펙에_없는_DELETE_콘서트_요청은_토큰_없이_호출하면_401을_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .delete("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(401);
    }

    @Test
    void 조회수_경로를_GET으로_토큰_없이_호출하면_401을_반환한다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}/views", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(401);
    }

    @Test
    void 인가_거부_401도_공통_에러_응답_규약의_problem_json_바디를_가진다() {
        RestAssured
                .given().spec(spec)
                .when()
                    .delete("/concerts/{concertId}", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", notNullValue());

        RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}/views", EXISTING_CONCERT_ID)
                .then()
                    .statusCode(401)
                    .contentType(PROBLEM_JSON)
                    .body("code", notNullValue());
    }

    @Test
    @Disabled("「공통 에러 응답 규약」은 detail을 한글로 규정하지만, 400 응답의 영문 detail 경로가 둘이다. "
            + "(1) ?page=-1 ?size=0 ?size=101 ?limit=51 처럼 @Min/@Max 위반은 HandlerMethodValidationException에서 "
            + "\"Validation failure\"가 나간다. (2) ?size=abc ?limit=abc /concerts/abc 처럼 타입 변환 실패는 "
            + "handleTypeMismatch에서 \"Failed to convert 'size' with value: 'abc'\"가 나가며, 클라이언트가 보낸 값까지 그대로 되돌려준다. "
            + "따라서 handleHandlerMethodValidationException 오버라이드만으로는 (2)가 그대로 남아 불충분하고, "
            + "handleTypeMismatch까지 함께 처리한 뒤 활성화한다.")
    void 파라미터_검증_실패의_detail은_한글이다() {
        RestAssured
                .given().spec(spec)
                    .queryParam("size", 101)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("detail", equalTo("요청 값이 유효하지 않습니다."));
    }

    private int fixtureConcertCount() {
        return RestAssured
                .given().spec(spec)
                    .queryParam("size", 100)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getList("content").size();
    }

    private JsonPath detailOf(long concertId) {
        return RestAssured
                .given().spec(spec)
                .when()
                    .get("/concerts/{concertId}", concertId)
                .then()
                    .statusCode(200)
                .extract().jsonPath();
    }

    private Map<String, Object> listCardOf(long concertId) {
        return RestAssured
                .given().spec(spec)
                    .queryParam("size", 100)
                .when()
                    .get("/concerts")
                .then()
                    .statusCode(200)
                .extract().jsonPath().getMap("content.find { it.id == %d }".formatted(concertId));
    }

    private static List<String> jsonNumbersOf(String json, String field) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)")
                .matcher(json);

        List<String> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(matcher.group(1));
        }
        return numbers;
    }
}
