plugins {
    id("ticket.java-library")
}

dependencies {
    implementation(projects.core)

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // 부트 4 는 자동 설정이 기술별 모듈로 쪼개져 있다.
    // flyway-core 만 있으면 기동할 때 마이그레이션이 돌지 않는다.
    runtimeOnly("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("com.mysql:mysql-connector-j")

    // QueryDSL — 버전은 스프링 부트 BOM 이 관리한다(5.1.0).
    // jakarta 분류자를 붙여야 javax 가 아닌 jakarta.persistence 용이 온다.
    implementation("com.querydsl:querydsl-jpa::jakarta")
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
}
