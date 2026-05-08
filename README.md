# Konfigyr Plugins

![CI Build](https://github.com/konfigyr/konfigyr-plugin/actions/workflows/ci.yml/badge.svg)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.konfigyr.publish-manifest)](https://plugins.gradle.org/plugin/com.konfigyr.publish-manifest)
[![Join the chat at https://gitter.im/konfigyr/konfigyr-plugin](https://badges.gitter.im/konfigyr/konfigyr-plugin.svg)](https://gitter.im/konfigyr/konfigyr-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Build tool plugins for [Konfigyr](https://konfigyr.com). Konfigyr is a platform for centralized configuration management,
auto-generated documentation, and provenance tracking of Spring Boot configuration properties across your services.

## What it does

When you run the plugin, it:

1. Scans your compile and runtime classpaths for `spring-configuration-metadata.json` files produced by the
   [Spring Boot configuration processor](https://docs.spring.io/spring-boot/reference/configuration-metadata/annotation-processor.html).
2. Generates structured metadata for each artifact found.
3. Authenticates with Konfigyr using OAuth2 client credentials.
4. Publishes the metadata and polls until each release is confirmed or the timeout is reached.

Each artifact is uploaded only once, if Konfigyr already has the metadata for a given artifact version, it is skipped.

---

## Gradle Plugin

### Requirements

- Java 21+
- Gradle 9.5+
- A Spring Boot project using `spring-boot-configuration-processor`
- A Konfigyr account with a namespace and OAuth2 client credentials

### Installation

```kotlin
// build.gradle.kts
plugins {
    id("com.konfigyr.publish-manifest") version "1.0.0"
}
```

```groovy
// build.gradle
plugins {
    id 'com.konfigyr.publish-manifest' version '1.0.0'
}
```

### Configuration

```kotlin                                                                                                                                                                                                                                                                 
konfigyr {
    namespace = "acme-corp"
    clientId = "acme-corp-client-id"
    clientSecret = "acme-corp-client-secret"
}
```

The plugin provides the `konfigyr` task that would scan the application classpath for `spring-configuration-metadata.json` files
and upload them to the specified Konfigyr server instance.

#### Available configuration properties

| Property              | Type     | Default                               | Description                                                                             |
|-----------------------|----------|---------------------------------------|-----------------------------------------------------------------------------------------|
| `namespace`           | `String` | `KONFIGYR_NAMESPACE` env var          | Konfigyr namespace your service belongs to.                                             |
| `clientId`            | `String` | `KONFIGYR_CLIENT_ID` env var          | OAuth2 client ID for authentication.                                                    |
| `clientSecret`        | `String` | `KONFIGYR_CLIENT_SECRET` env var      | OAuth2 client secret for authentication.                                                |
| `service`             | `String` | Project name                          | Service name used to group metadata in Konfigyr. Defaults to the Gradle project name.   |
| `host`                | `String` | `https://api.konfigyr.com`            | Base URL of the Konfigyr API. Override when self-hosting.                               |
| `tokenUri`            | `String` | `https://id.konfigyr.com/oauth/token` | OAuth2 token endpoint. Override when self-hosting.                                      |
| `releasePollTimeout`  | `Long`   | `600000` (10 minutes)                 | How long to wait for a release to be confirmed, in milliseconds.                        |
| `releasePollInterval` | `Long`   | `1000` (1 second)                     | Initial polling interval in milliseconds. Uses exponential backoff (×1.75 per attempt). |

#### Authentication via environment variables

Credentials should never be hardcoded in build files. The plugin reads them from environment variables automatically,
so no explicit `konfigyr {}` block is needed in CI if these are set:

```shell
export KONFIGYR_NAMESPACE=acme-corp
export KONFIGYR_CLIENT_ID=acme-corp-client-id
export KONFIGYR_CLIENT_SECRET=acme-corp-client-secret
```

### Running

```shell
./gradlew konfigyr
```

This executes `generateArtifactMetadata` and `publishArtifactMetadata` in order. You can also run each task individually:

```shell
# Scan and write metadata locally — no network calls
./gradlew generateArtifactMetadata

# Generate and then publish to Konfigyr
./gradlew publishArtifactMetadata
```

Generated files are written to `build/konfigyr/` and are fully cacheable, re-running without classpath changes is  instant.

### Tasks

| Task                       | Group      | Description                                                                                |
|----------------------------|------------|--------------------------------------------------------------------------------------------|
| `konfigyr`                 | `konfigyr` | Runs `generateArtifactMetadata` then `publishArtifactMetadata`.                            |
| `generateArtifactMetadata` | `konfigyr` | Scans classpaths and writes metadata JSON files to `build/konfigyr/manifests/`. Cacheable. |
| `publishArtifactMetadata`  | `konfigyr` | Publishes the generated metadata to Konfigyr. Always runs (not cached).                    |

### Multi-project builds

Apply the plugin selectively and share the common configuration at the root:

```kotlin                                                                                                                                                                                                                                                                 
// root build.gradle.kts                                                                                                                                                                                                                              

plugins {
    id("com.konfigyr") version "1.0.0" apply false
}

subprojects {
    apply(plugin = "com.konfigyr")

    konfigyr {
        namespace = "acme-corp"
    }
}
```                                                                                                                                                                                                                                                                       

To publish metadata for all subprojects in one command:

```shell
./gradlew konfigyr
```

### Self-hosted Konfigyr instances                                                                                                                                                                                                                                                  

Override `host` and `tokenUri` when running your own instance:                                                                                                                                                                                                            

```kotlin
konfigyr {
   host     = "https://api.konfigyr.internal"
   tokenUri = "https://id.konfigyr.internal/oauth/token"
}
```

---                                                                                                                                                                                                                                                                       
                                                                                                                                                                                                                                                                          
## Maven Plugin

> **Coming soon.** The Maven plugin is not yet implemented. Contributions are welcome, feel free to open a pull
request or follow [the issue tracker](https://github.com/konfigyr/konfigyr-plugin/issues) for updates.

Once available, it will provide equivalent functionality for Maven projects via a dedicated goal.

---

## License

[Apache 2.0](LICENSE)
