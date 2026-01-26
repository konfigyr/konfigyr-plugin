dependencies {
    api("com.konfigyr:konfigyr-artifactory:1.0.0-RC2")
    api("tools.jackson.core:jackson-databind:3.0.3")
    api("org.springframework:spring-core:7.0.3")
    api("org.springframework.boot:spring-boot-configuration-metadata:4.0.2")

    implementation("com.fasterxml:classmate:1.7.3")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.28.0")

    testImplementation(project(":konfigyr-plugin-test"))
    testImplementation(project(":konfigyr-test-application"))
}
