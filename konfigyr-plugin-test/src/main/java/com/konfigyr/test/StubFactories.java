package com.konfigyr.test;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.ClientCredentials;
import com.konfigyr.TokenExchange;
import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.ReleaseState;
import lombok.RequiredArgsConstructor;
import org.assertj.core.annotation.CanIgnoreReturnValue;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Factory class for creating stub mappings for testing purposes.
 */
@NullMarked
@CanIgnoreReturnValue
@RequiredArgsConstructor
public final class StubFactories {

    private final Stubbing stubbing;

    /**
     * Creates an OAuth2 Token Exchange mapping with a successful result that contains an OAuth Access Token
     * response that expires in 10 minutes.
     *
     * @param configuration the client configuration
     * @return OAuth2 Token Exchange stub mapping
     */
    public StubMapping tokenExchangeSuccessFor(ArtifactoryConfiguration configuration) {
        return tokenExchangeSuccessFor(configuration, 600);
    }

    /**
     * Creates an OAuth2 Token Exchange mapping with a successful result that contains an OAuth Access Token that would
     * expire in a specified number of seconds.
     *
     * @param configuration the client configuration
     * @param expiry token expiry in seconds
     * @return OAuth2 Token Exchange stub mapping
     */
    public StubMapping tokenExchangeSuccessFor(ArtifactoryConfiguration configuration, long expiry) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("token_type", "Bearer")
                .put("access_token", "oauth-access-token")
                .put("expires_in", expiry)
                .toPrettyString();

        return tokenExchangeResponseFor(configuration, jsonResponse(json, 200));
    }

    /**
     * Creates an OAuth2 Token Exchange mapping with an error result of {@code invalid_client} and 401 status code.
     *
     * @param configuration the client configuration
     * @return OAuth2 Token Exchange stub mapping
     */
    public StubMapping tokenExchangeErrorFor(ArtifactoryConfiguration configuration) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("error", "invalid_client")
                .put("error_description", "Unknown OAuth client.")
                .put("error_uri", "https://www.oauth.com/oauth2-servers/access-tokens/access-token-response/")
                .toPrettyString();

        return tokenExchangeResponseFor(configuration, jsonResponse(json, 401));
    }

    /**
     * Creates an OAuth2 Token Exchange mapping with a custom response.
     *
     * @param configuration the client configuration
     * @param response response builder
     * @return OAuth2 Token Exchange stub mapping
     */
    public StubMapping tokenExchangeResponseFor(ArtifactoryConfiguration configuration, ResponseDefinitionBuilder response) {
        final MappingBuilder mapping = switch (configuration.credentials()) {
            case ClientCredentials credentials -> post(urlEqualTo("/oauth/token"))
                    .withFormParam("grant_type", equalTo("client_credentials"))
                    .withFormParam("client_id", equalTo(credentials.clientId()))
                    .withFormParam("client_secret", equalTo(credentials.clientSecret()));
            case TokenExchange credentials -> post(urlEqualTo("/oauth/token"))
                    .withFormParam("grant_type", equalTo("urn:ietf:params:oauth:grant-type:token-exchange"))
                    .withFormParam("client_id", equalTo(credentials.clientId()))
                    .withFormParam("subject_token", equalTo(credentials.subjectToken()))
                    .withFormParam("subject_token_type", equalTo(credentials.subjectTokenType()));
        };

        return stubbing.stubFor(mapping.willReturn(
                response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates a Service Manifest download mapping with a response that is extracted from the supplied manifest
     * resource location.
     *
     * @param namespace the namespace owning the service
     * @param service the service whose manifest is downloaded
     * @param manifestLocation location of the manifest resource
     * @return artifact manifest stub mapping
     */
    public StubMapping manifestResponseFor(String namespace, String service, String manifestLocation) {
        final String manifest;

        try {
            manifest = ResourceUtils.readResource(manifestLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read manifest resource at: " + manifestLocation, ex);
        }

        return manifestResponseFor(namespace, service, jsonResponse(manifest, 200));
    }

    /**
     * Creates a Service Manifest download mapping with an error result of 404 HTTP status code.
     *
     * @param namespace the namespace owning the service
     * @param service the service whose manifest is downloaded
     * @return artifact manifest stub mapping
     */
    public StubMapping manifestNotFoundFor(String namespace, String service) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("type", "about:blank")
                .put("status", 404)
                .put("title", "Not Found")
                .put("detail", "Manifest not found.")
                .toPrettyString();

        return manifestResponseFor(namespace, service, jsonResponse(json, 404));
    }

    /**
     * Creates a Service Manifest download mapping with a custom response definition.
     *
     * @param namespace the namespace owning the service
     * @param service the service whose manifest is downloaded
     * @param response response definition
     * @return artifact manifest stub mapping
     */
    public StubMapping manifestResponseFor(String namespace, String service, ResponseDefinitionBuilder response) {
        final String path = "/namespaces/" + namespace + "/services/" + service + "/manifest";

        return stubbing.stubFor(
                get(urlPathEqualTo(path))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates an Artifact Release exist check mapping.
     *
     * @param artifact the artifact for which the release is retrieved
     * @param exists {@literal true} if the release exists, {@literal false} otherwise
     * @return get artifact release check stub mapping
     */
    public StubMapping getReleaseExistsResponseFor(Artifact artifact, boolean exists) {
        final String path = "/artifacts/" + artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version();

        return getReleaseExistsResponseFor(urlPathEqualTo(path), exists);
    }

    /**
     * Creates an Artifact Release exist check mapping.
     *
     * @param url the url pattern to be matched
     * @param exists {@literal true} if the release exists, {@literal false} otherwise
     * @return get artifact release check stub mapping
     */
    public StubMapping getReleaseExistsResponseFor(UrlPattern url, boolean exists) {
        return getReleaseExistsResponseFor(url, aResponse().withStatus(exists ? 200 : 404));
    }

    /**
     * Creates an Artifact Release exist check mapping.
     *
     * @param url the url pattern to be matched
     * @param response the response builder
     * @return get artifact release check stub mapping
     */
    public StubMapping getReleaseExistsResponseFor(UrlPattern url, ResponseDefinitionBuilder response) {
        return stubbing.stubFor(
                head(url)
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response)
        );
    }

    /**
     * Creates a Service release mapping with a custom response.
     *
     * @param namespace the namespace owning the service
     * @param service the service the release is opened for
     * @param responseLocation response location
     * @return service publish stub mapping
     */
    public StubMapping serviceReleaseResponseFor(String namespace, String service, String responseLocation) {
        final String response;

        try {
            response = ResourceUtils.readResource(responseLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read manifest publish response at: " + responseLocation, ex);
        }

        return serviceReleaseResponseFor(namespace, service, jsonResponse(response, 200));
    }

    /**
     * Creates a Service release mapping with a custom response definition.
     *
     * @param namespace the namespace owning the service
     * @param service the service the release is opened for
     * @param response response definition
     * @return service publish stub mapping
     */
    public StubMapping serviceReleaseResponseFor(String namespace, String service, ResponseDefinitionBuilder response) {
        final String path = "/namespaces/" + namespace + "/services/" + service + "/releases";

        return stubbing.stubFor(
                post(urlPathEqualTo(path))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates a Service release mapping with a custom response definition, asserting that the
     * submitted release candidates contain an entry for the given artifact with a non-blank checksum.
     *
     * @param namespace the namespace owning the service
     * @param service the service the release is opened for
     * @param expectedCandidate an artifact expected to be present in the submitted release candidates
     * @param response response definition
     * @return service publish stub mapping
     */
    public StubMapping serviceReleaseResponseFor(
            String namespace, String service, Artifact expectedCandidate, ResponseDefinitionBuilder response
    ) {
        final String path = "/namespaces/" + namespace + "/services/" + service + "/releases";

        return stubbing.stubFor(
                post(urlPathEqualTo(path))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .withRequestBody(matchingJsonPath(
                                "$[?(@.groupId=='" + expectedCandidate.groupId() + "' && " +
                                "@.artifactId=='" + expectedCandidate.artifactId() + "' && " +
                                "@.version=='" + expectedCandidate.version() + "' && " +
                                "@.checksum)]"
                        ))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates a Service Release artifact metadata upload mapping with a given response builder.
     *
     * @param namespace the namespace owning the service
     * @param service the service the release belongs to
     * @param release the service release identifier for which the artifact is uploaded
     * @param artifact the artifact for which metadata is uploaded
     * @param response the response builder
     * @return upload service release artifact stub mapping
     */
    public StubMapping uploadArtifactResponseFor(
            String namespace,
            String service,
            String release,
            Artifact artifact,
            ResponseDefinitionBuilder response
    ) {
        final String path = "/namespaces/" + namespace + "/services/" + service +
                "/releases/" + release + "/artifacts/" + artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version();

        return stubbing.stubFor(
                post(urlPathEqualTo(path))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response)
        );
    }

    /**
     * Creates a Service Release completion (publish) mapping with a given response builder.
     *
     * @param namespace the namespace owning the service
     * @param service the service the release belongs to
     * @param release the service release identifier to complete
     * @param response the response builder
     * @return complete service release stub mapping
     */
    public StubMapping completeServiceReleaseResponseFor(
            String namespace,
            String service,
            String release,
            ResponseDefinitionBuilder response
    ) {
        final String path = "/namespaces/" + namespace + "/services/" + service +
                "/releases/" + release + "/publish";

        return stubbing.stubFor(
                post(urlPathEqualTo(path))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates an Artifact Publication get mapping with a given release state response.
     *
     * @param artifact the artifact for which the publication is retrieved
     * @param state the publication state
     * @return retrieve artifact publication state stub mapping
     */
    public StubMapping getPublicationResponseFor(Artifact artifact, ReleaseState state) {
        final var json = JsonNodeFactory.instance.objectNode()
                .put("groupId", artifact.groupId())
                .put("artifactId", artifact.artifactId())
                .put("version", artifact.version())
                .put("name", artifact.name())
                .put("description", artifact.description())
                .putPOJO("website", artifact.website())
                .putPOJO("repository", artifact.repository())
                .put("state", state.name())
                .put("checksum", "checksum")
                .putPOJO("errors", Collections.emptyList())
                .put("publishedAt", Instant.now().toString())
                .toPrettyString();

        return getPublicationResponseFor(artifact, jsonResponse(json, 200));
    }

    /**
     * Creates an Artifact Publication get mapping with an error result of 404 HTTP status code.
     *
     * @param artifact the artifact for which the publication is retrieved
     * @return retrieve artifact publication state stub mapping
     */
    public StubMapping publicationNotFoundFor(Artifact artifact) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("type", "about:blank")
                .put("status", 404)
                .put("title", "Not Found")
                .put("detail", "Artifact not found.")
                .toPrettyString();

        return getPublicationResponseFor(artifact, jsonResponse(json, 404));
    }

    /**
     * Creates an Artifact Metadata get mapping with a given response builder.
     *
     * @param artifact the artifact for which publication is retrieved
     * @param response the response builder
     * @return retrieve artifact publication state stub mapping
     */
    public StubMapping getPublicationResponseFor(Artifact artifact, ResponseDefinitionBuilder response) {
        final String path = "/artifacts/" + artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version();

        return getPublicationResponseFor(
                urlPathEqualTo(path),
                response.withHeader("Content-Type", "application/json")
        );
    }

    /**
     * Creates an Artifact Metadata get mapping with a given URL pattern and response builder.
     *
     * @param url the url pattern to be matched
     * @param response the response builder
     * @return retrieve artifact publication state stub mapping
     */
    public StubMapping getPublicationResponseFor(UrlPattern url, ResponseDefinitionBuilder response) {
        return stubbing.stubFor(
                get(url)
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates an Artifact Metadata upload mapping with a given release state response.
     *
     * @param metadata artifact metadata
     * @param state the publication state
     * @return artifact publication stub mapping
     */
    public StubMapping createPublicationResponseFor(ArtifactMetadata metadata, ReleaseState state) {
        final var json = JsonNodeFactory.instance.objectNode()
                .put("groupId", metadata.groupId())
                .put("artifactId", metadata.artifactId())
                .put("version", metadata.version())
                .put("name", metadata.name())
                .put("description", metadata.description())
                .putPOJO("website", metadata.website())
                .putPOJO("repository", metadata.repository())
                .put("state", state.name())
                .put("checksum", metadata.checksum())
                .putPOJO("errors", Collections.emptyList())
                .put("publishedAt", Instant.now().toString())
                .toPrettyString();

        return createPublicationResponseFor(metadata, jsonResponse(json, 200));
    }

    /**
     * Creates a failing Artifact Metadata upload mapping with an internal server error response.
     *
     * @param artifact the artifact for which publication should fail
     * @return artifact publication stub mapping
     */
    public StubMapping createReleaseErrorResponseFor(Artifact artifact) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("type", "about:blank")
                .put("status", 500)
                .put("title", "Internal Server Error")
                .put("detail", "Could not upload artifact metadata.")
                .toPrettyString();

        return createPublicationResponseFor(artifact, jsonResponse(json, 500));
    }

    /**
     * Creates an Artifact Metadata upload mapping with a given response builder.
     *
     * @param artifact the artifact for which publication should fail
     * @param response the response builder
     * @return artifact publication stub mapping
     */
    public StubMapping createPublicationResponseFor(Artifact artifact, ResponseDefinitionBuilder response) {
        final String path = "/artifacts/" + artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version();

        return createPublicationResponseFor(
                urlPathEqualTo(path),
                response.withHeader("Content-Type", "application/json")
        );
    }

    /**
     * Creates an Artifact Metadata upload mapping with a given URL pattern and response builder.
     *
     * @param url the url pattern to be matched
     * @param response the response builder
     * @return artifact publication stub mapping
     */
    public StubMapping createPublicationResponseFor(UrlPattern url, ResponseDefinitionBuilder response) {
        return stubbing.stubFor(
                post(url)
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }
}
