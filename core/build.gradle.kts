plugins {
    id("ticket.java-library")
}

dependencies {
    implementation(libs.jackson.annotations)

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit5)
}
