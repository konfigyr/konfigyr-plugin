dependencies {
    api(libs.junit.mockito)
    api(libs.wiremock)

    compileOnly(project(":konfigyr-plugin-core"))
    compileOnly(libs.assertj)
    compileOnly(libs.junit)
}
