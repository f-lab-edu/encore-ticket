import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ticket.java-common")
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
}