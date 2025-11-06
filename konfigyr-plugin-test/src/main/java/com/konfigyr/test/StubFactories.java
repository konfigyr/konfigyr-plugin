package com.konfigyr.test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.ReleaseState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

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
    @NonNull
    public StubMapping tokenExchangeSuccessFor(@NonNull ArtifactoryConfiguration configuration) {
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
    @NonNull
    public StubMapping tokenExchangeSuccessFor(@NonNull ArtifactoryConfiguration configuration, long expiry) {
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
    @NonNull
    public StubMapping tokenExchangeErrorFor(@NonNull ArtifactoryConfiguration configuration) {
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
    @NonNull
    public StubMapping tokenExchangeResponseFor(@NonNull ArtifactoryConfiguration configuration,
                                                @NonNull ResponseDefinitionBuilder response) {
        return stubbing.stubFor(
                post(urlEqualTo("/oauth/token"))
                        .withFormParam("grant_type", equalTo("client_credentials"))
                        .withFormParam("client_id", equalTo(configuration.clientId()))
                        .withFormParam("client_secret", equalTo(configuration.clientSecret()))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates a Service Manifest download mapping with a response that is extracted from the supplied manifest
     * resource location.
     *
     * @param configuration the client configuration
     * @param manifestLocation location of the manifest resource
     * @return artifact manifest stub mapping
     */
    @NonNull
    public StubMapping manifestResponseFor(@NonNull ArtifactoryConfiguration configuration, @NonNull String manifestLocation) {
        final String manifest;

        try {
            manifest = ResourceUtils.readResource(manifestLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read manifest resource at: " + manifestLocation, ex);
        }

        return manifestResponseFor(configuration, jsonResponse(manifest, 200));
    }

    /**
     * Creates a Service Manifest download mapping with an error result of 404 HTTP status code.
     *
     * @param configuration the client configuration
     * @return artifact manifest stub mapping
     */
    @NonNull
    public StubMapping manifestNotFoundFor(@NonNull ArtifactoryConfiguration configuration) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("type", "about:blank")
                .put("status", 404)
                .put("title", "Not Found")
                .put("detail", "Manifest not found.")
                .toPrettyString();

        return manifestResponseFor(configuration, jsonResponse(json, 404));
    }

    /**
     * Creates a Service Manifest download mapping with a custom response definition.
     *
     * @param configuration the client configuration
     * @param response response definition
     * @return artifact manifest stub mapping
     */
    @NonNull
    public StubMapping manifestResponseFor(@NonNull ArtifactoryConfiguration configuration,
                                           @NonNull ResponseDefinitionBuilder response) {
        final String path = "/namespaces/" + configuration.namespace() + "/" + configuration.service() + "/manifest";

        return stubbing.stubFor(
                get(urlPathEqualTo(path))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }

    /**
     * Creates an Artifact Release get mapping with a given release state response.
     *
     * @param artifact the artifact for which the release is retrieved
     * @param state the release state
     * @return get artifact release state stub mapping
     */
    @NonNull
    public StubMapping getReleaseResponseFor(@NonNull Artifact artifact, @NonNull ReleaseState state) {
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
                .put("releaseDate", Instant.now().toString())
                .toPrettyString();

        return getReleaseResponseFor(artifact, jsonResponse(json, 200));
    }

    /**
     * Creates an Artifact Release get mapping with an error result of 404 HTTP status code.
     *
     * @param artifact the artifact for which the release is retrieved
     * @return artifact manifest stub mapping
     */
    @NonNull
    public StubMapping releaseNotFoundFor(@NonNull Artifact artifact) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("type", "about:blank")
                .put("status", 404)
                .put("title", "Not Found")
                .put("detail", "Artifact not found.")
                .toPrettyString();

        return getReleaseResponseFor(artifact, jsonResponse(json, 404));
    }

    /**
     * Creates an Artifact Metadata get mapping with a given response builder.
     *
     * @param artifact the artifact for which release is retrieved
     * @param response the response builder
     * @return get artifact release state stub mapping
     */
    @NonNull
    public StubMapping getReleaseResponseFor(@NonNull Artifact artifact, @NonNull ResponseDefinitionBuilder response) {
        final String path = "/artifacts/" + artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version();

        return getReleaseResponseFor(
                urlPathEqualTo(path),
                response.withHeader("Content-Type", "application/json")
        );
    }

    /**
     * Creates an Artifact Metadata get mapping with a given URL pattern and response builder.
     *
     * @param url the url pattern to be matched
     * @param response the response builder
     * @return upload artifact release state stub mapping
     */
    @NonNull
    public StubMapping getReleaseResponseFor(@NonNull UrlPattern url, @NonNull ResponseDefinitionBuilder response) {
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
     * @param state the release state
     * @return upload artifact release state stub mapping
     */
    @NonNull
    public StubMapping createReleaseResponseFor(@NonNull ArtifactMetadata metadata, @NonNull ReleaseState state) {
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
                .put("releaseDate", Instant.now().toString())
                .toPrettyString();

        return createReleaseResponseFor(metadata, jsonResponse(json, 200));
    }

    /**
     * Creates a failing Artifact Metadata upload mapping with an internal server error response.
     *
     * @param artifact the artifact for which release should fail
     * @return upload artifact release state stub mapping
     */
    @NonNull
    public StubMapping createReleaseErrorResponseFor(@NonNull Artifact artifact) {
        final String json = JsonNodeFactory.instance.objectNode()
                .put("type", "about:blank")
                .put("status", 500)
                .put("title", "Internal Server Error")
                .put("detail", "Could not upload artifact metadata.")
                .toPrettyString();

        return createReleaseResponseFor(artifact, jsonResponse(json, 500));
    }

    /**
     * Creates an Artifact Metadata upload mapping with a given response builder.
     *
     * @param artifact the artifact for which release should fail
     * @param response the response builder
     * @return upload artifact release state stub mapping
     */
    @NonNull
    public StubMapping createReleaseResponseFor(@NonNull Artifact artifact, @NonNull ResponseDefinitionBuilder response) {
        final String path = "/artifacts/" + artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version();

        return createReleaseResponseFor(
                urlPathEqualTo(path),
                response.withHeader("Content-Type", "application/json")
        );
    }

    /**
     * Creates an Artifact Metadata upload mapping with a given URL pattern and response builder.
     *
     * @param url the url pattern to be matched
     * @param response the response builder
     * @return upload artifact release state stub mapping
     */
    @NonNull
    public StubMapping createReleaseResponseFor(@NonNull UrlPattern url, @NonNull ResponseDefinitionBuilder response) {
        return stubbing.stubFor(
                post(url)
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(response.withHeader("Content-Type", "application/json"))
        );
    }
}
