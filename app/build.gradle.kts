plugins {
    id("ticket.spring-boot-application")
}

dependencies {
    implementation(projects.auth)
    implementation(projects.catalog)
    implementation(projects.booking)
    implementation(projects.payment)

    implementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.test)
}