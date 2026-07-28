package com.konfigyr;

import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

/**
 * Identifies a single Konfigyr Artifactory registry: where it is reachable and which credentials
 * authenticate against it.
 * <p>
 * This is the per-registry half of what {@link ArtifactoryClient} implementations need to
 * authenticate and communicate with an Artifactory service.
 * <p>
 * Transport-level settings shared across every registry in a build (timeouts, User-Agent) live
 * separately on {@link TransportOptions}.
 *
 * @param host        The base URL of the Konfigyr Artifactory API, also used as the discovery seed
 *                    for this registry's OAuth2 endpoints, never {@literal null}.
 * @param credentials Credentials used to authenticate with the Konfigyr Identity Provider, never {@literal null}.
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see Credentials
 * @see TransportOptions
 */
public record Registry(@NonNull URI host, @NonNull Credentials credentials) implements Serializable {

    @Serial
    private static final long serialVersionUID = 579252712638626059L;

    public static final URI DEFAULT_HOST = URI.create("https://api.konfigyr.com");

    public Registry {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(credentials, "credentials must not be null");
    }

    /**
     * Creates a new builder for constructing {@link Registry} instances.
     *
     * @return builder instance, never {@literal null}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for creating {@link Registry} instances.
     */
    public static final class Builder {
        private URI host = DEFAULT_HOST;
        private Credentials credentials;

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
         * Sets the credentials used to authenticate with the Konfigyr Identity Provider.
         *
         * @param credentials the credentials, never {@literal null}.
         * @return this builder instance for method chaining.
         * @see Credentials
         */
        public Builder credentials(Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        /**
         * Constructs a new {@link Registry} instance with the configured values.
         *
         * @return a new registry instance, never {@literal null}.
         */
        public Registry build() {
            return new Registry(host, credentials);
        }
    }
}
