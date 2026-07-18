plugins {
    `kotlin-dsl`
}

group = "com.encore.ticket.build-logic"

dependencies {
    implementation(
        "org.springframework.boot:spring-boot-gradle-plugin:" +
                libs.versions.spring.boot.get()
    )
}