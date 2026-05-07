plugins {
    id("java")
}

group = "com.konfigyr"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))

    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")

    compileOnly("org.springframework.boot:spring-boot-starter-cache")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jdbc")

    annotationProcessor(libs.spring.configuration.processor)
}
