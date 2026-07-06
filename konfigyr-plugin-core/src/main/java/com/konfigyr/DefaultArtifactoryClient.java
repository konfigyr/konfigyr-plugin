package com.konfigyr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.net.HttpHeaders;
import com.konfigyr.artifactory.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * Default {@link ArtifactoryClient} implementation using Java {@link HttpClient} and OAuth2 Client
 * Credentials authentication.
 * <p>
 * This client provides a blocking API for build plugin integration, designed for simplicity and
 * portability across environments.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
public final class DefaultArtifactoryClient implements ArtifactoryClient {

    private final Logger logger;
    private final HttpClient httpClient;
    private final ArtifactoryConfiguration configuration;
    private final JsonMapper mapper;
    private final OAuthClientCredentialsProvider authenticator;

    /**
     * Creates a new {@link DefaultArtifactoryClient} instance with the given {@link ArtifactoryConfiguration}.
     *
     * @param configuration the Artifactory configuration, cannot be {@literal null}.
     */
    public DefaultArtifactoryClient(ArtifactoryConfiguration configuration) {
        this(configuration, JsonMapper.builder()
                .addModule(new ArtifactoryJacksonModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(inclusion -> inclusion
                        .withContentInclusion(JsonInclude.Include.NON_EMPTY)
                        .withValueInclusion(JsonInclude.Include.NON_EMPTY)
                )
                .build());
    }

    /**
     * Creates a new {@link DefaultArtifactoryClient} instance with the given {@link ArtifactoryConfiguration} and
     * a custom {@link JsonMapper}.
     *
     * @param configuration the Artifactory configuration, cannot be {@literal null}.
     * @param mapper the JSON mapper to use, cannot be {@literal null}.
     */
    public DefaultArtifactoryClient(ArtifactoryConfiguration configuration, JsonMapper mapper) {
        this(LoggerFactory.getLogger(DefaultArtifactoryClient.class), configuration, mapper);
    }

    /**
     * Creates a new {@link DefaultArtifactoryClient} instance with the given {@link ArtifactoryConfiguration},
     * logger and a custom {@link JsonMapper}.
     *
     * @param logger the logger to use, cannot be {@literal null}.
     * @param configuration the Artifactory configuration, cannot be {@literal null}.
     * @param mapper the JSON mapper to use, cannot be {@literal null}.
     */
    public DefaultArtifactoryClient(Logger logger, ArtifactoryConfiguration configuration, JsonMapper mapper) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(configuration.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.mapper = mapper;
        this.configuration = configuration;
        this.authenticator = new OAuthClientCredentialsProvider(httpClient, configuration);
    }

    @Override
    public Manifest getManifest(String namespace, String service) {
        if (logger.isDebugEnabled()) {
            logger.debug("Retrieving Manifest for service {} in namespace {}", service, namespace);
        }

        final URI uri = buildUri("namespaces", namespace, "services", service, "manifest");

        final HttpRequest request = createHttpRequest("GET", uri, null);

        return execute(request, Manifest.class);
    }

    @Override
    public ServiceRelease release(String namespace, String service, Collection<? extends ServiceReleaseCandidate> artifacts) {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating a new service release for service {} in namespace {} with artifacts: {}",
                    service, namespace, artifacts);
        }

        final HttpRequest.BodyPublisher body;

        try {
            body = HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(artifacts));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to create service release payload", e);
        }

        final URI uri = buildUri("namespaces", namespace, "services", service, "releases");

        final HttpRequest request = createHttpRequest("POST", uri, body);

        return execute(request, ServiceRelease.class);
    }

    @Override
    public void upload(String namespace, String service, ServiceRelease release, ArtifactMetadata metadata) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to upload artifact metadata for [namespace={}, service={}, release={}]: {}",
                    namespace, service, release.id(), metadata);
        }

        final HttpRequest.BodyPublisher body;

        try {
            body = HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(metadata));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to create artifact metadata payload", e);
        }

        final URI uri = buildUri("namespaces", namespace, "services", service,
                "releases", release.id(), "artifacts", metadata.groupId(), metadata.artifactId(), metadata.version());

        final HttpRequest request = createHttpRequest("POST", uri, body);

        execute(request, Void.TYPE);

        logger.info("Successfully uploaded artifact metadata for [artifact={}, version={}, service={}, namespace={}]",
                metadata.artifactId(), metadata.version(), service, namespace);
    }

    @Override
    public ServiceRelease complete(String namespace, String service, ServiceRelease release) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to complete service release [namespace={}, service={}, release={}]",
                    namespace, service, release.id());
        }

        final URI uri = buildUri("namespaces", namespace, "services", service, "releases", release.id(), "publish");

        final HttpRequest request = createHttpRequest("POST", uri, null);
        final ServiceRelease completed = execute(request, ServiceRelease.class);

        logger.info("Successfully completed service release [id={}, state={}] for service {} in namespace {}: {}",
                completed.id(), completed.state(), service, namespace, completed);

        return completed;
    }

    @Override
    public boolean isPublished(Artifact artifact) {
        if (logger.isDebugEnabled()) {
            logger.debug("Checking if Artifact with coordinates '{}:{}:{}' is already released by Artifactory",
                    artifact.groupId(), artifact.artifactId(), artifact.version());
        }

        final HttpRequest request = createHttpRequest("HEAD", createArtifactUri(artifact), null);

        try {
            execute(request, Void.TYPE);
        } catch (HttpResponseException ex) {
            if (ex.getStatus() == 404) {
                return false;
            }
            throw ex;
        }

        return true;
    }

    @Override
    public Publication publish(ArtifactMetadata metadata) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to upload artifact metadata to Artifactory: {}", metadata);
        }

        final HttpRequest.BodyPublisher body;

        try {
            body = HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(metadata));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to create artifact metadata payload", e);
        }

        final HttpRequest request = createHttpRequest("POST", createArtifactUri(metadata), body);
        final Publication publication = execute(request, Publication.class);

        logger.info("Successfully created a publication for artifact with coordinates '{}:{}:{}': {}",
                metadata.groupId(), metadata.artifactId(), metadata.version(), publication);

        return publication;
    }

    @Override
    public Publication getPublication(Artifact artifact) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to retrieve release state for artifact: '{}:{}:{}'",
                    artifact.groupId(), artifact.artifactId(), artifact.version());
        }

        final HttpRequest request = createHttpRequest("GET", createArtifactUri(artifact), null);

        return execute(request, Publication.class);
    }

    private URI createArtifactUri(Artifact artifact) {
        return buildUri("artifacts", artifact.groupId(), artifact.artifactId(), artifact.version());
    }

    /**
     * Builds a {@link URI} by resolving an absolute path, joined from each percent-encoded segment,
     * against the configured {@link ArtifactoryConfiguration#host()}.
     */
    private URI buildUri(String... segments) {
        final StringBuilder path = new StringBuilder();

        for (String segment : segments) {
            path.append('/').append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }

        return configuration.host().resolve(path.toString());
    }

    private HttpRequest createHttpRequest(String method, URI uri, HttpRequest.@Nullable BodyPublisher publisher) {
        final String accessToken = authenticator.getAccessToken();

        return HttpRequest.newBuilder()
                .method(method, publisher == null ? HttpRequest.BodyPublishers.noBody() : publisher)
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name())
                .header(HttpHeaders.ACCEPT_LANGUAGE, Locale.ENGLISH.toLanguageTag())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.USER_AGENT, configuration.userAgent())
                .header(HttpHeaders.X_REQUEST_ID, UUID.randomUUID().toString())
                .timeout(configuration.readTimeout())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T execute(HttpRequest request, Class<T> type) {
        if (logger.isDebugEnabled()) {
            logger.debug("Executing HTTP request: {} {}", request.method(), request.uri());
        }

        final HttpResponse<String> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new UncheckedIOException("Error occurred while establishing connection for HTTP request: %s %s"
                    .formatted(request.method(), request.uri()), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request was interrupted: %s %s"
                    .formatted(request.method(), request.uri()), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unexpected error occurred while executing HTTP request: %s %s"
                    .formatted(request.method(), request.uri()), ex);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Server {} responded with status code {} and body: {}",
                    configuration.host(), response.statusCode(), response.body());
        }

        if (response.statusCode() == 401) {
            throw new HttpResponseException("Invalid Konfigyr Access Token provided. Please check your access token " +
                    "and try again.", request, response);
        }

        if (response.statusCode() == 403) {
            throw new HttpResponseException("Your Konfigyr Access Token does not have sufficient permission to " +
                    "perform this operation. Please check your OAuth client and try again.", request, response);
        }

        if (response.statusCode() >= 500) {
            throw new HttpResponseException(
                    "Konfigyr REST API returned a 5xx HTTP Status code for [%s %s] with a following error response: %s"
                            .formatted(request.method(), request.uri(), response.body()), request, response);
        }

        if (response.statusCode() >= 400) {
            throw new HttpResponseException(
                    "Konfigyr REST API returned a 4xx HTTP Status code for [%s %s] with a following error response: %s"
                            .formatted(request.method(), request.uri(), response.body()), request, response);
        }

        if (type == Void.TYPE) {
            return (T) Void.TYPE;
        }

        try {
            return mapper.readValue(response.body(), type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to convert HTTP response to: " + type.getTypeName(), e);
        }
    }

}
