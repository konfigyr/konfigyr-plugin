dependencies {
    api("org.mockito:mockito-junit-jupiter:5.21.0")
    api("org.wiremock:wiremock:3.13.2")

    compileOnly(project(":konfigyr-plugin-core"))

    compileOnly("org.assertj:assertj-core:3.27.7")
    compileOnly("org.junit.jupiter:junit-jupiter:6.0.2")
}
