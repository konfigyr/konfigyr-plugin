plugins {
    id("idea")
    id("java-library")
    id("io.freefair.lombok") version "9.1.0" apply false
}

subprojects {
    apply(plugin = "idea")
    apply(plugin = "java-library")
    apply(plugin = "io.freefair.lombok")

    repositories {
        mavenCentral()
        mavenLocal()
    }

    group = "com.konfigyr"
    version = "1.0-SNAPSHOT"

    java {
        withJavadocJar()
        withSourcesJar()

        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    dependencies {
        compileOnly("org.slf4j:slf4j-api:2.0.17")

        testImplementation("ch.qos.logback:logback-classic:1.5.26")
        testImplementation("org.assertj:assertj-core:3.27.7")
        testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
        testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.2")
    }

    tasks.test {
        useJUnitPlatform()
    }
}