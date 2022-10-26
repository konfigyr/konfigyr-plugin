# Konfigyr plugins

![CI Build](https://github.com/konfigyr/konfigyr-plugin/actions/workflows/ci.yml/badge.svg)
[![Join the chat at https://gitter.im/konfigyr/konfigyr-plugin](https://badges.gitter.im/konfigyr/konfigyr-plugin.svg)](https://gitter.im/konfigyr/konfigyr-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Gradle and Maven Plugins for the Konfigyr Config Server. You can use the plugins to upload the Spring Boot Property metadata files to your Konfigyr application.

## Gradle

To use the Gradle plugin simply apply the `com.konfigyr` plugin on your project and configure the Konfigyr host and access token.

```groovy
plugins {
    id 'java'
    id 'com.konfigyr'
    id 'org.springframework.boot' version '2.7.5'
    id 'io.spring.dependency-management' version '1.0.11.RELEASE'
}

group 'com.acme'
version '1.0.0'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.zalando:logbook-spring-boot-starter'
    
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
}

konfigyr {
    token = 'your-konfigyr-access-token'
    host = 'https://my-konfigyr-instance.com'
}
```

The plugin provides the `konfigyr` task that would scan the application classpath for `spring-configuration-metadata.json` files and upload them to the specified Konfigyr server instance.

```shell
./gradlew konfigyr
```

## Maven

Maven is not yet supported, you are welcome to open a PR.


### Modules

There are several modules in this project. Here is a quick overview:

#### konfigyr-plugin-core

Module providing the basic logic for plugins. The focal part here is the `ConfigurationMetadataUploader` interface which can be created using the `ConfigurationMetadataUploaderBuilder`.

```java

final ConfigurationMetadataUploader uploader = ConfigurationMetadataUploaderBuilder.create()
        .host("https://my-konfigyr-instance.com")
        .token("your-konfigyr-access-token")
        // specify the list of jar files that are considered as a part of the classpath
        .classpath(new File("com"))
        // define an artifict and it's configuration metadata file that should be uploaded 
        .artifact(
                new Artifact("com.acme", "acme", "1.0.0"),
                new File("spring-configuration-metadata.json")
        )
        .artifact(
                new Artifact("com.acme", "spring", "1.0.0"),
                new File("spring-configuration-metadata.json")
        )
        .build();

final List<ConfigurationMetadata> metadata = uploader.upload();
```

#### konfigyr-plugin-gradle

Module that implements and defines the Gradle Plugin for the Konfigyr Metadata upload.

#### konfigyr-plugin-test

Module containing testing utilities and helpers.