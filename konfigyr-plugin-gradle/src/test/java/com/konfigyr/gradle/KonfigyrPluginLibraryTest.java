package com.konfigyr.gradle;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.konfigyr.TokenExchange;
import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.PublicationState;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.ResourceUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Konfigyr plugin publishes a pure library's own artifact directly while skipping the
 * service-release tasks (the {@code com.acme.library/library} fixture has no {@code namespace}
 * configured). These tests are ordered and share build state across methods (no {@code clean} on the
 * second run), so they must stay together and in sequence.
 * <p>
 * This fixture configures the {@code tokenExchange { }} so the OAuth stub must match it, proves the
 * token exchange DSL block works end-to-end through a real Gradle build, not just via a direct
 * Action call.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KonfigyrPluginLibraryTest extends AbstractKonfigyrPluginTest {

    private static final Artifact LIBRARY = Artifact.of("com.acme", "library", "1.0.0");

    KonfigyrPluginLibraryTest() {
        super(new TokenExchange("konfigyr-client-id", "konfigyr-subject-token"));
    }

    private GradleRunner runner;

    @BeforeEach
    void setup() throws IOException {
        final var environment = new HashMap<>(System.getenv());
        environment.remove("KONFIGYR_NAMESPACE");

        runner = GradleRunner.create()
                .withDebug(false)
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(ResourceUtils.loadResource("com.acme.library/library").getFile())
                .withEnvironment(environment);
    }

    @Test
    @Order(1)
    @DisplayName("should publish library artifact and skip service release tasks when no namespace is configured")
    void shouldPublishLibraryArtifact() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseExistsResponseFor(LIBRARY, false);

        final var publication = JsonNodeFactory.instance.objectNode()
                .put("groupId", LIBRARY.groupId())
                .put("artifactId", LIBRARY.artifactId())
                .put("version", LIBRARY.version())
                .put("state", ReleaseState.PENDING.name())
                .put("checksum", "checksum")
                .putPOJO("errors", Collections.emptyList())
                .put("publishedAt", Instant.now().toString());

        stubFactories.createPublicationResponseFor(LIBRARY, WireMock.jsonResponse(publication.toPrettyString(), 200));
        stubFactories.getPublicationResponseFor(LIBRARY,
                WireMock.jsonResponse(publication.put("state", PublicationState.PUBLISHED.name()).toPrettyString(), 200));

        BuildResult result = runner
                .withArguments("clean", "konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.SUCCESS))
                .extracting(BuildTask::getPath)
                .contains(":generateArtifactMetadata", ":publishArtifactMetadata", ":konfigyr");

        assertThat(result.tasks(TaskOutcome.SKIPPED))
                .as("Service Release tasks should be skipped, no namespace is configured")
                .extracting(BuildTask::getPath)
                .contains(":resolveServiceDependencies", ":createServiceRelease");

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/artifacts/com.acme/library/1.0.0")));
        wiremock.verify(0, postRequestedFor(urlPathTemplate("/namespaces/{namespace}/services/{service}/releases")));
    }

    @Test
    @Order(2)
    @DisplayName("should cache generated metadata and skip republishing the library artifact on the next execution")
    void shouldCacheLibraryArtifactOnNextExecution() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseExistsResponseFor(LIBRARY, true);

        BuildResult result = runner
                .withArguments("konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.UP_TO_DATE))
                .as("Generate artifact metadata should be cached, its inputs haven't changed")
                .extracting(BuildTask::getPath)
                .contains(":generateArtifactMetadata");

        assertThat(result.tasks(TaskOutcome.SKIPPED))
                .as("Service Release tasks should still be skipped, no namespace is configured")
                .extracting(BuildTask::getPath)
                .contains(":resolveServiceDependencies", ":createServiceRelease");

        // publishArtifactMetadata declares no outputs, so it always re-executes - but should not
        // re-create a publication once the artifact is already published in the Artifactory
        wiremock.verify(1, headRequestedFor(urlPathEqualTo("/artifacts/com.acme/library/1.0.0")));
        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/artifacts/com.acme/library/1.0.0")));
        wiremock.verify(0, getRequestedFor(urlPathEqualTo("/artifacts/com.acme/library/1.0.0")));
    }

}
