# Konfigyr Plugins

![CI Build](https://github.com/konfigyr/konfigyr-plugin/actions/workflows/ci.yml/badge.svg)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.konfigyr.artifactory)](https://plugins.gradle.org/plugin/com.konfigyr.artifactory)
[![Join the chat at https://gitter.im/konfigyr/konfigyr-plugin](https://badges.gitter.im/konfigyr/konfigyr-plugin.svg)](https://gitter.im/konfigyr/konfigyr-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**Catch a bad Spring Boot property before it ships, not after.** [Konfigyr](https://konfigyr.com) turns the
configuration metadata your project already generates into a searchable catalog with type-safe validation,
version history, and provenance for every property. That way, a typo'd key or an invalid value gets caught
before a change is merged, not after it's paged someone.

This repository contains build plugins that publish your project's Spring Boot configuration metadata
to Konfigyr as part of your normal build. The Gradle (and, soon, Maven) plugin will scan your project,
and your project's own dependencies, and send Spring Boot configuration metadata to Konfigyr.

## Quick Start

**Requirements:** Java 21+, Gradle 9.5+, and a Spring Boot project using `spring-boot-configuration-processor`.

> **Building a deployable Spring Boot service?** This is almost certainly you. Read on.
> **Building a library other teams will depend on?** You can skip the `service { }` block entirely. See
> "Publishing a library" under [Gradle Plugin](#gradle-plugin) below.

1. Apply the plugin:

    ```kotlin
    plugins {
        id("com.konfigyr.artifactory") version "1.2.0"
    }
    ```

2. Configure a registry where to publish to:

    ```kotlin
    konfigyr {
        registries {
            konfigyrCentral()
        }
    }
    ```

    > Credentials are discovered automatically from the environment or via explicit registry configuration.

3. Name your service. It must match the identifier this service already has in the Konfigyr app; the
   plugin releases against an existing service, it doesn't create one:

    ```kotlin
    konfigyr {
        registries {
            konfigyrCentral()
        }

        service {
            name = "order-service"
        }
    }
    ```

4. Run it, as part of your normal release/deploy pipeline:

    ```shell
    ./gradlew konfigyr
    ```

That's it. Konfigyr now has a full picture of every configuration property your service exposes, both
from your own code and from everything it depends on (see [How it works](#how-it-works) below). Everything
past this point is about handling more advanced situations: publishing a library your own team owns,
multiple registries, CI-issued tokens instead of a secret, and multi-module builds.

## How it works

Konfigyr deals with two distinct kinds of things, and the plugin's two publishing mechanisms each target one
of them:

- An **artifact** is a jar identified by Maven coordinates (`groupId`, `artifactId`, `version`). It's the
  unit that carries configuration metadata: the `spring-configuration-metadata.json` generated whenever a
  jar declares `@ConfigurationProperties` classes.
- A **service** is a namespace-owned, deployable application already registered in Konfigyr. It's the
  thing your organization actually runs, with a configuration catalog assembled from everything it depends on.

Your project's own build output is always an artifact. If it's also meant to be deployed and run, it's a
service too, and each of those two identities is handled by a different plugin mechanism:

- **Direct artifact publish** targets the *artifact* side. Any artifact, whether that's a shared library or
  a service's own jar, can be published directly to Konfigyr's shared registry, but only once its `groupId`
  is **verified** (a namespace admin has proven ownership of the coordinate, e.g. `com.acme.*`, directly in
  Konfigyr). Once verified, that artifact's metadata is published once per version and reused by every
  service in your organization that depends on it.
- **Releasing** targets the *service* side. Configuring `service { }` opts a project into releasing: every
  time the service is released (tied to its own deploy/release cadence), the plugin reports its *entire*
  dependency graph. That means every classpath artifact that exposes configuration metadata, including the
  service's own jar as just one more entry. Konfigyr resolves as much of that graph as it can from the
  shared registry, verified or not, and only asks the plugin to upload metadata for whatever it can't
  otherwise resolve. That upload is captured privately, visible only from that one service, and never added
  to the shared registry.

In practice, **most projects are services, not libraries**. If you're deploying a Spring Boot application,
you almost always want `service { }` configured, since it's what builds your service's dependency catalog,
whether or not your own code defines any configuration properties. Direct publish only matters *on its own*
for the minority of projects that are themselves libraries other teams verify and depend on.

The two mechanisms aren't a one-time either/or choice, and they don't fight each other. A service's own
artifact commonly gets both: direct-published once it's verified, and included in every release regardless.
Once it's verified, later releases automatically stop needing to upload it locally, since Konfigyr can now
resolve it from the shared registry. Nothing needs to be re-configured for that transition to happen.

---

## Gradle Plugin

<details>
<summary><strong>Configure a Konfigyr registry</strong></summary>

Everything the plugin publishes goes to one or more **registries**, declared in a `registries { }` block.
Each registry needs a `url` and exactly one OAuth2 grant. Its token endpoint is discovered automatically at
build time, so you never configure it directly.

**`konfigyrCentral()`** is the reserved, well-known Konfigyr registry. Its `url` defaults to
`https://api.konfigyr.com`, and unset credentials fall back to environment variables:

```kotlin
konfigyr {
    registries {
        konfigyrCentral() // KONFIGYR_CLIENT_ID / KONFIGYR_CLIENT_SECRET / KONFIGYR_SUBJECT_TOKEN
    }
}
```

To set credentials explicitly instead (never hardcode a real secret in a committed build file):

```kotlin
konfigyr {
    registries {
        konfigyrCentral {
            clientCredentials {
                clientId = "acme-corp-client-id"
                clientSecret = "acme-corp-client-secret"
            }
        }
    }
}
```

If your CI issues short-lived identity tokens (e.g. an OIDC token) instead of a long-lived secret, use
`tokenExchange { }` instead. This is the only grant with no environment-variable default for its token type,
since there's no sensible one to assume:

```kotlin
konfigyr {
    registries {
        konfigyrCentral {
            tokenExchange {
                subjectTokenType = "urn:ietf:params:oauth:token-type:jwt"
                // clientId / subjectToken still resolve from KONFIGYR_CLIENT_ID / KONFIGYR_SUBJECT_TOKEN
            }
        }
    }
}
```

**A custom, self-hosted registry** works the same way if you're running your own Konfigyr instance. Give it
a name of your choosing, and set every value explicitly, since no environment-variable fallback applies:

```kotlin
konfigyr {
    registries {
        registry("staging") {
            url = uri("https://staging.konfigyr.internal")

            clientCredentials {
                clientId = "acme-corp-client-id"
                clientSecret = "acme-corp-client-secret"
            }
        }
    }
}
```

You can declare as many registries as you need. Every one gets published to independently. If both
`clientCredentials { }` and `tokenExchange { }` are configured on the same registry, `tokenExchange` wins.

A registry's `url` must use `https`, unless the host is a loopback address (`localhost`, `127.0.0.1`, `::1`),
which is exempt for local testing. If you're publishing to a service that's only ever reachable over an
otherwise secured channel, e.g. one exposed exclusively on a private network or VPN, set `insecure = true`
to allow a plain `http` URL. Only do this when you're sure the channel itself is trusted, since credentials
are sent in plaintext:

```kotlin
konfigyr {
    registries {
        registry("internal") {
            url      = uri("http://konfigyr.internal.acme.com")
            insecure = true

            clientCredentials {
                clientId = "acme-corp-client-id"
                clientSecret = "acme-corp-client-secret"
            }
        }
    }
}
```

</details>

<details>
<summary><strong>Releasing a service</strong></summary>

If you're deploying a Spring Boot application (as opposed to publishing a library), add a `service { }`
block, giving it the same `name` your service is already registered under in Konfigyr. That's what opts a
project into releasing. Every release then reports the project's complete dependency graph to Konfigyr, so
its catalog stays complete and current for that service, whether or not any given dependency is itself
verified:

```kotlin
konfigyr {
    registries {
        konfigyrCentral()
    }

    service {
        name = "order-service"
    }
}
```

`name` **must match the service's identifier (URL slug) in the Konfigyr app exactly**. It's how a release
is linked to the right service, not just a descriptive label. It will not match your Gradle module name in
most real projects, so don't rely on any default; set it explicitly to the identifier Konfigyr already has.

</details>

<details>
<summary><strong>Publishing a library</strong></summary>

If your project isn't a deployable service but a library other teams depend on, skip `service { }`
entirely. There's no release, no dependency graph to report. Configure your registries and credentials as
above, and once your `groupId` is verified in Konfigyr, the plugin's direct-publish task does the rest:
publishing your library's own configuration metadata, once per version, to the shared registry where every
consuming service can resolve it automatically, without reporting it themselves:

```kotlin
konfigyr {
    registries {
        konfigyrCentral()
    }
}
```

Verification itself happens in Konfigyr, not in this plugin. A namespace admin proves ownership of your
`groupId` (e.g. `com.acme.*`) before anything can be published under it. Until that's done, the publish task
fails rather than silently skipping, see the FAQ below.

</details>

<details>
<summary><strong>Tasks</strong></summary>

The plugin registers one publish/release task pair **per declared registry**, plus a shared `konfigyr`
umbrella task. For a registry named `staging`, the publish task is `publishArtifactMetadataToStaging`; for
the reserved `konfigyrCentral` registry, it's `publishArtifactMetadataToKonfigyrCentral`, and so on.

| Task | Scenario | Runs when |
|---|---|---|
| `konfigyr` | Both | Always. Depends on every registry's publish/release tasks below. |
| `generateArtifactMetadata` | Shared | Always. Scans this project's own built jar; feeds both scenarios. Cacheable. |
| `publishArtifactMetadataTo<Registry>` | Direct publish | Whenever `<Registry>` has a `url` and a grant configured. |
| `resolveServiceDependencies` | Service release | Only if `service { }` is configured. Cacheable. |
| `createServiceReleaseTo<Registry>` | Service release | Only if `service { }` **and** `<Registry>` are both configured. |

For most projects (deployable services), `createServiceReleaseTo<Registry>` is where the real value is. It's
what builds the service's dependency catalog. `publishArtifactMetadataTo<Registry>` only does something when
the project's own jar exposes configuration metadata; otherwise it's a no-op.

You can run any task individually, e.g. `./gradlew generateArtifactMetadata` to scan and write metadata
locally with no network calls. Generated files live under `build/konfigyr/` and are fully cacheable.
Re-running without classpath changes is instant.

</details>

<details>
<summary><strong>Multi-project builds</strong></summary>

A registry's connection is shared build-wide, not per-project. Apply the plugin selectively and configure
the shared registries once, typically on the root project or via `subprojects { }`:

```kotlin
// root build.gradle.kts
plugins {
    id("com.konfigyr.artifactory") version "1.2.0" apply false
}

subprojects {
    apply(plugin = "com.konfigyr.artifactory")

    konfigyr {
        registries {
            konfigyrCentral()
        }
    }
}
```

If subprojects end up declaring different connection settings for the same registry name, the build fails
rather than silently picking one. Configuring the root project's own `konfigyr { }` block directly (instead
of only via `subprojects { }`) is what lets you pin the connection in exactly one place.

`service { }` is *not* part of that shared block. It's configured per-project, only where it applies. In a
typical multi-module build, only the module that's actually deployed configures a `service { }`; everything
else is a library dependency and needs nothing beyond the shared registry:

```kotlin
// api/build.gradle.kts (the module that's actually deployed)
konfigyr {
    service {
        name = "order-service"
    }
}

// common/build.gradle.kts (a library module the service depends on, nothing to add here)
```

A build made up entirely of libraries, with no deployable service anywhere, is equally valid. In that
case, no subproject configures `service { }` at all.

To publish metadata for every subproject in one command, run `./gradlew konfigyr` from the root.

</details>

<details>
<summary><strong>FAQ / Troubleshooting</strong></summary>

**My build failed with an HTTP error from `publishArtifactMetadataTo...`. Why?**
That task runs whenever its registry is configured, independently of whether your `groupId` happens to be
verified yet. Verification is a Konfigyr-side, not a plugin-side, check. If your project's own artifact
exposes configuration metadata but its `groupId` isn't verified for that registry, the direct-publish call
is rejected and the build fails. If a project should only ever go through the service-release scenario,
disable the task explicitly:

```kotlin
tasks.named("publishArtifactMetadataToKonfigyrCentral") { enabled = false }
```

**Do I need to create the service in Konfigyr before I can release to it?**
Yes, you need to have an existing service to perform releases. Releases are linked to an existing service by
its `name`/identifier. The plugin does not create services on your behalf; it only releases against one
that's already registered in the Konfigyr app.

</details>

---

## Maven Plugin

> **Coming soon.** The Maven plugin is not yet implemented. Contributions are welcome, feel free to open a
> pull request or follow [the issue tracker](https://github.com/konfigyr/konfigyr-plugin/issues) for updates.

Once available, it will provide equivalent functionality for Maven projects via a dedicated goal, using the
same registry and scenario model described above.

---

## Getting help & contributing

- **Questions / discussion:** the [#konfigyr-plugin room on Gitter](https://gitter.im/konfigyr/konfigyr-plugin), see [SUPPORT.md](SUPPORT.md).
- **Bugs / feature requests:** [GitHub Issues](https://github.com/konfigyr/konfigyr-plugin/issues).
- **Contributing a change:** see [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup, project layout, and PR process.
- This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

## License

[Apache 2.0](LICENSE)
