package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;

/**
 * Settings for a {@link Transport}, shared across every {@link Registry} configured for a build.
 * <p>
 * Unlike {@link Registry}, which identifies a single Konfigyr environment, these settings are
 * the same regardless of how many registries are configured.
 *
 * @param userAgent      The User-Agent HTTP header value, defaults to {@code konfigyr-plugin}.
 * @param connectTimeout Connection timeout for HTTP requests, defaults to 10 seconds.
 * @param readTimeout    Read timeout for HTTP responses, defaults to 30 seconds.
 * @author Vladimir Spasic
 * @since 1.2.0
 * @see Registry
 * @see Transport
 */
@NullMarked
public record TransportOptions(String userAgent, Duration connectTimeout, Duration readTimeout) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The default {@link TransportOptions}, using every builder default.
     */
    public static final TransportOptions DEFAULT = builder().build();

    /**
     * Creates a new builder for constructing {@link TransportOptions} instances.
     *
     * @return builder instance, never {@literal null}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for creating {@link TransportOptions} instances.
     */
    public static final class Builder {
        private String userAgent = "konfigyr-plugin";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);

        private Builder() {
            // Private constructor to enforce the builder pattern
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
         * Constructs a new {@link TransportOptions} instance with the configured values.
         *
         * @return a new options instance, never {@literal null}.
         */
        public TransportOptions build() {
            return new TransportOptions(userAgent, connectTimeout, readTimeout);
        }
    }
}
