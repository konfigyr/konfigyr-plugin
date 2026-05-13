package com.konfigyr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for connecting to the Konfigyr Artifactory REST API.
 * <p>
 * This configuration defines the connection parameters and OAuth2 credentials used by
 * {@link ArtifactoryClient} implementations to authenticate and communicate with the Artifactory service.
 *
 * @param host           The base URL of the Konfigyr Artifactory API, never {@literal null}.
 * @param clientId       OAuth2 client identifier, never {@literal null}.
 * @param clientSecret   OAuth2 client secret, never {@literal null}.
 * @param tokenUri       OAuth2 token endpoint URI, never {@literal null}.
 * @param namespace      Optional namespace or service identifier for scoping requests, may be {@literal null}.
 * @param service        The service identifier for scoping requests, never {@literal null}.
 * @param connectTimeout Connection timeout for HTTP requests, defaults to 10 seconds.
 * @param readTimeout    Read timeout for HTTP responses, defaults to 30 seconds.
 * @param userAgent      The User-Agent HTTP header value, defaults to {@code konfigyr-plugin}.
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public record ArtifactoryConfiguration(
        @NonNull URI host,
        @NonNull String clientId,
        @NonNull String clientSecret,
        @NonNull URI tokenUri,
        @NonNull String namespace,
        @NonNull String service,
        @Nullable String userAgent,
        @Nullable Duration connectTimeout,
        @Nullable Duration readTimeout
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 579252712638626059L;

    public static final URI DEFAULT_HOST = URI.create("https://api.konfigyr.com");
    public static final URI DEFAULT_TOKEN_URI = URI.create("https://id.konfigyr.com/oauth/token");

    public ArtifactoryConfiguration {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(clientId, "clientId must not be blank");
        Objects.requireNonNull(clientSecret, "clientSecret must not be blank");
        Objects.requireNonNull(tokenUri, "tokenUri must not be null");
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(service, "service must not be null");

        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
        if (userAgent == null) {
            userAgent = "konfigyr-plugin";
        }
    }

    /**
     * Creates a new builder for constructing {@link ArtifactoryConfiguration} instances.
     *
     * @return builder instance, never {@literal null}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for creating {@link ArtifactoryConfiguration} instances.
     */
    public static final class Builder {
        private URI host = DEFAULT_HOST;
        private URI tokenUri = DEFAULT_TOKEN_URI;
        private String clientId;
        private String clientSecret;
        private String service;
        private String namespace;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private String userAgent = "konfigyr-plugin";

        private Builder() {
            // Private constructor to enforce the builder pattern
        }

        /**
         * Sets the base URL of the Konfigyr Artifactory API.
         *
         * @param host the host URL as a string, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder host(String host) {
            return host(URI.create(host));
        }

        /**
         * Sets the base URL of the Konfigyr Artifactory API.
         *
         * @param host the host URL, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder host(URI host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the OAuth2 client identifier for authentication.
         *
         * @param clientId the OAuth2 client ID, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Sets the OAuth2 client secret for authentication.
         *
         * @param clientSecret the OAuth2 client secret, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /**
         * Sets the OAuth2 token endpoint URI.
         *
         * @param tokenUri the token endpoint URI as a string, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder tokenUri(String tokenUri) {
            return tokenUri(URI.create(tokenUri));
        }

        /**
         * Sets the OAuth2 token endpoint URI.
         *
         * @param tokenUri the token endpoint URI, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder tokenUri(URI tokenUri) {
            this.tokenUri = tokenUri;
            return this;
        }

        /**
         * Sets the namespace for scoping requests.
         *
         * @param namespace the namespace identifier, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Sets the service identifier for scoping requests.
         *
         * @param service the service identifier, never {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder service(String service) {
            this.service = service;
            return this;
        }

        /**
         * Sets the connection timeout for HTTP requests.
         *
         * @param connectTimeout the connection timeout duration, may be {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Sets the read timeout for HTTP responses.
         *
         * @param readTimeout the read timeout duration, may be {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * Sets the User-Agent HTTP header value.
         *
         * @param userAgent the user agent string, may be {@literal null}.
         * @return this builder instance for method chaining.
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Constructs a new {@link ArtifactoryConfiguration} instance with the configured values.
         *
         * @return a new configuration instance, never {@literal null}.
         */
        public ArtifactoryConfiguration build() {
            return new ArtifactoryConfiguration(host, clientId, clientSecret, tokenUri,
                    namespace, service, userAgent, connectTimeout, readTimeout);
        }
    }
}
