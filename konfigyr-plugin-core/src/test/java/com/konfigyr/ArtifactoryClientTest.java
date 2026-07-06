package com.konfigyr;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.konfigyr.artifactory.*;
import com.konfigyr.test.AbstractWiremockTest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

class ArtifactoryClientTest extends AbstractWiremockTest {

    static final String NAMESPACE = "konfigyr";
    static final String SERVICE = "konfigyr-test-service";

    final ArtifactoryConfiguration configuration = configuration()
            .credentials(new ClientCredentials("client-id", "client-secret"))
            .build();

    ArtifactoryClient client;

    @BeforeEach
    void setup() {
        client = new DefaultArtifactoryClient(configuration);
    }

    @Test
    @DisplayName("should retrieve artifact manifest for service")
    void retrieveManifest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(NAMESPACE, SERVICE, "konfigyr-test-service-manifest.json");

        assertThatObject(client.getManifest(NAMESPACE, SERVICE))
                .isNotNull()
                .returns("6274e1984052", Manifest::id)
                .returns("konfigyr-test-service", Manifest::name)
                .extracting(Manifest::artifacts, InstanceOfAssertFactories.iterable(Artifact.class))
                .hasSize(5)
                .extracting(Artifact::groupId, Artifact::artifactId, Artifact::version)
                .containsExactlyInAnyOrder(
                        tuple("com.konfigyr", "konfigyr-artifactory", "1.0.0"),
                        tuple("com.konfigyr", "konfigyr-crypto-api", "1.0.0"),
                        tuple("com.konfigyr", "konfigyr-crypto-tink", "1.0.0"),
                        tuple("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0"),
                        tuple("org.springframework.boot", "spring-boot-autoconfigure", "3.5.7")
                );
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service when access token can not be obtained")
    void unauthenticatedRetrieveManifest() {
        stubFactories.manifestResponseFor(NAMESPACE, SERVICE, "konfigyr-test-service-manifest.json");

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.getManifest(NAMESPACE, SERVICE))
                .withMessageContaining("Could not obtain OAuth2 access token");
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service that does not exist")
    void retrieveUnknownManifest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestNotFoundFor(NAMESPACE, SERVICE);

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.getManifest(NAMESPACE, SERVICE))
                .withMessageContaining("Konfigyr REST API returned a 4xx HTTP Status code")
                .withMessageContaining("Manifest not found.")
                .satisfies(assertResponseError(404))
                .withNoCause();
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to invalid response")
    void retrieveInvalidManifest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(NAMESPACE, SERVICE, WireMock.aResponse()
                .withBody("\"invalid response\"")
                .withStatus(200)
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> client.getManifest(NAMESPACE, SERVICE))
                .withMessageContaining("Failed to convert HTTP response to: %s", Manifest.class.getTypeName())
                .withCauseInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to authentication error")
    void retrieveManifestAuthenticationError() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(NAMESPACE, SERVICE, WireMock.aResponse().withStatus(401));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.getManifest(NAMESPACE, SERVICE))
                .withMessageContaining("Invalid Konfigyr Access Token provided")
                .satisfies(assertResponseError(401));
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to authorization error")
    void retrieveManifestAuthorizationError() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(NAMESPACE, SERVICE, WireMock.aResponse().withStatus(403));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.getManifest(NAMESPACE, SERVICE))
                .withMessageContaining("Your Konfigyr Access Token does not have sufficient permission")
                .satisfies(assertResponseError(403));
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to server connection error")
    void retrieveManifestConnectionError() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(NAMESPACE, SERVICE, WireMock.aResponse()
                .withFault(Fault.CONNECTION_RESET_BY_PEER)
        );

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> client.getManifest(NAMESPACE, SERVICE))
                .withMessageContaining("Error occurred while establishing connection")
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("should publish new service release with artifacts to Artifactory")
    void releaseServiceManifest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.serviceReleaseResponseFor(NAMESPACE, SERVICE, "konfigyr-release-service-manifest.json");

        final var artifacts = List.of(
                ServiceReleaseCandidate.of("com.konfigyr", "konfigyr-artifactory", "1.0.0", "checksum"),
                ServiceReleaseCandidate.of("com.konfigyr", "konfigyr-crypto-api", "1.0.0", "checksum")
        );

        final var release = client.release(NAMESPACE, SERVICE, artifacts);

        assertThatObject(release)
                .isNotNull()
                .returns("6274e1984052", ServiceRelease::id)
                .returns(ReleaseState.PENDING, ServiceRelease::state);

        assertThat(release.artifacts())
                .hasSize(artifacts.size())
                .containsExactlyInAnyOrder(
                        ServiceReleaseEntry.of("com.konfigyr", "konfigyr-artifactory", "1.0.0", ArtifactUploadStatus.SKIP),
                        ServiceReleaseEntry.of("com.konfigyr", "konfigyr-crypto-api", "1.0.0", ArtifactUploadStatus.UPLOAD_REQUIRED)
                );

        assertThat(release.publishedAt())
                .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("should fail to publish new service release due to validation errors")
    void releaseServiceManifestBadRequest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.serviceReleaseResponseFor(NAMESPACE, SERVICE, WireMock.aResponse().withStatus(400));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.release(NAMESPACE, SERVICE, List.of()))
                .withMessageContaining("Konfigyr REST API returned a 4xx HTTP Status")
                .satisfies(assertResponseError(400));
    }

    @Test
    @DisplayName("should upload artifact metadata for a service release")
    void uploadServiceReleaseArtifact() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-api", "1.0.0");
        final var metadata = artifact.toMetadata(List.of(
                PropertyDescriptor.builder()
                        .name("konfigyr.message")
                        .typeName("java.lang.String")
                        .schema(StringSchema.builder()
                                .description("A message property, can be used anywhere.")
                                .build()
                        ).build()
        ));
        final var release = ServiceRelease.builder()
                .id("6274e1984052")
                .state(ReleaseState.PENDING)
                .build();

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.uploadArtifactResponseFor(NAMESPACE, SERVICE, release.id(), artifact, WireMock.aResponse().withStatus(201));

        assertThatNoException().isThrownBy(() -> client.upload(NAMESPACE, SERVICE, release, metadata));

        wiremock.verify(postRequestedFor(urlPathEqualTo(
                "/namespaces/konfigyr/services/konfigyr-test-service/releases/6274e1984052/artifacts/com.konfigyr/konfigyr-crypto-api/1.0.0"))
                .withRequestBody(equalToJson(
                        "{\"groupId\":\"com.konfigyr\",\"artifactId\":\"konfigyr-crypto-api\",\"version\":\"1.0.0\"," +
                                "\"properties\":[{\"name\":\"konfigyr.message\",\"typeName\":\"java.lang.String\"," +
                                "\"schema\":{\"description\":\"A message property, can be used anywhere.\",\"type\":\"string\"}}]}",
                        false, true
                ))
        );
    }

    @Test
    @DisplayName("should fail to upload artifact metadata for a service release due to internal server error")
    void uploadServiceReleaseArtifactFailure() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-api", "1.0.0");
        final var metadata = artifact.toMetadata(List.of(
                PropertyDescriptor.builder()
                        .name("konfigyr.message")
                        .typeName("java.lang.String")
                        .schema(StringSchema.builder()
                                .description("A message property, can be used anywhere.")
                                .build()
                        ).build()
        ));
        final var release = ServiceRelease.builder()
                .id("6274e1984052")
                .state(ReleaseState.PENDING)
                .build();

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.uploadArtifactResponseFor(NAMESPACE, SERVICE, release.id(), artifact, WireMock.aResponse().withStatus(500));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.upload(NAMESPACE, SERVICE, release, metadata))
                .withMessageContaining("Konfigyr REST API returned a 5xx HTTP Status code")
                .satisfies(assertResponseError(500));
    }

    @Test
    @DisplayName("should complete a service release")
    void completeServiceRelease() {
        final var release = ServiceRelease.builder()
                .id("6274e1984052")
                .state(ReleaseState.PENDING)
                .build();

        final var json = JsonNodeFactory.instance.objectNode()
                .put("id", release.id())
                .put("state", ReleaseState.RELEASED.name())
                .putPOJO("artifacts", List.of())
                .putPOJO("errors", List.of())
                .put("publishedAt", Instant.now().toString())
                .toPrettyString();

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.completeServiceReleaseResponseFor(NAMESPACE, SERVICE, release.id(), jsonResponse(json, 200));

        assertThatObject(client.complete(NAMESPACE, SERVICE, release))
                .isNotNull()
                .returns(release.id(), ServiceRelease::id)
                .returns(ReleaseState.RELEASED, ServiceRelease::state)
                .satisfies(it -> assertThat(it.publishedAt())
                        .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
                );
    }

    @Test
    @DisplayName("should fail to complete a service release when required uploads are missing")
    void completeServiceReleaseFailure() {
        final var release = ServiceRelease.builder()
                .id("6274e1984052")
                .state(ReleaseState.PENDING)
                .build();

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.completeServiceReleaseResponseFor(NAMESPACE, SERVICE, release.id(), WireMock.aResponse().withStatus(409));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.complete(NAMESPACE, SERVICE, release))
                .withMessageContaining("Konfigyr REST API returned a 4xx HTTP Status code")
                .satisfies(assertResponseError(409));
    }

    @Test
    @DisplayName("should check if Artifact is published in Artifactory when responding with 200 status code")
    void releaseExists() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseExistsResponseFor(artifact, true);

        assertThat(client.isPublished(artifact))
                .isTrue();
    }

    @Test
    @DisplayName("should check if Artifact is published in Artifactory when responding with 404 status code")
    void releaseDoesNotExists() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseExistsResponseFor(artifact, false);

        assertThat(client.isPublished(artifact))
                .isFalse();
    }

    @Test
    @DisplayName("should check if Artifact is published in Artifactory when responding with 5xx status code")
    void releaseExistsFailed() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseExistsResponseFor(
                WireMock.urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                WireMock.aResponse().withStatus(500)
        );

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.isPublished(artifact))
                .withMessageContaining("Konfigyr REST API returned a 5xx HTTP Status")
                .satisfies(assertResponseError(500));
    }

    @Test
    @DisplayName("should upload artifact metadata to Artifactory and create a new Publication")
    void publishArtifactMetadata() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");
        final var metadata = artifact.toMetadata(List.of(
                PropertyDescriptor.builder()
                        .name("konfigyr.message")
                        .typeName("java.lang.String")
                        .schema(StringSchema.builder()
                                .title("message")
                                .description("A message property, can be used anywhere.")
                                .example("Hello, world!")
                                .build()
                        ).build(),
                PropertyDescriptor.builder()
                        .name("konfigyr.enabled")
                        .typeName("java.lang.Boolean")
                        .schema(BooleanSchema.builder()
                                .description("Konfigyr enabled flag, defaults to true.")
                                .defaultValue(true)
                                .build()
                        ).build()
        ));

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.createPublicationResponseFor(metadata, ReleaseState.PENDING);

        assertThatObject(client.publish(metadata))
                .isNotNull()
                .returns(artifact.groupId(), Publication::groupId)
                .returns(artifact.artifactId(), Publication::artifactId)
                .returns(artifact.version(), Publication::version)
                .returns(artifact.name(), Publication::name)
                .returns(artifact.description(), Publication::description)
                .returns(artifact.website(), Publication::website)
                .returns(artifact.repository(), Publication::repository)
                .returns(PublicationState.PENDING, Publication::state)
                .returns(List.of(), Publication::errors)
                .returns(metadata.checksum(), Publication::checksum)
                .satisfies(it -> assertThat(it.publishedAt())
                        .isCloseTo(Instant.now(), within(500, ChronoUnit.MILLIS))
                );

        wiremock.verify(postRequestedFor(urlPathEqualTo("/artifacts/com.konfigyr/konfigyr-crypto-jdbc/1.0.0"))
                .withRequestBody(equalToJson(
                        "{\"groupId\":\"com.konfigyr\",\"artifactId\":\"konfigyr-crypto-jdbc\",\"version\":\"1.0.0\"," +
                                "\"properties\":[{\"name\":\"konfigyr.enabled\",\"typeName\":\"java.lang.Boolean\"," +
                                "\"schema\":{\"type\":\"boolean\",\"defaultValue\":true," +
                                "\"description\":\"Konfigyr enabled flag, defaults to true.\"}}," +
                                "{\"name\":\"konfigyr.message\",\"typeName\":\"java.lang.String\"," +
                                "\"schema\":{\"description\":\"A message property, can be used anywhere.\"," +
                                "\"examples\":[\"Hello, world!\"],\"title\":\"message\",\"type\":\"string\"}}]}",
                        false, true
                ))
        );
    }

    @Test
    @DisplayName("should fail to upload artifact metadata to Artifactory due to internal server error")
    void publishArtifactMetadataFailure() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");
        final var metadata = artifact.toMetadata(List.of(
                PropertyDescriptor.builder()
                        .name("konfigyr.message")
                        .typeName("java.lang.String")
                        .schema(StringSchema.builder()
                                .description("A message property, can be used anywhere.")
                                .build()
                        ).build()
        ));

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.createReleaseErrorResponseFor(metadata);

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.publish(metadata))
                .withMessageContaining("Konfigyr REST API returned a 5xx HTTP Status code")
                .withMessageContaining("Internal Server Error")
                .satisfies(assertResponseError(500))
                .withNoCause();
    }

    @Test
    @DisplayName("should retrieve publication state for artifact from Artifactory")
    void retrievePublication() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getPublicationResponseFor(artifact, ReleaseState.PENDING);

        assertThatObject(client.getPublication(artifact))
                .isNotNull()
                .returns(artifact.groupId(), Publication::groupId)
                .returns(artifact.artifactId(), Publication::artifactId)
                .returns(artifact.version(), Publication::version)
                .returns(artifact.name(), Publication::name)
                .returns(artifact.description(), Publication::description)
                .returns(artifact.website(), Publication::website)
                .returns(artifact.repository(), Publication::repository)
                .returns(PublicationState.PENDING, Publication::state)
                .returns(List.of(), Publication::errors)
                .returns("checksum", Publication::checksum)
                .satisfies(it -> assertThat(it.publishedAt())
                        .isCloseTo(Instant.now(), within(500, ChronoUnit.MILLIS))
                );
    }

    @Test
    @DisplayName("should fail to retrieve release state for an unknown artifact from Artifactory")
    void retrieveUnknownRelease() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.publicationNotFoundFor(artifact);

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.getPublication(artifact))
                .withMessageContaining("Konfigyr REST API returned a 4xx HTTP Status code")
                .withMessageContaining("Artifact not found.")
                .satisfies(assertResponseError(404))
                .withNoCause();
    }

    static Consumer<HttpResponseException> assertResponseError(int code) {
        return ex -> assertThat(ex)
                .returns(code, HttpResponseException::getStatus)
                .extracting(HttpResponseException::getResponse)
                .returns(ex.getRequest(), HttpResponse::request);
    }

}