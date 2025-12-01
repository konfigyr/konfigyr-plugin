plugins {
    id("java")
}

group = "com.konfigyr"

repositories {
    mavenCentral()
}

dependencies {
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    implementation("org.springframework.boot:spring-boot-starter:4.0.0")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.0.0")
}
