import org.gradle.plugin.compatibility.compatibility

plugins {
    id("signing")
    alias(libs.plugins.shadow)
    alias(libs.plugins.gradle.publish)
    alias(libs.plugins.gradle.compatibility)
}

dependencies {
    implementation(project(":konfigyr-plugin-core"))

    shadow(libs.spring.configuration.metadata)
    shadow(libs.javaparser)

    testImplementation(project(":konfigyr-plugin-test"))
    testImplementation(libs.spring.starter)
}

gradlePlugin {
    website = "https://github.com/konfigyr/konfigyr-plugin"
    vcsUrl = "https://github.com/konfigyr/konfigyr-plugin"
    plugins {
        register("konfigyr") {
            id = "com.konfigyr.artifactory"
            displayName = "Konfigyr Configuration Publisher"
            description = "Extracts Spring Boot @ConfigurationProperties metadata and publishes it to Konfigyr, enabling centralised configuration management, auto-generated documentation, and provenance tracking across your services."
            implementationClass = "com.konfigyr.gradle.KonfigyrPlugin"
            tags.set(listOf(
                "konfigyr",
                "spring-boot",
                "configuration",
                "configuration-properties",
                "configuration-metadata",
                "documentation",
                "provenance"
            ))

            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        providers.environmentVariable("GPG_SIGNING_KEY").orNull,
        providers.environmentVariable("GPG_SIGNING_SECRET").orNull
    )
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()

    dependencies {
        exclude(dependency(libs.spring.configuration.metadata))
        exclude(dependency(libs.slf4j))

        /* Exclude javaparser and all of transitive dependencies */
        exclude(dependency("com.github.javaparser:.*"))
        exclude(dependency("org.javassist:.*"))
        exclude(dependency("org.jspecify:.*"))
        exclude(dependency("com.google.*:.*"))
        exclude(dependency("org.checkerframework:.*"))
        exclude(dependency("commons-logging:.*"))
    }

    relocate("com.fasterxml", "com.konfigyr.shadow.com.fasterxml")
    relocate("tools.jackson", "com.konfigyr.shadow.tools.jackson")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.withType(Sign::class).configureEach {
    onlyIf("signing keys are specified") {
        System.getenv("GPG_SIGNING_KEY") != null && System.getenv("GPG_SIGNING_SECRET") != null
    }
}
