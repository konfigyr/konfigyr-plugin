package com.konfigyr.gradle;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.konfigyr.ArtifactoryClient;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.ClientCredentials;
import com.konfigyr.Credentials;
import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.ArtifactUploadStatus;
import com.konfigyr.artifactory.PropertyDescriptor;
import com.konfigyr.artifactory.PublicationState;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.AbstractWiremockTest;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.File;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Base class for Konfigyr plugin tests that exercise a real Gradle build via
 * {@link org.gradle.testkit.runner.GradleRunner}, providing the WireMock stubs and metadata-reading
 * helpers shared across the different fixture-project scenarios.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
abstract class AbstractKonfigyrPluginTest extends AbstractWiremockTest {

    static final String NAMESPACE = "konfigyr";
    static final String SERVICE = "test-service";
    static final Artifact ACME = Artifact.of("com.acme", "acme", "1.0.0");
    static final String RELEASE_ID = "rel1";

    final ArtifactoryConfiguration configuration;

    AbstractKonfigyrPluginTest() {
        this(new ClientCredentials("konfigyr-client-id", "konfigyr-client-secret"));
    }

    AbstractKonfigyrPluginTest(Credentials credentials) {
        this.configuration = configuration()
                .credentials(credentials)
                .build();
    }

    void stubPublishOwnArtifact(boolean alreadyPublished) {
        stubFactories.getReleaseExistsResponseFor(ACME, alreadyPublished);

        if (alreadyPublished) {
            return;
        }

        final var publication = JsonNodeFactory.instance.objectNode()
                .put("groupId", ACME.groupId())
                .put("artifactId", ACME.artifactId())
                .put("version", ACME.version())
                .put("state", ReleaseState.PENDING.name())
                .put("checksum", "checksum")
                .putPOJO("errors", Collections.emptyList())
                .put("publishedAt", Instant.now().toString());

        stubFactories.createPublicationResponseFor(ACME, WireMock.jsonResponse(publication.toPrettyString(), 200));
        stubFactories.getPublicationResponseFor(ACME,
                WireMock.jsonResponse(publication.put("state", PublicationState.PUBLISHED.name()).toPrettyString(), 200));
    }

    void stubServiceRelease() {
        final String release = JsonNodeFactory.instance.objectNode()
                .put("id", RELEASE_ID)
                .put("state", ReleaseState.PENDING.name())
                .putPOJO("artifacts", List.of(
                        JsonNodeFactory.instance.objectNode()
                                .put("groupId", ACME.groupId())
                                .put("artifactId", ACME.artifactId())
                                .put("version", ACME.version())
                                .put("status", ArtifactUploadStatus.UPLOAD_REQUIRED.name())
                ))
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        stubFactories.serviceReleaseResponseFor(NAMESPACE, SERVICE, ACME, WireMock.jsonResponse(release, 200));

        stubFactories.uploadArtifactResponseFor(NAMESPACE, SERVICE, RELEASE_ID, ACME, WireMock.aResponse().withStatus(201));

        final String completed = JsonNodeFactory.instance.objectNode()
                .put("id", RELEASE_ID)
                .put("state", ReleaseState.RELEASED.name())
                .putPOJO("artifacts", Collections.emptyList())
                .put("publishedAt", Instant.now().toString())
                .putPOJO("errors", Collections.emptyList())
                .toPrettyString();

        stubFactories.completeServiceReleaseResponseFor(NAMESPACE, SERVICE, RELEASE_ID, WireMock.jsonResponse(completed, 200));
    }

    static PropertyDescriptor findProperty(ArtifactMetadata metadata, String name) {
        return metadata.properties().stream()
                .filter(property -> name.equals(property.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No property named '" + name + "' found in: " + metadata));
    }

    static ArtifactMetadata readArtifactMetadata(File file) {
        final ArtifactoryService service = new ArtifactoryService((ArtifactoryClient) null) {
            @Override
            public Parameters getParameters() {
                throw new UnsupportedOperationException();
            }
        };

        return service.readArtifactMetadata(file);
    }

}
