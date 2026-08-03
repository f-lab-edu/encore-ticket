plugins {
    id("ticket.java-library")
}

dependencies {
    api(projects.catalog)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
}