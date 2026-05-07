plugins {
    id("java-gradle-plugin")
    id("maven-publish")
}

dependencies {
    implementation(project(":konfigyr-plugin-core"))

    testImplementation(project(":konfigyr-plugin-test"))
    testImplementation(libs.spring.starter)
}

gradlePlugin {
    plugins {
        create("konfigyr") {
            id = "com.konfigyr"
            displayName = "Konfigyr Gradle Plugin"
            description = "Gradle plugin for uploading Spring Boot configuration metadata to Konfigyr Artifactory"
            implementationClass = "com.konfigyr.gradle.KonfigyrPlugin"
            tags.set(listOf("konfigyr"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("konfigyr") {
            from(components["java"])
        }
    }
}
