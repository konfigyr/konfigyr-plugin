package com.konfigyr;

import com.konfigyr.artifactory.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Default {@link ArtifactoryClient} implementation using Java {@link HttpClient} and OAuth2 Client
 * Credentials authentication.
 * <p>
 * This client provides a blocking API for Gradle plugin integration, designed for simplicity and
 * portability across environments.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public final class DefaultArtifactoryClient implements ArtifactoryClient {

    private final Logger logger;
    private final HttpClient httpClient;
    private final ArtifactoryConfiguration configuration;
    private final JsonMapper mapper;
    private final OAuthClientCredentialsProvider authenticator;

    public DefaultArtifactoryClient(ArtifactoryConfiguration configuration) {
        this(LoggerFactory.getLogger(DefaultArtifactoryClient.class), configuration);
    }

    public DefaultArtifactoryClient(@NonNull Logger logger, @NonNull ArtifactoryConfiguration configuration) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(configuration.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.mapper = JsonMapper.builder()
                .addModule(new KonfigyrJacksonModule())
                .build();

        this.configuration = configuration;
        this.authenticator = new OAuthClientCredentialsProvider(httpClient, configuration);
    }

    @NonNull
    @Override
    public Manifest getManifest() {
        if (logger.isDebugEnabled()) {
            logger.debug("Retrieving Manifest for service {} in namespace {}", configuration.service(), configuration.namespace());
        }

        final String path = "/namespaces/" + configuration.namespace() + "/" + configuration.service() + "/manifest";
        final HttpRequest request = createHttpRequest("GET", path, null);

        return execute(request, Manifest.class);
    }

    @NonNull
    @Override
    public Release upload(@NonNull ArtifactMetadata metadata) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to upload artifact metadata for service {} in namespace {}: {}",
                    configuration.service(), configuration.namespace(), metadata);
        }

        final HttpRequest.BodyPublisher body;

        try {
            body = HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(metadata));

            System.out.println(
                    mapper.valueToTree(metadata).toPrettyString()
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to create artifact metadata payload", e);
        }

        final HttpRequest request = createHttpRequest("POST", generateArtifactPath(metadata), body);
        final Release release = execute(request, Release.class);

        logger.info("Successfully created a release for [artifact={}, version={}, service={}, namespace={}]: {}",
                metadata.artifactId(), metadata.version(), configuration.service(), configuration.namespace(), release);

        return release;
    }

    @NonNull
    @Override
    public Release getRelease(@NonNull Artifact artifact) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to retrieve release state for artifact {} used by service {} in namespace {}",
                    artifact.name(), configuration.service(), configuration.namespace());
        }

        final HttpRequest request = createHttpRequest("GET", generateArtifactPath(artifact), null);

        return execute(request, Release.class);
    }

    private HttpRequest createHttpRequest(String method, String path, HttpRequest.BodyPublisher publisher) {
        final URI uri = configuration.host().resolve(path);
        final String accessToken = authenticator.getAccessToken();

        return HttpRequest.newBuilder()
                .method(method, publisher == null ? HttpRequest.BodyPublishers.noBody() : publisher)
                .uri(uri)
                .header("Accept", "application/json")
                .header("Accept-Charset", StandardCharsets.UTF_8.name())
                .header("Accept-Language", Locale.ENGLISH.toLanguageTag())
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("User-Agent", configuration.userAgent())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .timeout(configuration.readTimeout())
                .build();
    }

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
            throw new HttpResponseException("Konfigyr REST API returned a 5xx HTTP Status code with a following " +
                    "error response: " + response.body(), request, response);
        }

        if (response.statusCode() >= 400) {
            throw new HttpResponseException("Konfigyr REST API returned a 4xx HTTP Status code with a following " +
                    "error response: " + response.body(), request, response);
        }

        try {
            return mapper.readValue(response.body(), type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to convert HTTP response to: " + type.getTypeName(), e);
        }
    }

    @NonNull
    private static String generateArtifactPath(@NonNull Artifact artifact) {
        return "/artifacts/" + artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version();
    }
}
