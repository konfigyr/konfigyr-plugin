package com.konfigyr;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Provides OAuth2 Client Credentials authentication for Artifactory API requests.
 * <p>
 * This provider handles token acquisition and caching for the lifetime of the client.
 * When a token expires, it automatically refreshes it by invoking the configured token endpoint.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
final class OAuthClientCredentialsProvider {

    private final static String FORM_PARAMETERS = "grant_type=client_credentials&client_id=%s&client_secret=%s";

    private final Logger logger;
    private final JsonMapper mapper;
    private final HttpClient httpClient;
    private final ArtifactoryConfiguration configuration;

    private String accessToken;
    private Instant expiresAt;

    OAuthClientCredentialsProvider(HttpClient httpClient, ArtifactoryConfiguration configuration) {
        this(new JsonMapper(), httpClient, configuration);
    }

    OAuthClientCredentialsProvider(JsonMapper mapper, HttpClient httpClient, ArtifactoryConfiguration configuration) {
        this(LoggerFactory.getLogger(OAuthClientCredentialsProvider.class), mapper, httpClient, configuration);
    }

    OAuthClientCredentialsProvider(Logger logger, JsonMapper mapper, HttpClient httpClient, ArtifactoryConfiguration configuration) {
        Assert.notNull(logger, "Logger must not be null");
        Assert.notNull(mapper, "JSON mapper must not be null");
        Assert.notNull(httpClient, "HTTP Client must not be null");
        Assert.notNull(configuration, "Artifactory client configuration must not be null");

        this.logger = logger;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.configuration = configuration;
    }

    /**
     * Returns a valid OAuth2 access token, refreshing it if necessary.
     *
     * @return Bearer token string, never {@literal null}.
     */
    @NonNull
    synchronized String getAccessToken() {
        if (accessToken == null || Instant.now().isAfter(expiresAt)) {
            refreshToken();
        }
        return accessToken;
    }

    private void refreshToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to obtain OAuth2 access token from: {}", configuration.tokenUri());
        }

        final String form = FORM_PARAMETERS.formatted(
                URLEncoder.encode(configuration.clientId(), StandardCharsets.UTF_8),
                URLEncoder.encode(configuration.clientSecret(), StandardCharsets.UTF_8)
        );

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(configuration.tokenUri())
                .header("Accept", "application/json")
                .header("Accept-Charset", StandardCharsets.UTF_8.name())
                .header("Accept-Language", Locale.ENGLISH.toLanguageTag())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", configuration.userAgent())
                .header("X-Request-ID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

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

        if (response.statusCode() != 200) {
            throw new HttpResponseException("Could not obtain OAuth2 access token due to server error response: "
                    + response.body(), request, response);
        }

        try {
            final JsonNode json = mapper.readTree(response.body());

            accessToken = getNodeValue(json, "access_token", JsonNode::asText).orElseThrow(
                    () -> new IllegalStateException("Failed to extract OAuth2 access token from server response")
            );

            final long expiresIn = getNodeValue(json, "expires_in", JsonNode::asLong).orElse(3600L);
            expiresAt = Instant.now().plusSeconds(expiresIn - 60); // renew 1 minute early

            logger.info("Successfully obtained OAuth Access Token that expires at: {}", expiresAt);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to extract OAuth2 access token from server response", e);
        }
    }

    static <T> Optional<T> getNodeValue(JsonNode node, String key, Function<JsonNode, T> mapper) {
        return Optional.ofNullable(node.get(key))
                .filter(JsonNode::isValueNode)
                .map(mapper);
    }

}
