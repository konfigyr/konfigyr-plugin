plugins {
    id("java")
}

group = "com.konfigyr"

repositories {
    mavenCentral()
}

dependencies {
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    implementation("org.springframework.boot:spring-boot-starter:3.5.7")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.5.7")
}
