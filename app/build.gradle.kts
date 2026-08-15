import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ticket.spring-boot-application")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(projects.core)

    implementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.rest.assured)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("com.mysql:mysql-connector-j")
}
