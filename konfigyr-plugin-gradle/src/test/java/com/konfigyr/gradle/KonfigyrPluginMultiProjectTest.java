package com.konfigyr.gradle;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.PublicationState;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.PropertyDescriptorAssert;
import com.konfigyr.test.ResourceUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Konfigyr plugin behavior in a Gradle multi-project build (the
 * {@code com.acme.multiproject} fixture: {@code core}, {@code customers}, {@code inventory} and
 * {@code orders}). This build is self-contained ({@code clean} on every run), so it doesn't need to
 * share state or ordering with any other test class.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
class KonfigyrPluginMultiProjectTest extends AbstractKonfigyrPluginTest {

    @Test
    @DisplayName("should execute the configured Konfigyr upload task in Gradle multi project setup")
    void assertPluginExecutedInMultiproject() throws IOException {
        stubFactories.tokenExchangeSuccessFor(configuration);

        stubFactories.getReleaseExistsResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                false
        );

        final var publication = JsonNodeFactory.instance.objectNode()
                .put("groupId", "{{jsonPath request.body '$.groupId'}}")
                .put("artifactId", "{{jsonPath request.body '$.artifactId'}}")
                .put("version", "{{jsonPath request.body '$.version'}}")
                .put("state", ReleaseState.PENDING.name())
                .put("checksum", "{{jsonPath request.body '$.checksum'}}")
                .putPOJO("errors", Collections.emptyList())
                .put("publishedAt", Instant.now().toString());

        stubFactories.createPublicationResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                WireMock.jsonResponse(publication.toPrettyString(), 200)
        );

        stubFactories.getPublicationResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                WireMock.jsonResponse(publication.put("state", PublicationState.PUBLISHED.name()).toPrettyString(), 200)
        );

        final String release = JsonNodeFactory.instance.objectNode()
                .put("id", RELEASE_ID)
                .put("state", ReleaseState.PENDING.name())
                .putPOJO("artifacts", Collections.emptyList())
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        // one stub answers all four projects (core/customers/inventory/orders), so the body matcher
        // only pins the groupId shared by all of them plus a non-blank checksum, not a specific artifactId
        wiremock.stubFor(
                post(urlPathTemplate("/namespaces/{namespace}/services/{service}/releases"))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .withRequestBody(matchingJsonPath("$[?(@.groupId=='com.acme' && @.checksum)]"))
                        .willReturn(jsonResponse(release, 200))
        );

        final String completed = JsonNodeFactory.instance.objectNode()
                .put("id", RELEASE_ID)
                .put("state", ReleaseState.RELEASED.name())
                .putPOJO("artifacts", Collections.emptyList())
                .put("publishedAt", Instant.now().toString())
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        wiremock.stubFor(
                post(urlPathTemplate("/namespaces/{namespace}/services/{service}/releases/{release}/publish"))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(jsonResponse(completed, 200))
        );

        final File projectDir = ResourceUtils.loadResource("com.acme.multiproject").getFile();

        BuildResult result = GradleRunner.create()
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .withArguments("clean", "konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.FAILED))
                .as("No task should fail")
                .isEmpty();

        // generateArtifactMetadata is @CacheableTask; a project with no metadata to produce (like
        // inventory) has identical empty inputs/outputs across test runs in this session, so it may
        // be restored FROM_CACHE rather than re-executed as SUCCESS - assert task paths ran at all,
        // regardless of that specific non-failure outcome.
        assertThat(result.getTasks())
                .extracting(BuildTask::getPath)
                .contains(
                        ":core:generateArtifactMetadata",
                        ":core:publishArtifactMetadata",
                        ":core:resolveServiceDependencies",
                        ":core:createServiceRelease",
                        ":core:konfigyr",
                        ":customers:generateArtifactMetadata",
                        ":customers:publishArtifactMetadata",
                        ":customers:resolveServiceDependencies",
                        ":customers:createServiceRelease",
                        ":customers:konfigyr",
                        ":inventory:generateArtifactMetadata",
                        ":inventory:publishArtifactMetadata",
                        ":inventory:resolveServiceDependencies",
                        ":inventory:createServiceRelease",
                        ":inventory:konfigyr",
                        ":orders:generateArtifactMetadata",
                        ":orders:publishArtifactMetadata",
                        ":orders:resolveServiceDependencies",
                        ":orders:createServiceRelease",
                        ":orders:konfigyr"
                );

        // CoreProperties declares properties whose types (DataSize, Resource) live in :core's own
        // dependencies (spring-core), not in :core's own compiled classes - proves generateArtifactMetadata
        // resolves types using the project's full classpath, not just its own jar.
        final ArtifactMetadata metadata = readArtifactMetadata(new File(projectDir, "core/build/konfigyr/metadata.json"));

        PropertyDescriptorAssert.assertThat(findProperty(metadata, "acme.core.upload-size"))
                .typeName(DataSize.class);
        PropertyDescriptorAssert.assertThat(findProperty(metadata, "acme.core.ssl"))
                .typeName(Resource.class);
    }

}
