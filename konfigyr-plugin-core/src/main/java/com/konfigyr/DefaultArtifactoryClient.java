package com.konfigyr;

import com.google.common.net.HttpHeaders;
import com.konfigyr.artifactory.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Default {@link ArtifactoryClient} implementation.
 * <p>
 * Request sending is delegated to a {@link Transport} and access token acquisition to an
 * {@link OAuthClientCredentialsProvider}. Both are supplied rather than built by this class, so several
 * clients, one per {@link Registry}, can share a single connection pool and a single token/discovery
 * cache instead of each opening their own. Instances are meant to be created via
 * {@link ArtifactoryClientFactory}, not directly.
 * <p>
 * This client provides a blocking API for build plugin integration, designed for simplicity and
 * portability across environments.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
final class DefaultArtifactoryClient implements ArtifactoryClient {

    private final Logger logger = LoggerFactory.getLogger(DefaultArtifactoryClient.class);

    private final JsonMapper mapper;
    private final Transport transport;
    private final Registry registry;
    private final OAuthClientCredentialsProvider authenticator;

    /**
     * Creates a new {@link DefaultArtifactoryClient} instance sharing the given {@link Transport} and
     * {@link OAuthClientCredentialsProvider} with every other client built from the same pair - used
     * when multiple registries are configured for a single build, so they share one connection pool
     * and one token/discovery cache rather than each opening their own.
     *
     * @param transport the shared transport to send requests with, cannot be {@literal null}.
     * @param authenticator the shared OAuth2 access token provider, cannot be {@literal null}.
     * @param registry the registry this client connects to, cannot be {@literal null}.
     * @param mapper the JSON mapper to use, cannot be {@literal null}.
     */
    DefaultArtifactoryClient(
            Transport transport,
            OAuthClientCredentialsProvider authenticator,
            Registry registry,
            JsonMapper mapper
    ) {
        this.transport = transport;
        this.mapper = mapper;
        this.registry = registry;
        this.authenticator = authenticator;
    }

    @Override
    public Manifest getManifest(String service) {
        if (logger.isDebugEnabled()) {
            logger.debug("Retrieving Manifest for service {}", service);
        }

        final URI uri = buildUri("services", service, "manifest");

        final HttpRequest request = createHttpRequest("GET", uri, null);

        return execute(request, Manifest.class);
    }

    @Override
    public ServiceRelease release(String service, Collection<? extends ServiceReleaseCandidate> artifacts) {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating a new service release for service {} with artifacts: {}", service, artifacts);
        }

        final HttpRequest.BodyPublisher body;

        try {
            body = HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(artifacts));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to create service release payload", e);
        }

        final URI uri = buildUri("releases", service);

        final HttpRequest request = createHttpRequest("POST", uri, body);

        return execute(request, ServiceRelease.class);
    }

    @Override
    public void upload(String service, ServiceRelease release, ArtifactMetadata metadata) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to upload artifact metadata for [service={}, release={}]: {}",
                    service, release.id(), metadata);
        }

        final HttpRequest.BodyPublisher body;

        try {
            body = HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(metadata));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to create artifact metadata payload", e);
        }

        final URI uri = buildUri("releases", service, release.id(), "artifacts");

        final HttpRequest request = createHttpRequest("POST", uri, body);

        execute(request, Void.TYPE);

        logger.info("Successfully uploaded artifact metadata for [artifact={}, version={}, service={}]",
                metadata.artifactId(), metadata.version(), service);
    }

    @Override
    public ServiceRelease complete(String service, ServiceRelease release) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to complete service release [service={}, release={}]", service, release.id());
        }

        final URI uri = buildUri("releases", service, release.id(), "complete");

        final HttpRequest request = createHttpRequest("POST", uri, null);
        final ServiceRelease completed = execute(request, ServiceRelease.class);

        logger.info("Successfully completed service release [id={}, state={}] for service {}: {}",
                completed.id(), completed.state(), service, completed);

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
     * against the configured {@link Registry#host()}.
     *
     * @param segments the segments to join into the URI path, must not be {@literal null}.
     * @return the built URI, never {@literal null}.
     */
    private URI buildUri(String... segments) {
        return UriBuilder.of(registry.host())
                .paths(segments)
                .build();
    }

    private HttpRequest createHttpRequest(String method, URI uri, HttpRequest.@Nullable BodyPublisher publisher) {
        final String accessToken = authenticator.getAccessToken(registry);

        return HttpRequest.newBuilder()
                .method(method, publisher == null ? HttpRequest.BodyPublishers.noBody() : publisher)
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T execute(HttpRequest request, Class<T> type) {
        final HttpResponse<String> response = transport.send(request);

        if (response.statusCode() == 401) {
            throw new HttpResponseException("Invalid or an expired Konfigyr Access Token provided. " +
                    "Please check your access token and try again.", response);
        }

        if (response.statusCode() == 403) {
            throw new HttpResponseException("Your Konfigyr Access Token does not have sufficient permission to " +
                    "perform this operation. Please check your OAuth client and try again.", response);
        }

        if (response.statusCode() >= 500) {
            throw new HttpResponseException(
                    "Konfigyr REST API returned a 5xx HTTP Status code for [%s %s] with a following error response: %s"
                            .formatted(request.method(), request.uri(), response.body()), response);
        }

        if (response.statusCode() >= 400) {
            throw new HttpResponseException(
                    "Konfigyr REST API returned a 4xx HTTP Status code for [%s %s] with a following error response: %s"
                            .formatted(request.method(), request.uri(), response.body()), response);
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
