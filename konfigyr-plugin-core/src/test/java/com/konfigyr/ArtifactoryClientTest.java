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

    final ArtifactoryConfiguration configuration = configuration()
            .clientId("client-id")
            .clientSecret("client-secret")
            .service("konfigyr-test-service")
            .namespace("konfigyr")
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
        stubFactories.manifestResponseFor(configuration, "konfigyr-test-service-manifest.json");

        assertThatObject(client.getManifest())
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
        stubFactories.manifestResponseFor(configuration, "konfigyr-test-service-manifest.json");

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(client::getManifest)
                .withMessageContaining("Could not obtain OAuth2 access token");
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service that does not exist")
    void retrieveUnknownManifest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestNotFoundFor(configuration);

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(client::getManifest)
                .withMessageContaining("Konfigyr REST API returned a 4xx HTTP Status code")
                .withMessageContaining("Manifest not found.")
                .satisfies(assertResponseError(404))
                .withNoCause();
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to invalid response")
    void retrieveInvalidManifest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(configuration, WireMock.aResponse()
                .withBody("\"invalid response\"")
                .withStatus(200)
        );

        assertThatIllegalStateException()
                .isThrownBy(client::getManifest)
                .withMessageContaining("Failed to convert HTTP response to: %s", Manifest.class.getTypeName())
                .withCauseInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to authentication error")
    void retrieveManifestAuthenticationError() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(configuration, WireMock.aResponse().withStatus(401));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(client::getManifest)
                .withMessageContaining("Invalid Konfigyr Access Token provided")
                .satisfies(assertResponseError(401));
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to authorization error")
    void retrieveManifestAuthorizationError() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(configuration, WireMock.aResponse().withStatus(403));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(client::getManifest)
                .withMessageContaining("Your Konfigyr Access Token does not have sufficient permission")
                .satisfies(assertResponseError(403));
    }

    @Test
    @DisplayName("should fail to retrieve artifact manifest for service due to server connection error")
    void retrieveManifestConnectionError() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(configuration, WireMock.aResponse()
                .withFault(Fault.CONNECTION_RESET_BY_PEER)
        );

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(client::getManifest)
                .withMessageContaining("Error occurred while establishing connection")
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("should publish new service release with artifacts to Artifactory")
    void publishServiceManifest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.publishResponseFor(configuration, "konfigyr-publish-service-manifest.json");

        final var artifacts = List.of(
                Artifact.of("com.konfigyr", "konfigyr-artifactory", "1.0.0"),
                Artifact.of("com.konfigyr", "konfigyr-crypto-api", "1.0.0")
        );

        assertThatObject(client.publish(artifacts))
                .isNotNull()
                .returns("6274e1984052", Manifest::id)
                .returns("konfigyr-published-service", Manifest::name)
                .returns(artifacts, Manifest::artifacts);
    }

    @Test
    @DisplayName("should fail to publish new service release due to validation errors")
    void publishServiceManifestBadRequest() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.publishResponseFor(configuration, WireMock.aResponse().withStatus(400));

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.publish(List.of()))
                .withMessageContaining("Konfigyr REST API returned a 4xx HTTP Status")
                .satisfies(assertResponseError(400));
    }

    @Test
    @DisplayName("should check if Artifact is published in Artifactory when responding with 200 status code")
    void releaseExists() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseExistsResponseFor(artifact, true);

        assertThat(client.isReleased(artifact))
                .isTrue();
    }

    @Test
    @DisplayName("should check if Artifact is published in Artifactory when responding with 404 status code")
    void releaseDoesNotExists() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseExistsResponseFor(artifact, false);

        assertThat(client.isReleased(artifact))
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
                .isThrownBy(() -> client.isReleased(artifact))
                .withMessageContaining("Konfigyr REST API returned a 5xx HTTP Status")
                .satisfies(assertResponseError(500));
    }

    @Test
    @DisplayName("should upload artifact metadata to Artifactory")
    void uploadArtifactMetadata() {
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
        stubFactories.createReleaseResponseFor(metadata, ReleaseState.PENDING);

        assertThatObject(client.upload(metadata))
                .isNotNull()
                .returns(artifact.groupId(), Release::groupId)
                .returns(artifact.artifactId(), Release::artifactId)
                .returns(artifact.version(), Release::version)
                .returns(artifact.name(), Release::name)
                .returns(artifact.description(), Release::description)
                .returns(artifact.website(), Release::website)
                .returns(artifact.repository(), Release::repository)
                .returns(ReleaseState.PENDING, Release::state)
                .returns(List.of(), Release::errors)
                .returns(metadata.checksum(), Release::checksum)
                .satisfies(it -> assertThat(it.releasedAt())
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
                                "\"examples\":[\"Hello, world!\"],\"title\":\"message\",\"type\":\"string\"}}]}"
                ))
        );
    }

    @Test
    @DisplayName("should fail to upload artifact metadata to Artifactory due to internal server error")
    void uploadArtifactMetadataFailure() {
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
                .isThrownBy(() -> client.upload(metadata))
                .withMessageContaining("Konfigyr REST API returned a 5xx HTTP Status code")
                .withMessageContaining("Internal Server Error")
                .satisfies(assertResponseError(500))
                .withNoCause();
    }

    @Test
    @DisplayName("should retrieve release state for artifact from Artifactory")
    void retrieveRelease() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.getReleaseResponseFor(artifact, ReleaseState.PENDING);

        assertThatObject(client.getRelease(artifact))
                .isNotNull()
                .returns(artifact.groupId(), Release::groupId)
                .returns(artifact.artifactId(), Release::artifactId)
                .returns(artifact.version(), Release::version)
                .returns(artifact.name(), Release::name)
                .returns(artifact.description(), Release::description)
                .returns(artifact.website(), Release::website)
                .returns(artifact.repository(), Release::repository)
                .returns(ReleaseState.PENDING, Release::state)
                .returns(List.of(), Release::errors)
                .returns("checksum", Release::checksum)
                .satisfies(it -> assertThat(it.releasedAt())
                        .isCloseTo(Instant.now(), within(500, ChronoUnit.MILLIS))
                );
    }

    @Test
    @DisplayName("should fail to retrieve release state for an unknown artifact from Artifactory")
    void retrieveUnknownRelease() {
        final var artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-jdbc", "1.0.0");

        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.releaseNotFoundFor(artifact);

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(() -> client.getRelease(artifact))
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