plugins {
    id("ticket.java-library")
}

dependencies {
    implementation(libs.jackson.annotations)
    api(libs.spring.web)
}