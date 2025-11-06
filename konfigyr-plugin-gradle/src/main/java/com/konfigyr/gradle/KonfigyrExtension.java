package com.konfigyr.gradle;

import com.konfigyr.ArtifactoryConfiguration;
import lombok.Getter;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.time.Duration;

/**
 * Gradle extension for Konfigyr plugin.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
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
     * Specify the Konfigyr service name for which this plugin would upload the configuration metadata,
     * defaults to the current project name.
     */
    private final Property<String> service;

    /**
     * Specify the Konfigyr namespace to which this service belongs to. This value can be specified
     * by the {@code KONFIGYR_NAMESPACE} environment variable.
     */
    private final Property<String> namespace;

    /**
     * Specify the OAuth {@code client_id} that is used to get the OAuth access token. This value
     * can be specified by the {@code KONFIGYR_CLIENT_ID} environment variable.
     */
    private final Property<String> clientId;

    /**
     * Specify the OAuth {@code client_secret} that is used to get the OAuth access token. This value
     * can be specified by the {@code KONFIGYR_CLIENT_SECRET} environment variable.
     */
    private final Property<String> clientSecret;

    /**
     * The maximum time in milliseconds to wait for a successful poll of a release. Defaults to 10 minutes.
     * <p>
     * This property defines the overall timeout for the polling process. If a release has not been
     * successfully detected within this duration, the poll job will fail, preventing the build from
     * hanging indefinitely.
     */
    private final Property<Long> releasePollTimeout;

    /**
     * The initial time interval in milliseconds between consecutive poll attempts to check for a release.
     * Defaults to one second.
     * <p>
     * This property specifies the starting interval for an exponential backoff strategy. If a poll attempt
     * fails, the next interval will be multiplied by 1.75. This allows for more rapid retries initially,
     * with a gracefully increasing delay for persistent failures. The backoff will continue until a successful
     * poll occurs or the overall {@link #releasePollTimeout} is reached.
     */
    private final Property<Long> releasePollInterval;

    public KonfigyrExtension(Project project, ObjectFactory factory) {
        host = factory.property(String.class).convention(ArtifactoryConfiguration.DEFAULT_HOST.toString());
        tokenUri = factory.property(String.class).convention(ArtifactoryConfiguration.DEFAULT_TOKEN_URI.toString());
        service = factory.property(String.class).convention(project.getName());
        namespace = factory.property(String.class).convention(System.getenv("KONFIGYR_NAMESPACE"));
        clientId = factory.property(String.class).convention(System.getenv("KONFIGYR_CLIENT_ID"));
        clientSecret = factory.property(String.class).convention(System.getenv("KONFIGYR_CLIENT_SECRET"));
        releasePollTimeout = factory.property(Long.class).convention(Duration.ofMinutes(10).toMillis());
        releasePollInterval = factory.property(Long.class).convention(Duration.ofSeconds(1).toMillis());
    }

    /**
     * Creates the {@link ArtifactoryConfiguration} from the properties defined in this extension.
     *
     * @return the Artifactory plugin configuration, never {@literal null}.
     */
    ArtifactoryConfiguration toConfiguration() {
        return ArtifactoryConfiguration.builder()
                .host(getHost().map(URI::create).getOrElse(ArtifactoryConfiguration.DEFAULT_HOST))
                .tokenUri(getTokenUri().map(URI::create).getOrElse(ArtifactoryConfiguration.DEFAULT_TOKEN_URI))
                .service(getService().get())
                .namespace(getNamespace().get())
                .clientId(getClientId().get())
                .clientSecret(getClientSecret().get())
                .build();
    }
}
