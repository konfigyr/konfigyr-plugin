package com.konfigyr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.konfigyr.artifactory.ArtifactoryJacksonModule;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * Creates {@link ArtifactoryClient} instances for any number of registries, sharing one transport,
 * {@link OAuthClientCredentialsProvider} and {@link JsonMapper} across every client it creates,
 * rather than each opening its own connection pool, token/discovery cache, and mapper.
 * <p>
 * A single instance is meant to be constructed once per build and asked to {@link #create(Registry)}
 * a client for every configured registry.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 * @see ArtifactoryClient
 */
@NullMarked
public final class ArtifactoryClientFactory {

    private final Transport transport;
    private final OAuthClientCredentialsProvider authenticator;
    private final JsonMapper mapper;

    /**
     * Creates a default {@link JsonMapper} with the {@link ArtifactoryJacksonModule} registered.
     *
     * @return the default JSON mapper, never {@literal null}.
     */
    public static JsonMapper createDefaultJsonMapper() {
        return JsonMapper.builder()
                .addModule(new ArtifactoryJacksonModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(inclusion -> inclusion
                        .withContentInclusion(JsonInclude.Include.NON_EMPTY)
                        .withValueInclusion(JsonInclude.Include.NON_EMPTY)
                )
                .build();
    }

    /**
     * Creates a new {@link ArtifactoryClientFactory}, building its own transport and
     * {@link OAuthClientCredentialsProvider} from the given {@link TransportOptions}.
     *
     * @param options the transport options, cannot be {@literal null}.
     */
    public ArtifactoryClientFactory(TransportOptions options) {
        this(new Transport(options), createDefaultJsonMapper());
    }

    /**
     * Creates a new {@link ArtifactoryClientFactory}, with a custom {@link JsonMapper},
     * with a shared HTTP transport built from the given {@link TransportOptions}.
     *
     * @param options the transport options, cannot be {@literal null}.
     * @param mapper the JSON mapper to share across every created client, cannot be {@literal null}.
     */
    public ArtifactoryClientFactory(TransportOptions options, JsonMapper mapper) {
        this(new Transport(options), mapper);
    }

    /**
     * Creates a new {@link ArtifactoryClientFactory}, building the default {@link JsonMapper},
     * with a shared HTTP transport built from the given {@link TransportOptions}.
     *
     * @param options the transport options, cannot be {@literal null}.
     * @param authenticator the OAuth2 access token provider to share across every created client, cannot be {@literal null}.
     */
    public ArtifactoryClientFactory(TransportOptions options, OAuthClientCredentialsProvider authenticator) {
        this(new Transport(options), authenticator, createDefaultJsonMapper());
    }

    private ArtifactoryClientFactory(Transport transport, JsonMapper mapper) {
        this(transport, new DefaultOAuthClientCredentialsProvider(mapper, transport), mapper);
    }

    /**
     * Creates a new {@link ArtifactoryClientFactory} with a custom {@link JsonMapper}, with a shared
     * HTTP transport built from the given {@link TransportOptions}.
     *
     * @param options the transport options, cannot be {@literal null}.
     * @param authenticator the OAuth2 access token provider to share across every created client, cannot be {@literal null}.
     * @param mapper the JSON mapper to share across every created client, cannot be {@literal null}.
     */
    public ArtifactoryClientFactory(TransportOptions options, OAuthClientCredentialsProvider authenticator, JsonMapper mapper) {
        this(new Transport(options), authenticator, mapper);
    }

    private ArtifactoryClientFactory(Transport transport, OAuthClientCredentialsProvider authenticator, JsonMapper mapper) {
        this.transport = Objects.requireNonNull(transport, "Transport must not be null");
        this.authenticator = Objects.requireNonNull(authenticator, "OAuth2 access token provider must not be null");
        this.mapper = Objects.requireNonNull(mapper, "JSON mapper must not be null");
    }

    /**
     * Creates a new {@link ArtifactoryClient} for the given registry, sharing this factory's
     * transport, {@link OAuthClientCredentialsProvider} and {@link JsonMapper} rather than building
     * its own.
     *
     * @param registry the registry to create a client for, cannot be {@literal null}.
     * @return a new client for the given registry, never {@literal null}.
     */
    public ArtifactoryClient create(Registry registry) {
        return new DefaultArtifactoryClient(transport, authenticator, registry, mapper);
    }

}
