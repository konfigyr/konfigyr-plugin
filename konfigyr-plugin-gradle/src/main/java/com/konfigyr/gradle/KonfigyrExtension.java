package com.konfigyr.gradle;

import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.ClientCredentials;
import com.konfigyr.Credentials;
import com.konfigyr.TokenExchange;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration extension for the Konfigyr Gradle plugin.
 * <p>
 * Exposes the {@code konfigyr { }} DSL block in the consuming project's build script. Exactly one
 * OAuth2 grant type must be selected, either {@link ClientCredentialsSpec client credentials}:
 *
 * <pre>{@code
 * konfigyr {
 *     host = "https://api.konfigyr.io"            // defaults to https://api.konfigyr.com
 *     tokenUri = "https://id.konfigyr.io/oauth/token" // defaults to https://id.konfigyr.com/oauth/token
 *
 *     clientCredentials {
 *         clientId     = "acme-corp-client" or `KONFIGYR_CLIENT_ID` environment property
 *         clientSecret = "acme-corp-secret" or `KONFIGYR_CLIENT_SECRET` environment property
 *     }
 *
 *     // Optional: this project's service identity, needed only for the service-release scenario
 *     service {
 *         namespace = "acme-corp" or `KONFIGYR_NAMESPACE` environment property
 *         name      = "order-service" // defaults to project.name
 *     }
 *
 *     // Optional: direct-publish polling behavior
 *     publish {
 *         pollTimeout  = 10000L // defaults to 10 minutes
 *         pollInterval = 1000L  // defaults to 1 second
 *     }
 * }}</pre>
 * <p>
 * or {@link TokenExchangeSpec token exchange}:
 *
 * <pre>{@code
 * konfigyr {
 *     tokenExchange {
 *         clientId         = "acme-corp-client" or `KONFIGYR_CLIENT_ID` environment property
 *         subjectToken     = "..." or `KONFIGYR_SUBJECT_TOKEN` environment property
 *         subjectTokenType = "urn:ietf:params:oauth:token-type:jwt" // no default, must be set explicitly
 *     }
 * }}</pre>
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see ClientCredentialsSpec
 * @see TokenExchangeSpec
 * @see ServiceSpec
 * @see PublishSpec
 **/
@Getter
@NullMarked
public class KonfigyrExtension {

    /**
     * Host where the Konfigyr server is reachable, defaults to {@code https://api.konfigyr.com}.
     */
    private final Property<String> host;

    /**
     * The location where this plugin would perform the OAuth Token exchange, defaults to
     * {@code https://id.konfigyr.com/oauth/token}.
     */
    private final Property<String> tokenUri;

    /**
     * This project's service identity, used by the service-release scenario. Always present,
     * populated with its conventions regardless of whether {@link #service(Action)} is ever called -
     * configuring it is only needed to override those conventions.
     */
    private final ServiceSpec service;

    /**
     * Direct-publish polling behavior. Always present, populated with its conventions regardless of
     * whether {@link #publish(Action)} is ever called - configuring it is only needed to override
     * those conventions.
     */
    private final PublishSpec publish;

    @Getter(lombok.AccessLevel.NONE)
    private final ObjectFactory objects;

    @Getter(lombok.AccessLevel.NONE)
    private final ProviderFactory providers;

    /**
     * Whichever credentials grant type was last configured via {@link #clientCredentials(Action)} or
     * {@link #tokenExchange(Action)}, absent until one of those methods is called.
     */
    @Getter(lombok.AccessLevel.NONE)
    @Nullable
    private CredentialsSpec credentials;

    /**
     * Creates a new {@link KonfigyrExtension} instance.
     *
     * @param project the Gradle project
     * @param factory the Gradle object factory
     */
    public KonfigyrExtension(Project project, ObjectFactory factory) {
        this.objects = factory;
        this.providers = project.getProviders();

        host = factory.property(String.class).convention(ArtifactoryConfiguration.DEFAULT_HOST.toString());
        tokenUri = factory.property(String.class).convention(ArtifactoryConfiguration.DEFAULT_TOKEN_URI.toString());
        service = new ServiceSpec(factory, providers, project.getName());
        publish = new PublishSpec(factory);
    }

    /**
     * Configures {@link ClientCredentials}, for the OAuth2 {@code client_credentials} grant.
     *
     * @param action configures the client credentials, cannot be {@literal null}.
     */
    public void clientCredentials(Action<ClientCredentialsSpec> action) {
        final ClientCredentialsSpec spec = new ClientCredentialsSpec(objects, providers);
        action.execute(spec);
        credentials = spec;
    }

    /**
     * Configures a {@link TokenExchange}, for the OAuth2 Token Exchange grant.
     *
     * @param action configures the token exchange, cannot be {@literal null}.
     */
    public void tokenExchange(Action<TokenExchangeSpec> action) {
        final TokenExchangeSpec spec = new TokenExchangeSpec(objects, providers);
        action.execute(spec);
        credentials = spec;
    }

    /**
     * Configures this project's {@link ServiceSpec service identity}, used by the service-release
     * scenario.
     *
     * @param action configures the service identity, cannot be {@literal null}.
     */
    public void service(Action<ServiceSpec> action) {
        action.execute(service);
    }

    /**
     * Configures the {@link PublishSpec direct-publish polling behavior}.
     *
     * @param action configures the publish behavior, cannot be {@literal null}.
     */
    public void publish(Action<PublishSpec> action) {
        action.execute(publish);
    }

    /**
     * Checks whether this extension has an OAuth2 grant type configured, and that every property it
     * requires has a present value, either directly or through its supported environment variables.
     * <p>
     * Used to decide whether a project's own extension, or the root project's, should be the source
     * of truth for the shared {@link ArtifactoryService}'s connection configuration.
     *
     * @return {@literal true} if a credentials grant type is configured and fully resolvable.
     */
    boolean isConfigured() {
        return credentials != null && credentials.isConfigured();
    }

    /**
     * Creates the {@link ArtifactoryConfiguration} from the properties defined in this extension.
     *
     * @return the Artifactory plugin configuration, never {@literal null}.
     */
    ArtifactoryConfiguration toConfiguration() {
        if (credentials == null) {
            throw new GradleException("Konfigyr plugin requires credentials to be configured. Configure them via " +
                    "the konfigyr { clientCredentials { } } or konfigyr { tokenExchange { } } block.");
        }

        return ArtifactoryConfiguration.builder()
                .host(getHost().map(URI::create).getOrElse(ArtifactoryConfiguration.DEFAULT_HOST))
                .tokenUri(getTokenUri().map(URI::create).getOrElse(ArtifactoryConfiguration.DEFAULT_TOKEN_URI))
                .credentials(credentials.toCredentials())
                .userAgent("konfigyr-plugin/gradle")
                .build();
    }

    static void assertPropertySet(Property<?> property, String name, @Nullable String env) {
        if (!property.isPresent()) {
            final String suffix = env != null ? " or via the '" + env + "' environment variable" : "";

            throw new GradleException("Konfigyr plugin requires '" + name + "' to be set. " +
                    "Configure it in the konfigyr { } block" + suffix + ".");
        }
    }

    /**
     * Common contract for the credential specifications configurable on this extension, one per OAuth2
     * grant type supported by the Konfigyr Identity Provider.
     *
     * @see ClientCredentialsSpec
     * @see TokenExchangeSpec
     */
    private interface CredentialsSpec {

        /**
         * Checks whether every property required by this credential specification has a present
         * value, either configured directly or through one of its supported environment variables.
         *
         * @return {@literal true} if this specification is fully configured.
         */
        boolean isConfigured();

        /**
         * Creates the {@link Credentials} described by this specification.
         * <p>
         * Callers must have already verified {@link #isConfigured()} returns {@literal true} before
         * calling this method, since it eagerly resolves every required property and fails otherwise.
         *
         * @return the credentials, never {@literal null}.
         */
        Credentials toCredentials();

    }

    /**
     * Configures {@link ClientCredentials}, used for the OAuth2 {@code client_credentials} grant, as
     * defined by <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">RFC 6749, Section 4.4</a>.
     *
     * @author Vladimir Spasic
     * @since 1.1.0
     * @see KonfigyrExtension#clientCredentials(Action)
     */
    @Getter
    @NullMarked
    public static final class ClientCredentialsSpec implements CredentialsSpec {

        /**
         * Specify the OAuth {@code client_id} that is used to get the OAuth access token. This value
         * can be specified by the {@code KONFIGYR_CLIENT_ID} environment variable.
         */
        private final Property<String> clientId;

        /**
         * Specify the OAuth {@code client_secret} that is used to get the OAuth access token. This
         * value can be specified by the {@code KONFIGYR_CLIENT_SECRET} environment variable.
         */
        private final Property<String> clientSecret;

        ClientCredentialsSpec(ObjectFactory factory, ProviderFactory providers) {
            clientId = factory.property(String.class).convention(providers.environmentVariable("KONFIGYR_CLIENT_ID"));
            clientSecret = factory.property(String.class).convention(providers.environmentVariable("KONFIGYR_CLIENT_SECRET"));
        }

        @Override
        public boolean isConfigured() {
            return clientId.isPresent() && clientSecret.isPresent();
        }

        @Override
        public Credentials toCredentials() {
            assertPropertySet(clientId, "clientId", "KONFIGYR_CLIENT_ID");
            assertPropertySet(clientSecret, "clientSecret", "KONFIGYR_CLIENT_SECRET");

            return new ClientCredentials(clientId.get(), clientSecret.get());
        }

    }

    /**
     * Configures a {@link TokenExchange}, used for the OAuth2 Token Exchange grant, as defined by
     * <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>.
     *
     * @author Vladimir Spasic
     * @since 1.1.0
     * @see KonfigyrExtension#tokenExchange(Action)
     */
    @Getter
    @NullMarked
    public static final class TokenExchangeSpec implements CredentialsSpec {

        /**
         * Specify the OAuth {@code client_id} that is used to get the OAuth access token. This value
         * can be specified by the {@code KONFIGYR_CLIENT_ID} environment variable.
         */
        private final Property<String> clientId;

        /**
         * The token being exchanged for an access token. This value can be specified by the
         * {@code KONFIGYR_SUBJECT_TOKEN} environment variable.
         */
        private final Property<String> subjectToken;

        /**
         * An identifier, as defined by RFC 8693, for the type of the {@link #subjectToken} (for
         * example {@code urn:ietf:params:oauth:token-type:jwt} or
         * {@code urn:ietf:params:oauth:token-type:id_token}). There is no default value, this must
         * always be set explicitly.
         */
        private final Property<String> subjectTokenType;

        TokenExchangeSpec(ObjectFactory factory, ProviderFactory providers) {
            clientId = factory.property(String.class).convention(providers.environmentVariable("KONFIGYR_CLIENT_ID"));
            subjectToken = factory.property(String.class).convention(providers.environmentVariable("KONFIGYR_SUBJECT_TOKEN"));
            subjectTokenType = factory.property(String.class);
        }

        @Override
        public boolean isConfigured() {
            return clientId.isPresent() && subjectToken.isPresent() && subjectTokenType.isPresent();
        }

        @Override
        public Credentials toCredentials() {
            assertPropertySet(clientId, "clientId", "KONFIGYR_CLIENT_ID");
            assertPropertySet(subjectToken, "subjectToken", "KONFIGYR_SUBJECT_TOKEN");
            assertPropertySet(subjectTokenType, "subjectTokenType", null);

            return new TokenExchange(clientId.get(), subjectToken.get(), subjectTokenType.get());
        }

    }

    /**
     * This project's service identity, used to resolve its dependencies and open a service release
     * against the Konfigyr Artifactory. Only needed for the service-release scenario - a pure library
     * with no {@link #namespace} configured skips it entirely.
     *
     * @author Vladimir Spasic
     * @since 1.1.0
     * @see KonfigyrExtension#service(Action)
     */
    @Getter
    @NullMarked
    public static final class ServiceSpec {

        /**
         * Specify the Konfigyr namespace to which this service belongs to. This value can be specified
         * by the {@code KONFIGYR_NAMESPACE} environment variable.
         */
        private final Property<String> namespace;

        /**
         * Specify the Konfigyr service name for which this plugin would upload the configuration
         * metadata, defaults to the current project name.
         */
        private final Property<String> name;

        ServiceSpec(ObjectFactory factory, ProviderFactory providers, String projectName) {
            namespace = factory.property(String.class).convention(providers.environmentVariable("KONFIGYR_NAMESPACE"));
            name = factory.property(String.class).convention(projectName);
        }

    }

    /**
     * Polling behavior for the direct-publish scenario, used while waiting for a
     * {@code com.konfigyr.artifactory.Publication} to be confirmed after
     * {@code PublishArtifactMetadataTask} uploads this project's own artifact metadata.
     *
     * @author Vladimir Spasic
     * @since 1.1.0
     * @see KonfigyrExtension#publish(Action)
     */
    @Getter
    @NullMarked
    public static final class PublishSpec {

        /**
         * The maximum time in milliseconds to wait for a successful poll of a release. Defaults to 10 minutes.
         * <p>
         * This property defines the overall timeout for the polling process. If a release has not been
         * successfully detected within this duration, the poll job will fail, preventing the build from
         * hanging indefinitely.
         */
        private final Property<Long> pollTimeout;

        /**
         * The initial time interval in milliseconds between consecutive polling attempts to check for a release.
         * Defaults to one second.
         * <p>
         * This property specifies the starting interval for an exponential backoff strategy. If a poll attempt
         * fails, the next interval will be multiplied by 1.75. This allows for more rapid retries initially,
         * with a gracefully increasing delay for persistent failures. The backoff will continue until a successful
         * poll occurs or the overall {@link #pollTimeout} is reached.
         */
        private final Property<Long> pollInterval;

        PublishSpec(ObjectFactory factory) {
            pollTimeout = factory.property(Long.class).convention(Duration.ofMinutes(10).toMillis());
            pollInterval = factory.property(Long.class).convention(Duration.ofSeconds(1).toMillis());
        }

    }

}
