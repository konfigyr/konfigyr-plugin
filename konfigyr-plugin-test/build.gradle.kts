dependencies {
    api("org.mockito:mockito-junit-jupiter:5.20.0")
    api("org.wiremock:wiremock:3.13.1")

    compileOnly(project(":konfigyr-plugin-core"))

    compileOnly("org.assertj:assertj-core:3.27.6")
    compileOnly("org.junit.jupiter:junit-jupiter:6.0.1")
}
