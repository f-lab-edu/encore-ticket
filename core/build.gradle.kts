plugins {
    id("ticket.java-library")
}

dependencies {
    implementation(libs.jackson.annotations)

    compileOnly("org.springframework:spring-context")

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit5)
}
