plugins {
    id("ticket.java-library")
}

dependencies {
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
}