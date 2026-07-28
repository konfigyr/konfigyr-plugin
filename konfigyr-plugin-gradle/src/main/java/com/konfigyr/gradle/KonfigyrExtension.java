package com.konfigyr.gradle;

import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Configuration extension for the Konfigyr Gradle plugin.
 * <p>
 * Exposes the {@code konfigyr { }} DSL block in the consuming project's build script. Every registry
 * this project publishes to is declared in the {@link #registries} container, either the well-known
 * {@link #konfigyrCentral()} registry:
 *
 * <pre>{@code
 * konfigyr {
 *     registries {
 *         konfigyrCentral() // credentials resolve from KONFIGYR_CLIENT_ID/KONFIGYR_CLIENT_SECRET/
 *                            // KONFIGYR_SUBJECT_TOKEN environment variables, exactly as before
 *     }
 *
 *     // Optional: only needed for the service-release scenario - calling this block at all is what
 *     // opts a project into it, regardless of what's configured inside
 *     service {
 *         name = "order-service" // defaults to project.name
 *     }
 *
 *     // Optional: direct-publish polling behavior
 *     publish {
 *         pollTimeout  = 10000L // defaults to 10 minutes
 *         pollInterval = 1000L  // defaults to 1 second
 *     }
 * }}</pre>
 * <p>
 * or a custom, self-hosted registry, which requires an explicit {@code url} and every credential set
 * explicitly - no environment variable defaulting applies:
 *
 * <pre>{@code
 * konfigyr {
 *     registries {
 *         registry("staging") {
 *             url = uri("https://staging.konfigyr.io")
 *
 *             tokenExchange {
 *                 clientId         = "acme-corp-client"
 *                 subjectToken     = "..."
 *                 subjectTokenType = "urn:ietf:params:oauth:token-type:jwt"
 *             }
 *         }
 *     }
 * }}</pre>
 * <p>
 * Every declared registry receives its own publish/release request when the relevant task runs,
 * there is no "active" or "selected" registry, mirroring how Gradle's own {@code maven-publish}
 * plugin generates one publish task per declared repository.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see RegistrySpec
 * @see ServiceSpec
 * @see PublishSpec
 **/
@Getter
@NullMarked
public class KonfigyrExtension {

    /**
     * The reserved name under which {@link #konfigyrCentral()} registers its registry.
     */
    static final String CENTRAL_REGISTRY_NAME = "konfigyrCentral";

    /**
     * Every registry this project should publish its artifact metadata and service releases to.
     * <p>
     * Populated either via {@link #konfigyrCentral()} or {@link #registry(String, Action)}.
     */
    private final NamedDomainObjectContainer<RegistrySpec> registries;

    /**
     * This project's service identity, used by the service-release scenario. Always present,
     * populated with its conventions regardless of whether {@link #service(Action)} is ever called,
     * configuring it is only needed to override those conventions.
     */
    private final ServiceSpec service;

    /**
     * Direct-publish polling behavior. Always present, populated with its conventions regardless of
     * whether {@link #publish(Action)} is ever called, configuring it is only needed to override
     * those conventions.
     */
    private final PublishSpec publish;

    @Getter(lombok.AccessLevel.NONE)
    private final ObjectFactory objects;

    @Getter(lombok.AccessLevel.NONE)
    private final ProviderFactory providers;

    /**
     * Creates a new {@link KonfigyrExtension} instance.
     *
     * @param project the Gradle project
     * @param factory the Gradle object factory
     */
    public KonfigyrExtension(Project project, ObjectFactory factory) {
        this.objects = factory;
        this.providers = project.getProviders();

        registries = factory.domainObjectContainer(RegistrySpec.class,
                name -> factory.newInstance(RegistrySpec.class, name, objects, providers, CENTRAL_REGISTRY_NAME.equals(name)));

        service = new ServiceSpec(factory, project.getName());
        publish = new PublishSpec(factory);
    }

    /**
     * Configures the {@link #registries} container directly, using Gradle's own container idiom.
     *
     * @param action configures the registries container, cannot be {@literal null}.
     */
    public void registries(Action<NamedDomainObjectContainer<RegistrySpec>> action) {
        action.execute(registries);
    }

    /**
     * Registers (or looks up) the well-known Konfigyr Central registry under the reserved
     * {@value #CENTRAL_REGISTRY_NAME} name. Its {@link RegistrySpec#getUrl() url} defaults to
     * {@link com.konfigyr.Registry#DEFAULT_HOST}, and its credentials resolve from the
     * {@code KONFIGYR_CLIENT_ID}/{@code KONFIGYR_CLIENT_SECRET}/{@code KONFIGYR_SUBJECT_TOKEN}
     * environment variables when not set explicitly.
     *
     * @return the Konfigyr Central registry, never {@literal null}.
     */
    public RegistrySpec konfigyrCentral() {
        final RegistrySpec registry = registries.maybeCreate(CENTRAL_REGISTRY_NAME);
        registry.clientCredentials(ignore -> {});
        registry.tokenExchange(ignore -> {});
        return registry;
    }

    /**
     * Registers (or looks up) the well-known Konfigyr Central registry, applying the given action to
     * override its conventions.
     *
     * @param action configures the registry, cannot be {@literal null}.
     * @return the Konfigyr Central registry, never {@literal null}.
     * @see #konfigyrCentral()
     */
    public RegistrySpec konfigyrCentral(Action<RegistrySpec> action) {
        final RegistrySpec spec = konfigyrCentral();
        action.execute(spec);
        return spec;
    }

    /**
     * Registers (or looks up) a custom, self-hosted registry under the given name. Every value,
     * including its {@link RegistrySpec#getUrl() url} and its credentials, must be set explicitly,
     * no environment variable defaulting applies, unlike {@link #konfigyrCentral()}.
     *
     * @param name the registry name, cannot be the reserved {@value #CENTRAL_REGISTRY_NAME} name.
     * @param action configures the registry, cannot be {@literal null}.
     * @return the registry, never {@literal null}.
     */
    public RegistrySpec registry(String name, Action<RegistrySpec> action) {
        if (CENTRAL_REGISTRY_NAME.equals(name)) {
            throw new GradleException("'" + CENTRAL_REGISTRY_NAME + "' is a reserved registry name, use " +
                    "konfigyr { konfigyrCentral() } to configure it instead.");
        }

        final RegistrySpec spec = registries.maybeCreate(name);
        action.execute(spec);
        return spec;
    }

    /**
     * Opts this project into the service-release scenario, configuring its {@link ServiceSpec service
     * identity}. Calling this at all, regardless of what's configured inside, is what enables the
     * scenario; a pure library that never calls it skips it entirely.
     *
     * @param action configures the service identity, cannot be {@literal null}.
     */
    public void service(Action<ServiceSpec> action) {
        action.execute(service);
        service.markConfigured();
    }

    /**
     * Configures the {@link PublishSpec direct-publish polling behavior}.
     *
     * @param action configures the publication behavior, cannot be {@literal null}.
     */
    public void publish(Action<PublishSpec> action) {
        action.execute(publish);
    }

    static void assertPropertySet(Property<?> property, String name, @Nullable String env) {
        if (!property.isPresent()) {
            final String suffix = env != null ? " or via the '" + env + "' environment variable" : "";

            throw new GradleException("Konfigyr plugin requires '" + name + "' to be set. " +
                    "Configure it in the konfigyr { } block" + suffix + ".");
        }
    }

    /**
     * This project's service identity, used to resolve its dependencies and open a service release
     * against the Konfigyr Artifactory. Only needed for the service-release scenario, a pure library
     * that never calls {@link KonfigyrExtension#service(Action)} skips it entirely.
     * <p>
     * The namespace owning this service is never configured here, or anywhere else, in the build
     * script. It is always resolved server-side from the registry's authenticated access token.
     *
     * @author Vladimir Spasic
     * @since 1.1.0
     * @see KonfigyrExtension#service(Action)
     */
    @Getter
    @NullMarked
    public static final class ServiceSpec {

        /**
         * Specify the Konfigyr service name for which this plugin would upload the configuration
         * metadata, defaults to the current project name.
         */
        private final Property<String> name;

        private boolean configured;

        ServiceSpec(ObjectFactory factory, String projectName) {
            name = factory.property(String.class).convention(projectName);
        }

        /**
         * Checks whether {@link KonfigyrExtension#service(Action)} was ever called for this project,
         * regardless of what was configured inside it. This is what opts a project into the
         * service-release scenario.
         *
         * @return {@literal true} if the service-release scenario is enabled for this project.
         */
        boolean isConfigured() {
            return configured;
        }

        void markConfigured() {
            configured = true;
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
