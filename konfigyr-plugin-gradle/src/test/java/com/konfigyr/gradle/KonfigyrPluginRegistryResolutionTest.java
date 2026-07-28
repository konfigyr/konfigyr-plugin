package com.konfigyr.gradle;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.konfigyr.ClientCredentials;
import com.konfigyr.Registry;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.AbstractWiremockTest;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@code KonfigyrPlugin}'s cross-project registry resolution: for every registry name
 * declared anywhere in a build, a fully configured registry on the *root* project always wins, and
 * otherwise every project declaring that name must agree on its connection settings, or the build
 * fails. These are ad-hoc TestKit builds written directly into a {@code @TempDir}, rather than
 * checked-in fixtures, since they exercise multi-project configuration edge cases.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 * @see KonfigyrPlugin
 */
class KonfigyrPluginRegistryResolutionTest extends AbstractWiremockTest {

    @Test
    @DisplayName("fails the build when subprojects configure conflicting connections for the same registry name")
    void conflictingRegistriesAcrossSubprojectsFailTheBuild(@TempDir Path projectDir) throws IOException {
        write(projectDir, "settings.gradle", """
                rootProject.name = 'conflict'
                include 'a', 'b'
                """);

        write(projectDir, "a/build.gradle", subprojectBuildFile("https://one.example.com", "id-one", "secret-one"));
        write(projectDir, "b/build.gradle", subprojectBuildFile("https://two.example.com", "id-two", "secret-two"));

        final BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments("createServiceReleaseToShared", "--stacktrace")
                .buildAndFail();

        assertThat(result.getOutput())
                .contains("Multiple projects configure different connection settings for registry 'shared'");
    }

    @Test
    @DisplayName("the root project's own registry always wins over a subproject's conflicting connection")
    void rootRegistryTakesPrecedenceOverSubprojectConflict(@TempDir Path projectDir) throws IOException {
        final Registry rootRegistry = registry()
                .credentials(new ClientCredentials("root-client-id", "root-client-secret"))
                .build();

        stubFactories.tokenExchangeSuccessFor(rootRegistry);
        stubServiceRelease("root-wins-service", "rel-root-1");

        write(projectDir, "settings.gradle", """
                rootProject.name = 'rootwins'
                include 'a'
                """);

        write(projectDir, "build.gradle", """
                plugins {
                    id 'java'
                    id 'com.konfigyr.artifactory'
                }

                konfigyr {
                    registries {
                        registry("shared") {
                            url = uri("${wiremock}")
                            clientCredentials {
                                clientId = 'root-client-id'
                                clientSecret = 'root-client-secret'
                            }
                        }
                    }
                }
                """);

        write(projectDir, "a/build.gradle", """
                plugins {
                    id 'java'
                    id 'com.konfigyr.artifactory'
                }

                group = 'com.acme'
                version = '1.0.0'

                konfigyr {
                    registries {
                        registry("shared") {
                            url = uri("https://unreachable.invalid")
                            clientCredentials {
                                clientId = 'decoy-client-id'
                                clientSecret = 'decoy-client-secret'
                            }
                        }
                    }
                    service {
                        name = 'root-wins-service'
                    }
                }
                """);

        final BuildResult result = GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments("createServiceReleaseToShared", "--stacktrace", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.task(":a:createServiceReleaseToShared"))
                .isNotNull()
                .returns(TaskOutcome.SUCCESS, buildTask -> buildTask != null ? buildTask.getOutcome() : null);

        // proves the root's own (reachable) registry was used, not "a"'s conflicting, unreachable one
        wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/releases/root-wins-service")));
    }

    private static String subprojectBuildFile(String url, String clientId, String clientSecret) {
        return """
                plugins {
                    id 'java'
                    id 'com.konfigyr.artifactory'
                }

                group = 'com.acme'
                version = '1.0.0'

                konfigyr {
                    registries {
                        registry("shared") {
                            url = uri("%s")
                            clientCredentials {
                                clientId = '%s'
                                clientSecret = '%s'
                            }
                        }
                    }
                    service {
                      name = project.name
                    }
                }
                """.formatted(url, clientId, clientSecret);
    }

    private void stubServiceRelease(String service, String releaseId) {
        final String release = JsonNodeFactory.instance.objectNode()
                .put("id", releaseId)
                .put("state", ReleaseState.PENDING.name())
                .putPOJO("artifacts", Collections.emptyList())
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        stubFactories.serviceReleaseResponseFor(service, WireMock.jsonResponse(release, 200));

        final String completed = JsonNodeFactory.instance.objectNode()
                .put("id", releaseId)
                .put("state", ReleaseState.RELEASED.name())
                .putPOJO("artifacts", Collections.emptyList())
                .put("publishedAt", Instant.now().toString())
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        stubFactories.completeServiceReleaseResponseFor(service, releaseId, WireMock.jsonResponse(completed, 200));
    }

    private static void write(Path projectDir, String relativePath, String content) throws IOException {
        final Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

}
