# Contributing to Konfigyr Plugins

Thank you for taking the time to contribute. This document covers how to set up a development environment,
run the tests, and submit changes.

## Code of conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating you
agree to abide by its terms.

## Reporting bugs and requesting features

Please use [GitHub Issues](https://github.com/konfigyr/konfigyr-plugin/issues) for bug reports and feature requests.
Before opening a new issue, search existing issues to avoid duplicates.

For general questions and discussion, use the [#konfigyr-plugin room on Gitter](https://gitter.im/konfigyr/konfigyr-plugin).

## Development setup

### Prerequisites

- Java 21 or later
- Gradle 9.5 or later (the wrapper in the repo handles this automatically)

### Clone and build

```shell
git clone https://github.com/konfigyr/konfigyr-plugin.git
cd konfigyr-plugin
./gradlew build
```

### Project structure

| Module                   | Purpose                                                                                                    |
|--------------------------|------------------------------------------------------------------------------------------------------------|
| `konfigyr-plugin-core`   | Shared library: metadata parsing, Konfigyr API client, artifact model. Bundled into the Gradle plugin jar. |
| `konfigyr-plugin-gradle` | The Gradle plugin. Published to the Gradle Plugin Portal.                                                  |
| `konfigyr-plugin-maven`  | The Maven plugin. Not yet implemented.                                                                     |
| `konfigyr-plugin-test`   | Shared test utilities (WireMock, Mockito helpers).                                                         |
| `konfigyr-test-application` | Sample Spring Boot application used in integration tests.                                                  |

## Building

```shell
# Full build including tests
./gradlew build

# Compile only
./gradlew assemble

# Run checks (tests + coverage)
./gradlew check
```

## Testing

```shell
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :konfigyr-plugin-gradle:test

# Run a specific test class
./gradlew :konfigyr-plugin-gradle:test --tests "com.konfigyr.gradle.KonfigyrPluginTest"
```

Tests use JUnit and WireMock to stub the Konfigyr API. Integration tests run against the `konfigyr-test-application`
Spring Boot project, no running Konfigyr instance is required.

## Code style

- Follow standard Java conventions.
- Keep methods small and focused.
- Write tests for every non-trivial change. Bug fixes should include a test that would have caught the regression.
- Avoid adding dependencies to `konfigyr-plugin-core` without discussion, they will be bundled into the published plugin jar.

## Submitting a pull request

1. Fork the repository and create a branch from `main`.
2. Make your changes, including tests.
3. Run `./gradlew check` and ensure everything passes.
4. Push your branch and open a pull request against `main`.
5. Fill in the pull request template — in particular, link the relevant issue.

Pull requests are reviewed by the maintainers. Small, focused changes are easier to review and merge.
If you are planning a large change, open an issue first to discuss the approach.

## Commit messages

Use short, imperative-mood subject lines (e.g. `Fix NPE when manifest is empty`, not
`Fixed a null pointer exception that occurred when the manifest was empty`). Reference the issue number where relevant (`Closes #42`).
