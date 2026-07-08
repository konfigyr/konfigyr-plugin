package com.konfigyr.gradle;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.konfigyr.test.ResourceUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Konfigyr plugin behavior for a single, standalone project (the {@code com.acme/acme}
 * fixture). These tests are ordered and share build state across methods (no {@code clean} between
 * them beyond the first), so they must stay together and in sequence.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KonfigyrPluginSingleProjectTest extends AbstractKonfigyrPluginTest {

    private GradleRunner runner;

    @BeforeEach
    void setup() throws IOException {
        runner = GradleRunner.create()
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(ResourceUtils.loadResource("com.acme/acme").getFile());
    }

    @Test
    @Order(1)
    @DisplayName("should execute the configured Konfigyr upload task")
    void assertPluginExecuted() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubPublishOwnArtifact(false);
        stubServiceRelease();

        BuildResult result = runner
                .withArguments("clean", "konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl(), "-Pspring=3.5.7")
                .build();

        assertThat(result.tasks(TaskOutcome.SUCCESS))
                .as("Konfigyr tasks should be successfully executed")
                .extracting(BuildTask::getPath)
                .contains(
                        ":generateArtifactMetadata",
                        ":publishArtifactMetadata",
                        ":resolveServiceDependencies",
                        ":createServiceRelease",
                        ":konfigyr"
                );

        wiremock.verify(WireMock.postRequestedFor(urlPathEqualTo("/oauth/token")));
        wiremock.verify(1, headRequestedFor(urlPathEqualTo("/artifacts/com.acme/acme/1.0.0")));
        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/artifacts/com.acme/acme/1.0.0")));
        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/artifacts/com.acme/acme/1.0.0")));
        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/namespaces/konfigyr/services/test-service/releases")));
        wiremock.verify(1, postRequestedFor(urlPathEqualTo(
                "/namespaces/konfigyr/services/test-service/releases/" + RELEASE_ID + "/artifacts")));
        wiremock.verify(1, postRequestedFor(urlPathEqualTo(
                "/namespaces/konfigyr/services/test-service/releases/" + RELEASE_ID + "/complete")));
    }

    @Test
    @Order(2)
    @DisplayName("should not execute generate metadata task when classpath is not changed")
    void assertGenerateMetadataTaskIsCached() {
        BuildResult result = runner
                .withArguments("generateArtifactMetadata", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.UP_TO_DATE))
                .as("Generate artifact metadata tasks should be cached")
                .extracting(BuildTask::getPath)
                .contains(":generateArtifactMetadata");
    }

    @Test
    @Order(3)
    @DisplayName("should not publish artifact metadata directly when already published in Artifactory")
    void ignoreAlreadyPublishedArtifact() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubPublishOwnArtifact(true);
        stubServiceRelease();

        BuildResult result = runner
                .withArguments("konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.SUCCESS))
                .extracting(BuildTask::getPath)
                .contains(":publishArtifactMetadata", ":createServiceRelease", ":konfigyr");

        wiremock.verify(WireMock.postRequestedFor(urlPathEqualTo("/oauth/token")));
        wiremock.verify(1, headRequestedFor(urlPathEqualTo("/artifacts/com.acme/acme/1.0.0")));
        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/artifacts/com.acme/acme/1.0.0")));
        wiremock.verify(0, getRequestedFor(urlPathEqualTo("/artifacts/com.acme/acme/1.0.0")));
    }

}
