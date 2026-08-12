plugins {
    id("ticket.spring-boot-application")
}

dependencies {
    implementation(projects.core)

    implementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.rest.assured)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
}