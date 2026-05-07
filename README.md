# Konfigyr plugins

![CI Build](https://github.com/konfigyr/konfigyr-plugin/actions/workflows/ci.yml/badge.svg)
[![Join the chat at https://gitter.im/konfigyr/konfigyr-plugin](https://badges.gitter.im/konfigyr/konfigyr-plugin.svg)](https://gitter.im/konfigyr/konfigyr-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Gradle and Maven Plugins for the Konfigyr Config Server. You can use the plugins to upload the Spring Boot Property metadata files to your Konfigyr application.

## Gradle

To use the Gradle plugin, apply the `com.konfigyr` plugin on your project and configure the Konfigyr host and access token.

```groovy
plugins {
    id 'com.konfigyr'
}

konfigyr {
    token = 'your-konfigyr-access-token'
    host = 'https://my-konfigyr-instance.com'
    namespace = 'your-namespace'
    service = 'your-service'
    clientId = 'konfigyr-client-id'
    clientSecret = 'konfigyr-client-secret'
}
```

The plugin provides the `konfigyr` task that would scan the application classpath for `spring-configuration-metadata.json` files and upload them to the specified Konfigyr server instance.

```shell
./gradlew konfigyr
```

## Maven

Maven is not yet supported, you are welcome to open a PR.
