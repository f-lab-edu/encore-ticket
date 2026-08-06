package com.encore.ticket;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiSpecTestSupport {

    protected static final String PROBLEM_JSON = "application/problem+json";

    protected static final String BEARER_TOKEN = "Bearer test-token";

    protected static final String KST_OFFSET = "+09:00";

    protected static final String KST_DATE_TIME_REGEX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}" + Pattern.quote(KST_OFFSET);

    @LocalServerPort
    protected int port;

    protected RequestSpecification spec;

    @BeforeAll
    static void registerProblemJsonParser() {
        RestAssured.registerParser(PROBLEM_JSON, Parser.JSON);
    }

    @BeforeEach
    void setUpSpec() {
        spec = new RequestSpecBuilder()
                .setPort(port)
                .setContentType(ContentType.JSON)
                .addFilter(new RequestLoggingFilter(LogDetail.ALL))
                .addFilter(new ResponseLoggingFilter(LogDetail.ALL))
                .build();
    }
}
