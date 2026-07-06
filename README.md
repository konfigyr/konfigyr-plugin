# Konfigyr Plugins

![CI Build](https://github.com/konfigyr/konfigyr-plugin/actions/workflows/ci.yml/badge.svg)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.konfigyr.artifactory)](https://plugins.gradle.org/plugin/com.konfigyr.artifactory)
[![Join the chat at https://gitter.im/konfigyr/konfigyr-plugin](https://badges.gitter.im/konfigyr/konfigyr-plugin.svg)](https://gitter.im/konfigyr/konfigyr-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Build tool plugins for [Konfigyr](https://konfigyr.com). Konfigyr is a platform for centralized configuration management,
auto-generated documentation, and provenance tracking of Spring Boot configuration properties across your services.

## What it does

Konfigyr builds a searchable, versioned catalog of your organization's Spring Boot configuration
properties - complete with types, defaults, and descriptions - by collecting the metadata
`spring-boot-configuration-processor` already generates for every `@ConfigurationProperties` class.
That catalog is what powers Konfigyr's validation and documentation, and what lets you trace where a
property came from and how its defaults changed across versions. This plugin is how your metadata gets
into that catalog: it runs as part of your normal build, so nobody has to upload anything by hand.

Konfigyr calls each jar it processes an **artifact**, identified by its Maven coordinates (`groupId`,
`artifactId`, `version`) - that includes both your own project's build output and every dependency on
its classpath that exposes Spring Boot configuration metadata of its own.

When you run the plugin, it:

1. Scans your compile and runtime classpaths for `spring-configuration-metadata.json` files produced by the
   [Spring Boot configuration processor](https://docs.spring.io/spring-boot/reference/configuration-metadata/annotation-processor.html).
2. Generates structured metadata for each artifact found.
3. Authenticates with Konfigyr using OAuth2 (client credentials or token exchange).
4. Publishes that metadata to Konfigyr, through one of two scenarios described below.

### Direct artifact publish

Applies to an artifact whose `groupId` has already been **verified** in Konfigyr. Verification happens
in Konfigyr itself, not through this plugin - a namespace admin proves ownership of the coordinate
(e.g. `com.acme.*`); this plugin only reacts to whether that's already been done. Once verified, that
artifact's metadata is published to Konfigyr's shared Artifactory registry, where it becomes reusable by
every service in your organization that depends on it - published once, regardless of how many services
use it.

### Service release

Applies to everything a service depends on that *isn't* covered by a verified `groupId`: that can be the
service's own artifact before its `groupId` is verified, or any dependency - internal or third-party -
your organization doesn't own. Since none of those artifacts can go through direct publish, the service
reports their metadata itself, on its own behalf, as part of its release. That metadata is captured
privately, scoped to and visible only from that one service - it is not added to the shared registry.

These two scenarios are independent, not an either/or choice - a project commonly goes through both at
once (its own verified artifact published directly, its unverified dependencies reported through a
service release). Konfigyr skips artifacts it already has metadata for, so re-running without changes
is cheap. Each build tool section below covers the specific configuration and tasks/goals for both
scenarios.

---

## Gradle Plugin

### Requirements

- Java 21+
- Gradle 9.5+
- A Spring Boot project using `spring-boot-configuration-processor`
- A Konfigyr account with OAuth2 client credentials (a namespace is only needed for the service-release scenario)

### Installation

```kotlin
// build.gradle.kts
plugins {
    id("com.konfigyr.artifactory") version "1.0.0"
}
```

```groovy
// build.gradle
plugins {
    id 'com.konfigyr.artifactory' version '1.0.0'
}
```

### Two publishing scenarios

The `konfigyr` task shown above drives both scenarios from [What it does](#what-it-does). Here's how they
map onto Gradle tasks and configuration - which ones actually run for a given project depends only on
which configuration you've provided (see [Multi-project builds](#multi-project-builds) for an example
of configuring both at once):

|                      | Direct artifact publish                                        | Service release                                                                    |
|----------------------|------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| **Tasks**            | `generateArtifactMetadata` → `publishArtifactMetadata`           | `generateArtifactMetadata` + `resolveServiceDependencies` → `createServiceRelease`    |
| **Required config**  | `clientCredentials { }` or `tokenExchange { }`                   | Credentials, plus `service { namespace = ... }`                                       |
| **Runs when**        | Always, once credentials are configured.                         | Only if `service.namespace` is set - otherwise both tasks are skipped.                |

One asymmetry worth knowing about: `publishArtifactMetadata` isn't gated by extra configuration
the way the service-release tasks are. It always attempts to run once credentials are configured,
and **fails the build** if this project's own artifact exposes Spring Boot configuration metadata
but its `groupId` isn't verified in the Artifactory. If a project should only ever go through the
service-release scenario, disable the direct-publish task explicitly:

```kotlin
tasks.named("publishArtifactMetadata") { enabled = false }
```

Every property and task in the sections below is labeled with which scenario it belongs to:
**shared** (used by both), **publish-only**, or **service-only**.

### Configuration

```kotlin                                                                                                                                                                                                                                                                 
konfigyr {
    clientCredentials {
        clientId = "acme-corp-client-id"
        clientSecret = "acme-corp-client-secret"
    }

    // Optional - only needed if this project should also go through the service-release scenario
    service {
        namespace = "acme-corp"
    }
}
```

The plugin provides the `konfigyr` task that would scan the application classpath for `spring-configuration-metadata.json` files
and upload them to the specified Konfigyr server instance.

#### Credentials

*Shared* - required either way, both scenarios need it to authenticate with Konfigyr. Choose
exactly one OAuth2 grant type. `clientCredentials { }` is the simplest option, authenticating
directly with a long-lived client secret:

```kotlin
konfigyr {
    clientCredentials {
        clientId = "acme-corp-client-id"         // or KONFIGYR_CLIENT_ID env var
        clientSecret = "acme-corp-client-secret" // or KONFIGYR_CLIENT_SECRET env var
    }
}
```

`tokenExchange { }` is the alternative for CI environments that issue short-lived identity tokens (for example an
OIDC token from your CI provider) instead of a long-lived secret:

```kotlin
konfigyr {
    tokenExchange {
        clientId = "acme-corp-client-id"                         // or KONFIGYR_CLIENT_ID env var
        subjectToken = "..."                                     // or KONFIGYR_SUBJECT_TOKEN env var
        subjectTokenType = "urn:ietf:params:oauth:token-type:jwt" // no default, must be set explicitly
    }
}
```

#### Available configuration properties

| Property                         | Scenario        | Type     | Default                                | Description                                                                                        |
|-----------------------------------|-----------------|----------|-----------------------------------------|-----------------------------------------------------------------------------------------------------|
| `host`                           | Shared          | `String` | `https://api.konfigyr.com`              | Base URL of the Konfigyr API. Override when self-hosting.                                           |
| `tokenUri`                       | Shared          | `String` | `https://id.konfigyr.com/oauth/token`   | OAuth2 token endpoint. Override when self-hosting.                                                  |
| `clientCredentials.clientId`     | Shared          | `String` | `KONFIGYR_CLIENT_ID` env var            | OAuth2 client ID for authentication.                                                                 |
| `clientCredentials.clientSecret` | Shared          | `String` | `KONFIGYR_CLIENT_SECRET` env var        | OAuth2 client secret for authentication.                                                             |
| `tokenExchange.clientId`         | Shared          | `String` | `KONFIGYR_CLIENT_ID` env var            | OAuth2 client ID for authentication.                                                                 |
| `tokenExchange.subjectToken`     | Shared          | `String` | `KONFIGYR_SUBJECT_TOKEN` env var        | The token being exchanged for a Konfigyr access token.                                              |
| `tokenExchange.subjectTokenType` | Shared          | `String` | *(required, no default)*                | RFC 8693 token type identifier for the subject token, e.g. `urn:ietf:params:oauth:token-type:jwt`.  |
| `service.namespace`              | Service-only    | `String` | `KONFIGYR_NAMESPACE` env var            | Konfigyr namespace your service belongs to. Setting this is what turns on the service-release scenario. |
| `service.name`                   | Service-only    | `String` | Project name                            | Service name used to group metadata in Konfigyr. Defaults to the Gradle project name.               |
| `publish.pollTimeout`            | Publish-only    | `Long`   | `600000` (10 minutes)                   | How long to wait for a direct-publish release to be confirmed, in milliseconds.                     |
| `publish.pollInterval`           | Publish-only    | `Long`   | `1000` (1 second)                       | Initial polling interval in milliseconds. Uses exponential backoff (×1.75 per attempt).             |

`service { }` and `publish { }` are always available with the defaults above even if you never write the block -
configuring them is only needed to override those defaults, or, for `service { }`, to turn on the service-release
scenario by setting `namespace`. A pure library with no `service.namespace` configured simply skips the
service-release tasks.

#### Authentication via environment variables

Credentials should never be hardcoded in build files. The plugin reads them from environment variables automatically
(this example uses the `client_credentials` grant; `KONFIGYR_SUBJECT_TOKEN` is the equivalent for `tokenExchange { }`):

```shell
export KONFIGYR_NAMESPACE=acme-corp
export KONFIGYR_CLIENT_ID=acme-corp-client-id
export KONFIGYR_CLIENT_SECRET=acme-corp-client-secret
```

Note that even when relying purely on environment variables, the build script must still call `clientCredentials { }`
(an empty body is fine) to select that grant type - see [Credentials](#credentials) above.

### Running

```shell
./gradlew konfigyr
```

This runs the direct-publish scenario (`generateArtifactMetadata` then `publishArtifactMetadata`) always, and the
service-release scenario (`resolveServiceDependencies` then `createServiceRelease`) only if `service.namespace` is
set. You can also run each task individually:

```shell
# Scan and write metadata locally — no network calls
./gradlew generateArtifactMetadata

# Generate and then publish this project's own metadata directly to Konfigyr
./gradlew publishArtifactMetadata

# Resolve dependency metadata and open a service release (requires service.namespace to be set)
./gradlew createServiceRelease
```

Generated files are written under `build/konfigyr/` and are fully cacheable, re-running without classpath changes is
instant.

### Tasks

| Task                          | Scenario       | Group      | Description                                                                                          |
|--------------------------------|----------------|------------|--------------------------------------------------------------------------------------------------------|
| `konfigyr`                    | Both           | `konfigyr` | Umbrella task; runs `publishArtifactMetadata` and `createServiceRelease` (which pulls in the other two as needed). |
| `generateArtifactMetadata`    | Shared         | `konfigyr` | Scans this project's own built jar and writes its metadata to `build/konfigyr/metadata.json`, if it exposes any. Feeds both scenarios. Cacheable. |
| `publishArtifactMetadata`     | Publish-only   | `konfigyr` | Publishes this project's own generated metadata directly to Konfigyr. Always runs (not cached) - see the note above about disabling it if unwanted. |
| `resolveServiceDependencies`  | Service-only   | `konfigyr` | Scans this service's dependencies for configuration metadata. Only runs if `service.namespace` is set. Cacheable. |
| `createServiceRelease`        | Service-only   | `konfigyr` | Opens a service release and uploads required dependency (and own) metadata. Only runs if `service.namespace` is set. Always runs (not cached). |

### Multi-project builds

Apply the plugin selectively and share the common configuration at the root:

```kotlin                                                                                                                                                                                                                                                                 
// root build.gradle.kts                                                                                                                                                                                                                              

plugins {
    id("com.konfigyr.artifactory") version "1.0.0" apply false
}

subprojects {
    apply(plugin = "com.konfigyr.artifactory")

    konfigyr {
        clientCredentials {
            clientId = "acme-corp-client-id"
            clientSecret = "acme-corp-client-secret"
        }

        // Optional - only for subprojects that should also go through the service-release scenario
        service {
            namespace = "acme-corp"
        }
    }
}
```

The connection to Konfigyr is a single build-wide service, so every subproject configuring the plugin must resolve
to identical `host`/`tokenUri`/credentials - as they do above, since `subprojects { }` applies the same block to all
of them. If subprojects end up with different connection settings, the build fails rather than picking one silently.
Configuring the root project's own `konfigyr { }` block directly (instead of via `subprojects { }`) is what lets you
set connection details in exactly one place.

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
