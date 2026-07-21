import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ticket.java-common")
    `java-library`
}

dependencies {
    api(platform(SpringBootPlugin.BOM_COORDINATES))
}