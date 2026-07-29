package com.konfigyr;

import com.google.common.net.HttpHeaders;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Default {@link OAuthClientCredentialsProvider} implementation.
 * <p>
 * Access tokens are cached per {@link Registry}, each expiring one minute before the
 * lifetime the token endpoint actually reported for it, via a {@link Cache} shared across every
 * registry this instance is asked about - stateless with respect to any one registry, so a single
 * instance is meant to be constructed once and reused for a whole build's worth of registries.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see OAuthClientCredentialsProvider
 */
@NullMarked
final class DefaultOAuthClientCredentialsProvider implements OAuthClientCredentialsProvider {

    private final static String DEFAULT_OAUTH_SCOPES =
            URLEncoder.encode("artifactory:publish namespaces:publish-releases", StandardCharsets.UTF_8);
    private final static String CLIENT_CREDENTIALS_FORM_PARAMETERS =
            "grant_type=client_credentials&client_id=%s&client_secret=%s&scope=%s";
    private final static String TOKEN_EXCHANGE_FORM_PARAMETERS =
            "grant_type=urn:ietf:params:oauth:grant-type:token-exchange&client_id=%s&subject_token=%s&subject_token_type=%s&scope=%s";

    /**
     * How much earlier than its actual lifetime a cached {@link AccessToken} is treated as expired, to
     * account for clock skew between this plugin and the token server.
     */
    private final static long CLOCK_SKEW_SECONDS = 60;

    private final Logger logger = LoggerFactory.getLogger(DefaultOAuthClientCredentialsProvider.class);
    private final Cache<Registry, AccessToken> cache = new Cache<>(AccessToken::expiry);

    private final JsonMapper mapper;
    private final Transport transport;
    private final AuthorizationServerMetadataResolver resolver;

    /**
     * Creates a new {@link DefaultOAuthClientCredentialsProvider}, building its own
     * {@link AuthorizationServerMetadataResolver} from the given {@link Transport}.
     *
     * @param mapper the JSON mapper to use, cannot be {@literal null}.
     * @param transport the transport used to fetch tokens, cannot be {@literal null}.
     */
    DefaultOAuthClientCredentialsProvider(JsonMapper mapper, Transport transport) {
        this(mapper, transport, new AuthorizationServerMetadataResolver(mapper, transport));
    }

    /**
     * Creates a new {@link DefaultOAuthClientCredentialsProvider} with a custom {@link JsonMapper}.
     *
     * @param mapper the JSON mapper to use, cannot be {@literal null}.
     * @param transport the transport used to fetch tokens, cannot be {@literal null}.
     * @param resolver the resolver used to discover each registry's token endpoint, cannot be {@literal null}.
     */
    DefaultOAuthClientCredentialsProvider(JsonMapper mapper, Transport transport, AuthorizationServerMetadataResolver resolver) {
        this.mapper = Objects.requireNonNull(mapper, "JSON mapper must not be null");
        this.transport = Objects.requireNonNull(transport, "Transport must not be null");
        this.resolver = Objects.requireNonNull(resolver, "Authorization Server Metadata resolver must not be null");
    }

    @Override
    public String getAccessToken(Registry registry) {
        return cache.get(registry, () -> requestToken(registry)).token();
    }

    private AccessToken requestToken(Registry registry) {
        final URI tokenUri = resolver.resolve(registry).tokenEndpoint();

        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to obtain OAuth2 access token from: {}", tokenUri);
        }

        final String form = buildFormBody(registry.credentials());

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(tokenUri)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name())
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        final HttpResponse<String> response = transport.send(request);

        if (response.statusCode() != 200) {
            throw new HttpResponseException("Could not obtain OAuth2 access token due to server error response: "
                    + response.body(), response);
        }

        try {
            final JsonNode json = mapper.readTree(response.body());

            final String token = getNodeValue(json, "access_token", JsonNode::asString).orElseThrow(
                    () -> new IllegalStateException("Failed to extract OAuth2 access token from server response")
            );

            final long expiresIn = getNodeValue(json, "expires_in", JsonNode::asLong).orElse(3600L);

            logger.info("Successfully obtained OAuth Access Token for registry '{}' that expires in {} seconds",
                    registry.host(), expiresIn);

            return new AccessToken(token, expiresIn);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to extract OAuth2 access token from server response", e);
        }
    }

    /**
     * Extracts and maps the value node found under the given key, if present and a JSON value node
     * (not an object or array).
     *
     * @param node the JSON node to look up the key in, cannot be {@literal null}.
     * @param key the field name to look up, cannot be {@literal null}.
     * @param mapper maps the found value node to the desired type, cannot be {@literal null}.
     * @return the mapped value, or {@link Optional#empty()} if the key is absent or not a value node.
     */
    static <T> Optional<T> getNodeValue(JsonNode node, String key, Function<JsonNode, T> mapper) {
        return Optional.ofNullable(node.get(key))
                .filter(JsonNode::isValueNode)
                .map(mapper);
    }

    /**
     * Builds the {@code application/x-www-form-urlencoded} request body for the given {@link Credentials},
     * encoding it according to the OAuth2 grant type it represents.
     *
     * @param credentials the credentials to encode, cannot be {@literal null}.
     * @return the encoded form body, never {@literal null}.
     */
    private static String buildFormBody(Credentials credentials) {
        return switch (credentials) {
            case ClientCredentials(String clientId, String clientSecret) ->
                    CLIENT_CREDENTIALS_FORM_PARAMETERS.formatted(
                            URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                            URLEncoder.encode(clientSecret, StandardCharsets.UTF_8),
                            DEFAULT_OAUTH_SCOPES
                    );
            case TokenExchange(String clientId, String subjectToken, String subjectTokenType) ->
                    TOKEN_EXCHANGE_FORM_PARAMETERS.formatted(
                            URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                            URLEncoder.encode(subjectToken, StandardCharsets.UTF_8),
                            URLEncoder.encode(subjectTokenType, StandardCharsets.UTF_8),
                            DEFAULT_OAUTH_SCOPES
                    );
        };
    }

    private record AccessToken(String token, long expiresIn) {

        Duration expiry() {
            // renew CLOCK_SKEW_SECONDS early, unless the token's actual lifetime is shorter than
            // that margin, in which case the full lifetime is used instead of an immediate expiry
            final long ttl = expiresIn > CLOCK_SKEW_SECONDS ? expiresIn - CLOCK_SKEW_SECONDS : expiresIn;
            return Duration.ofSeconds(ttl);
        }

    }

}
