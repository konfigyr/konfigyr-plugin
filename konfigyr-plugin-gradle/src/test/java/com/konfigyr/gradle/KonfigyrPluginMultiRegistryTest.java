package com.konfigyr.gradle;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.konfigyr.Registry;
import com.konfigyr.TokenExchange;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.ResourceUtils;
import com.konfigyr.test.StubFactories;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Konfigyr plugin registers and executes an independent publish/release task pair for
 * *every* registry a single project declares (the {@code com.acme.multiregistry} fixture: the
 * reserved {@code konfigyrCentral} registry using a {@code clientCredentials { } } }} grant, plus a
 * custom {@code staging} registry using {@code tokenExchange { } } }}), each backed by its own
 * WireMock server, proving the two dispatch through entirely independent {@code ArtifactoryClient}s
 * rather than sharing state.
 * <p>
 * This fixture has no Spring Boot configuration metadata to publish, so
 * {@code publishArtifactMetadataTo*} succeeds as a no-op (see {@link PublishArtifactMetadataTask});
 * only the service-release flow, which always realizes the {@link ArtifactoryService} regardless of
 * whether there's any metadata, performs a real network call per registry.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 */
class KonfigyrPluginMultiRegistryTest extends AbstractKonfigyrPluginTest {

    private static final String SERVICE = "multi-registry-service";
    private static final String CENTRAL_RELEASE_ID = "rel-central-1";
    private static final String STAGING_RELEASE_ID = "rel-staging-1";

    @RegisterExtension
    static WireMockExtension staging = WireMockExtension.newInstance()
            .resetOnEachTest(true)
            .options(WireMockConfiguration.options()
                    .dynamicPort()
                    .globalTemplating(true)
                    .templatingEnabled(true)
                    .notifier(new Slf4jNotifier(true))
            )
            .build();

    private final StubFactories stagingStubFactories = new StubFactories(staging);

    private final Registry stagingRegistry = Registry.builder()
            .host(staging.baseUrl())
            .credentials(new TokenExchange("staging-client-id", "staging-subject-token",
                    "urn:ietf:params:oauth:token-type:jwt"))
            .build();

    @BeforeEach
    void stubStagingAuthorizationServerMetadata() {
        stagingStubFactories.authorizationServerMetadataFor(URI.create(staging.baseUrl()));
    }

    private void stubServiceRelease(StubFactories factories, String releaseId) {
        final String release = JsonNodeFactory.instance.objectNode()
                .put("id", releaseId)
                .put("state", ReleaseState.PENDING.name())
                .putPOJO("artifacts", Collections.emptyList())
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        factories.serviceReleaseResponseFor(SERVICE, WireMock.jsonResponse(release, 200));

        final String completed = JsonNodeFactory.instance.objectNode()
                .put("id", releaseId)
                .put("state", ReleaseState.RELEASED.name())
                .putPOJO("artifacts", Collections.emptyList())
                .put("publishedAt", Instant.now().toString())
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        factories.completeServiceReleaseResponseFor(SERVICE, releaseId, WireMock.jsonResponse(completed, 200));
    }

    @Test
    @DisplayName("should execute independent publish/release tasks for every declared registry")
    void assertPluginExecutedForEveryRegistry() throws IOException {
        stubFactories.tokenExchangeSuccessFor(registry);
        stagingStubFactories.tokenExchangeSuccessFor(stagingRegistry);

        stubServiceRelease(stubFactories, CENTRAL_RELEASE_ID);
        stubServiceRelease(stagingStubFactories, STAGING_RELEASE_ID);

        final BuildResult result = GradleRunner.create()
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(ResourceUtils.loadResource("com.acme.multiregistry/multiregistry").getFile())
                .withArguments(
                        "clean", "konfigyr", "--stacktrace", "--info",
                        "-Pwiremock=" + wiremock.baseUrl(),
                        "-PstagingUrl=" + staging.baseUrl()
                )
                .build();

        // generateArtifactMetadata is @CacheableTask; with no metadata to produce, its outcome may be
        // FROM_CACHE rather than SUCCESS (see KonfigyrPluginMultiProjectTest's "inventory" caveat)
        assertThat(result.getTasks())
                .extracting(BuildTask::getPath)
                .contains(":generateArtifactMetadata");

        assertThat(result.tasks(TaskOutcome.SUCCESS))
                .as("Every task, for both registries, should be successfully executed")
                .extracting(BuildTask::getPath)
                .contains(
                        ":publishArtifactMetadataToKonfigyrCentral",
                        ":publishArtifactMetadataToStaging",
                        ":resolveServiceDependencies",
                        ":createServiceReleaseToKonfigyrCentral",
                        ":createServiceReleaseToStaging",
                        ":konfigyr"
                );

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
        staging.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/releases/" + SERVICE)));
        staging.verify(1, postRequestedFor(urlPathEqualTo("/releases/" + SERVICE)));

        wiremock.verify(1, postRequestedFor(urlPathEqualTo(
                "/releases/" + SERVICE + "/" + CENTRAL_RELEASE_ID + "/complete")));
        staging.verify(1, postRequestedFor(urlPathEqualTo(
                "/releases/" + SERVICE + "/" + STAGING_RELEASE_ID + "/complete")));

        // proves each registry's release never crossed over to the other's server
        wiremock.verify(0, postRequestedFor(urlPathEqualTo(
                "/releases/" + SERVICE + "/" + STAGING_RELEASE_ID + "/complete")));
        staging.verify(0, postRequestedFor(urlPathEqualTo(
                "/releases/" + SERVICE + "/" + CENTRAL_RELEASE_ID + "/complete")));
    }

}
