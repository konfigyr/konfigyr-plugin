plugins {
    id("idea")
    id("java-library")

    alias(libs.plugins.lombok) apply false
}

subprojects {
    apply(plugin = "idea")
    apply(plugin = "java-library")
    apply(plugin = "io.freefair.lombok")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    group = "com.konfigyr"
    version = "1.0.0"

    java {
        withJavadocJar()
        withSourcesJar()

        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        compileOnly(rootProject.libs.slf4j)

        testImplementation(rootProject.libs.logback)
        testImplementation(rootProject.libs.assertj)
        testImplementation(rootProject.libs.junit)
        testImplementation(rootProject.libs.junit.params)
        testRuntimeOnly(rootProject.libs.junit.launcher)
    }

    tasks.test {
        useJUnitPlatform()
    }
}