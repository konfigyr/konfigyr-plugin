dependencies {
    api("com.konfigyr:konfigyr-artifactory:1.0.0")
    api("tools.jackson.core:jackson-databind:3.0.1")
    api("org.springframework:spring-core:6.2.12")
    api("org.springframework.boot:spring-boot-configuration-metadata:3.5.7")

    implementation("com.fasterxml:classmate:1.7.1")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.27.1")

    testImplementation(project(":konfigyr-plugin-test"))
    testImplementation(project(":konfigyr-test-application"))
}