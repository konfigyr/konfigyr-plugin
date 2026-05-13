dependencies {
    api(libs.konfigyr.artifactory)
    api(libs.jackson.databind)
    api(libs.spring.configuration.metadata)

    implementation(libs.classmate)
    implementation(libs.javaparser)

    testImplementation(project(":konfigyr-plugin-test"))
    testImplementation(project(":konfigyr-test-application"))
}
