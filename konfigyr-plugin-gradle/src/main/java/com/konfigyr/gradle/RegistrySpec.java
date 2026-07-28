package com.konfigyr.gradle;

import com.konfigyr.ClientCredentials;
import com.konfigyr.Credentials;
import com.konfigyr.Registry;
import com.konfigyr.TokenExchange;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.net.URI;

/**
 * Configures a single Konfigyr Artifactory registry within the {@code registries { } } container
 * exposed by {@link KonfigyrExtension}.
 * <p>
 * Every registry needs exactly one seed value, its {@link #getUrl() url} - the OAuth2 token endpoint
 * (and any other OAuth2 endpoint the plugin needs) is discovered from it at build time rather than
 * configured directly, and the Artifactory API is reached via fixed, plugin-internal relative paths
 * under that same origin.
 * <p>
 * Exactly one OAuth2 grant type must be configured, either {@link ClientCredentialsSpec client credentials}:
 *
 * <pre>{@code
 * registries {
 *     registry("staging") {
 *         url = uri("https://staging.konfigyr.io")
 *
 *         clientCredentials {
 *             clientId     = "acme-corp-client"
 *             clientSecret = "acme-corp-secret"
 *         }
 *     }
 * }}</pre>
 * <p>
 * or {@link TokenExchangeSpec token exchange}:
 *
 * <pre>{@code
 * registries {
 *     registry("staging") {
 *         url = uri("https://staging.konfigyr.io")
 *
 *         tokenExchange {
 *             clientId         = "acme-corp-client"
 *             subjectToken     = "..."
 *             subjectTokenType = "urn:ietf:params:oauth:token-type:jwt"
 *         }
 *     }
 * }}</pre>
 * <p>
 * If both blocks are configured, {@code tokenExchange} takes priority, since it prefers the
 * shorter-lived, revocable mechanism. Neither grant falls back to environment variables for a
 * registry created via {@link KonfigyrExtension#registry(String, Action)}. Every value must be set
 * explicitly. The reserved registry created via {@link KonfigyrExtension#konfigyrCentral()} is the
 * only exception, resolving unset values from the same {@code KONFIGYR_CLIENT_ID} /
 * {@code KONFIGYR_CLIENT_SECRET} / {@code KONFIGYR_SUBJECT_TOKEN} environment variables the plugin
 * has always supported.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 * @see KonfigyrExtension#getRegistries() Registries container
 * @see ClientCredentialsSpec
 * @see TokenExchangeSpec
 */
@Getter
@NullMarked
public class RegistrySpec implements Named {

    /**
     * The name under which this registry is declared in the {@code registries { } } container -
     * either the reserved {@value KonfigyrExtension#CENTRAL_REGISTRY_NAME} name, or a custom name
     * passed to {@link KonfigyrExtension#registry(String, Action)}. Also used to derive this
     * registry's per-registry Gradle task names (e.g. {@code publishArtifactMetadataToStaging}).
     */
    private final String name;

    /**
     * The registry's {@code url}. This is the sole discovery seed for this registry: its OAuth2
     * endpoints are resolved from it at build time, and the Artifactory API is reached via fixed
     * relative paths under this same origin. Must use {@code https}, unless the host is a loopback
     * address, which is only intended for local testing.
     */
    private final Property<URI> url;

    @Getter(lombok.AccessLevel.NONE)
    private final ObjectFactory objects;

    @Getter(lombok.AccessLevel.NONE)
    private final ProviderFactory providers;

    @Getter(lombok.AccessLevel.NONE)
    private final boolean useEnvironmentConventions;

    @Getter(lombok.AccessLevel.NONE)
    @Nullable
    private ClientCredentialsSpec clientCredentials;

    @Getter(lombok.AccessLevel.NONE)
    @Nullable
    private TokenExchangeSpec tokenExchange;

    /**
     * Creates a new {@link RegistrySpec}, instantiated by Gradle for each entry added to the
     * {@link KonfigyrExtension#getRegistries()} container.
     *
     * @param name the registry name, cannot be {@literal null}.
     * @param objects the Gradle object factory, cannot be {@literal null}.
     * @param providers the Gradle provider factory, used to resolve environment variable conventions,
     *                   cannot be {@literal null}.
     * @param useEnvironmentConventions {@literal true} only for the reserved
     *                                   {@value KonfigyrExtension#CENTRAL_REGISTRY_NAME} registry,
     *                                   enabling its environment variable fallbacks.
     */
    @Inject
    public RegistrySpec(String name, ObjectFactory objects, ProviderFactory providers, boolean useEnvironmentConventions) {
        this.name = name;
        this.objects = objects;
        this.providers = providers;
        this.useEnvironmentConventions = useEnvironmentConventions;

        this.url = objects.property(URI.class);

        if (useEnvironmentConventions) {
            this.url.convention(Registry.DEFAULT_HOST);
        }
    }

    /**
     * Configures {@link ClientCredentials}, for the OAuth2 {@code client_credentials} grant.
     *
     * @param action configures the client credentials, cannot be {@literal null}.
     */
    public void clientCredentials(Action<ClientCredentialsSpec> action) {
        if (clientCredentials == null) {
            clientCredentials = objects.newInstance(ClientCredentialsSpec.class, objects, providers, useEnvironmentConventions);
        }
        action.execute(clientCredentials);
    }

    /**
     * Configures a {@link TokenExchange}, for the OAuth2 Token Exchange grant.
     *
     * @param action configures the token exchange, cannot be {@literal null}.
     */
    public void tokenExchange(Action<TokenExchangeSpec> action) {
        if (tokenExchange == null) {
            tokenExchange = objects.newInstance(TokenExchangeSpec.class, objects, providers, useEnvironmentConventions);
        }
        action.execute(tokenExchange);
    }

    /**
     * Checks whether this registry has a {@link #url} and an OAuth2 grant type configured, with every
     * property that grant requires present, either directly or through its supported environment
     * variables.
     *
     * @return {@literal true} if this registry is fully configured.
     */
    boolean isConfigured() {
        return url.isPresent() && (
                (tokenExchange != null && tokenExchange.isConfigured())
                        || (clientCredentials != null && clientCredentials.isConfigured())
        );
    }

    /**
     * Creates the {@link Registry} described by this specification.
     * <p>
     * Callers must have already verified {@link #isConfigured()} returns {@literal true} before
     * calling this method, since it eagerly resolves every required property and fails otherwise.
     *
     * @return the registry, never {@literal null}.
     */
    Registry toRegistry() {
        KonfigyrExtension.assertPropertySet(url, "url", null);

        final URI registryUrl = url.get();
        assertHttps(registryUrl);

        return Registry.builder()
                .host(registryUrl)
                .credentials(resolveCredentials())
                .build();
    }

    private Credentials resolveCredentials() {
        if (tokenExchange != null && tokenExchange.isConfigured()) {
            return tokenExchange.toCredentials();
        }
        if (clientCredentials != null && clientCredentials.isConfigured()) {
            return clientCredentials.toCredentials();
        }

        throw new GradleException("Registry '" + name + "' requires credentials to be configured. Configure them " +
                "via registries { " + name + " { clientCredentials { } } } or " +
                "registries { " + name + " { tokenExchange { } } }.");
    }

    private void assertHttps(URI registryUrl) {
        if ("https".equalsIgnoreCase(registryUrl.getScheme())) {
            return;
        }

        if ("http".equalsIgnoreCase(registryUrl.getScheme()) && isLoopback(registryUrl.getHost())) {
            return;
        }

        throw new GradleException("Registry '" + name + "' url '" + registryUrl + "' must use HTTPS " +
                "(loopback addresses are exempt for local testing).");
    }

    private static boolean isLoopback(@Nullable String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    /**
     * Configures {@link ClientCredentials}, used for the OAuth2 {@code client_credentials} grant, as
     * defined by <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">RFC 6749, Section 4.4</a>.
     *
     * @author Vladimir Spasic
     * @since 1.2.0
     * @see RegistrySpec#clientCredentials(Action)
     */
    @Getter
    @NullMarked
    public static class ClientCredentialsSpec {

        /**
         * Specify the OAuth {@code client_id} that is used to get the OAuth access token. For the
         * reserved {@code konfigyrCentral} registry, this value can be specified by the
         * {@code KONFIGYR_CLIENT_ID} environment variable - every other registry requires it to be
         * set explicitly.
         */
        private final Property<String> clientId;

        /**
         * Specify the OAuth {@code client_secret} that is used to get the OAuth access token. For the
         * reserved {@code konfigyrCentral} registry, this value can be specified by the
         * {@code KONFIGYR_CLIENT_SECRET} environment variable - every other registry requires it to be
         * set explicitly.
         */
        private final Property<String> clientSecret;

        /**
         * Whether this grant falls back to Konfigyr's well-known environment variables
         * ({@code KONFIGYR_CLIENT_ID}/{@code KONFIGYR_CLIENT_SECRET}) when a property is left unset -
         * {@literal true} only for the reserved {@value KonfigyrExtension#CENTRAL_REGISTRY_NAME}
         * registry, every other registry must set every property explicitly.
         */
        private final boolean useEnvironmentConventions;

        /**
         * Creates a new {@link ClientCredentialsSpec}, instantiated by Gradle when
         * {@link RegistrySpec#clientCredentials(Action)} is first called for a given registry.
         *
         * @param factory the Gradle object factory, cannot be {@literal null}.
         * @param providers the Gradle provider factory, used to resolve environment variable
         *                   conventions, cannot be {@literal null}.
         * @param useEnvironmentConventions {@literal true} only for the reserved
         *                                   {@value KonfigyrExtension#CENTRAL_REGISTRY_NAME} registry,
         *                                   enabling its environment variable fallbacks.
         */
        @Inject
        public ClientCredentialsSpec(ObjectFactory factory, ProviderFactory providers, boolean useEnvironmentConventions) {
            this.useEnvironmentConventions = useEnvironmentConventions;

            clientId = factory.property(String.class);
            clientSecret = factory.property(String.class);

            if (useEnvironmentConventions) {
                clientId.convention(providers.environmentVariable("KONFIGYR_CLIENT_ID"));
                clientSecret.convention(providers.environmentVariable("KONFIGYR_CLIENT_SECRET"));
            }
        }

        /**
         * Checks whether both {@link #clientId} and {@link #clientSecret} are present, either set
         * directly or resolved from their environment variable conventions.
         *
         * @return {@literal true} if this grant is fully configured.
         */
        boolean isConfigured() {
            return clientId.isPresent() && clientSecret.isPresent();
        }

        /**
         * Creates the {@link ClientCredentials} described by this specification.
         * <p>
         * Callers must have already verified {@link #isConfigured()} returns {@literal true} before
         * calling this method, since it eagerly resolves every required property and fails otherwise.
         *
         * @return the credentials, never {@literal null}.
         */
        Credentials toCredentials() {
            KonfigyrExtension.assertPropertySet(clientId, "clientId", environmentVariableHint("KONFIGYR_CLIENT_ID"));
            KonfigyrExtension.assertPropertySet(clientSecret, "clientSecret", environmentVariableHint("KONFIGYR_CLIENT_SECRET"));

            return new ClientCredentials(clientId.get(), clientSecret.get());
        }

        private @Nullable String environmentVariableHint(String name) {
            return useEnvironmentConventions ? name : null;
        }

    }

    /**
     * Configures a {@link TokenExchange}, used for the OAuth2 Token Exchange grant, as defined by
     * <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>.
     *
     * @author Vladimir Spasic
     * @since 1.2.0
     * @see RegistrySpec#tokenExchange(Action)
     */
    @Getter
    @NullMarked
    public static class TokenExchangeSpec {

        /**
         * Specify the OAuth {@code client_id} that is used to get the OAuth access token. For the
         * reserved {@code konfigyrCentral} registry, this value can be specified by the
         * {@code KONFIGYR_CLIENT_ID} environment variable - every other registry requires it to be
         * set explicitly.
         */
        private final Property<String> clientId;

        /**
         * The token being exchanged for an access token. For the reserved {@code konfigyrCentral}
         * registry, this value can be specified by the {@code KONFIGYR_SUBJECT_TOKEN} environment
         * variable - every other registry requires it to be set explicitly.
         */
        private final Property<String> subjectToken;

        /**
         * An identifier, as defined by RFC 8693, for the type of the {@link #subjectToken} (for
         * example {@code urn:ietf:params:oauth:token-type:jwt} or
         * {@code urn:ietf:params:oauth:token-type:id_token}). There is no default value and no
         * environment variable fallback, even for the reserved {@code konfigyrCentral} registry -
         * this must always be set explicitly.
         */
        private final Property<String> subjectTokenType;

        /**
         * Whether this grant falls back to Konfigyr's well-known environment variables
         * ({@code KONFIGYR_CLIENT_ID}/{@code KONFIGYR_SUBJECT_TOKEN}) when a property is left unset -
         * {@literal true} only for the reserved {@value KonfigyrExtension#CENTRAL_REGISTRY_NAME}
         * registry, every other registry must set every property explicitly. Note that
         * {@link #subjectTokenType} never falls back to an environment variable, even here.
         */
        private final boolean useEnvironmentConventions;

        /**
         * Creates a new {@link TokenExchangeSpec}, instantiated by Gradle when
         * {@link RegistrySpec#tokenExchange(Action)} is first called for a given registry.
         *
         * @param factory the Gradle object factory, cannot be {@literal null}.
         * @param providers the Gradle provider factory, used to resolve environment variable
         *                   conventions, cannot be {@literal null}.
         * @param useEnvironmentConventions {@literal true} only for the reserved
         *                                   {@value KonfigyrExtension#CENTRAL_REGISTRY_NAME} registry,
         *                                   enabling its environment variable fallbacks.
         */
        @Inject
        public TokenExchangeSpec(ObjectFactory factory, ProviderFactory providers, boolean useEnvironmentConventions) {
            this.useEnvironmentConventions = useEnvironmentConventions;

            clientId = factory.property(String.class);
            subjectToken = factory.property(String.class);
            subjectTokenType = factory.property(String.class);

            if (useEnvironmentConventions) {
                clientId.convention(providers.environmentVariable("KONFIGYR_CLIENT_ID"));
                subjectToken.convention(providers.environmentVariable("KONFIGYR_SUBJECT_TOKEN"));
            }
        }

        /**
         * Checks whether {@link #clientId}, {@link #subjectToken} and {@link #subjectTokenType} are
         * all present, either set directly or resolved from their environment variable conventions.
         *
         * @return {@literal true} if this grant is fully configured.
         */
        boolean isConfigured() {
            return clientId.isPresent() && subjectToken.isPresent() && subjectTokenType.isPresent();
        }

        /**
         * Creates the {@link TokenExchange} described by this specification.
         * <p>
         * Callers must have already verified {@link #isConfigured()} returns {@literal true} before
         * calling this method, since it eagerly resolves every required property and fails otherwise.
         *
         * @return the credentials, never {@literal null}.
         */
        Credentials toCredentials() {
            KonfigyrExtension.assertPropertySet(clientId, "clientId", environmentVariableHint("KONFIGYR_CLIENT_ID"));
            KonfigyrExtension.assertPropertySet(subjectToken, "subjectToken", environmentVariableHint("KONFIGYR_SUBJECT_TOKEN"));
            KonfigyrExtension.assertPropertySet(subjectTokenType, "subjectTokenType", null);

            return new TokenExchange(clientId.get(), subjectToken.get(), subjectTokenType.get());
        }

        private @Nullable String environmentVariableHint(String name) {
            return useEnvironmentConventions ? name : null;
        }

    }

}
