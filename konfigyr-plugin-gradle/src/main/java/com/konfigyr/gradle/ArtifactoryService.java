package com.konfigyr.gradle;

import com.konfigyr.*;
import com.konfigyr.artifactory.*;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Gradle {@link BuildService} for interacting with the Konfigyr Artifactory REST API.
 * <p>
 * Registered once, exactly, for the whole build via {@code registerIfAbsent} and shared
 * across every project and task that needs it, rather than one instance per project.
 * Builds one {@link ArtifactoryClient} per configured {@link Registry}, all sharing a single
 * {@link ArtifactoryClientFactory}, and therefore a single connection pool and a single
 * token/discovery cache. Every method that talks to a registry takes its name as the first
 * argument to select which of those clients to use, this service itself is keyed by the
 * registry name rather than bound to a single one.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see ArtifactoryClient
 */
@NullMarked
public abstract class ArtifactoryService implements BuildService<ArtifactoryService.Parameters> {

    private final Logger logger = Logging.getLogger(ArtifactoryService.class);

    private final Map<String, ArtifactoryClient> clients;
    private final JsonMapper mapper;

    /**
     * Creates a new {@link ArtifactoryService} instance.
     */
    public ArtifactoryService() {
        this.mapper = ArtifactoryClientFactory.createDefaultJsonMapper();

        final ArtifactoryClientFactory factory = new ArtifactoryClientFactory(
                TransportOptions.builder().userAgent("konfigyr-plugin/gradle").build(),
                this.mapper
        );

        final Map<String, Registry> registries = getParameters().getConfigurations().get();
        final Map<String, ArtifactoryClient> clients = new LinkedHashMap<>(registries.size());
        registries.forEach((name, registry) -> clients.put(name, factory.create(registry)));

        this.clients = Collections.unmodifiableMap(clients);
    }

    /**
     * Creates a new {@link ArtifactoryService} instance with a pre-built set of clients, bypassing the
     * usual {@link Parameters}-driven construction. Only intended for tests.
     *
     * @param clients the clients to use, keyed by registry name, cannot be {@literal null}.
     */
    @VisibleForTesting
    ArtifactoryService(Map<String, ArtifactoryClient> clients) {
        this.mapper = ArtifactoryClientFactory.createDefaultJsonMapper();
        this.clients = clients;
    }

    private ArtifactoryClient resolveClient(String registryName) {
        final ArtifactoryClient client = clients.get(registryName);

        if (client == null) {
            throw new GradleException("Registry '" + registryName + "' is not fully configured. Make sure it " +
                    "declares a 'url' and either 'clientCredentials { }' or 'tokenExchange { }' in the " +
                    "konfigyr { registries { } } block.");
        }

        return client;
    }

    /**
     * Attempts to parse the given collection of {@link ArtifactMetadataResource}s into a
     * list of {@link PropertyDescriptor}s.
     * <p>
     * This method would create a {@link ClassLoader} that can resolve the Java types to construct
     * the {@link com.konfigyr.artifactory.JsonSchema} for each property using the specified collection
     * of classpath files, usually jars.
     *
     * @param metadata the collection of Spring Boot configuration metadata resources, cannot be {@literal null}.
     * @param classpath the collection of files used to create a {@link ClassLoader}, cannot be {@literal null}.
     * @return list of parsed {@link PropertyDescriptor}, never {@literal null}.
     */
    public List<PropertyDescriptor> parsePropertyDescriptors(
            Iterable<? extends ArtifactMetadataResource> metadata,
            Iterable<? extends File> classpath
    ) {
        final URLClassLoader classLoader = createClassLoader(classpath);
        final List<PropertyDescriptor> descriptors;

        try {
            descriptors = new ArtifactMetadataParser(classLoader).parse(metadata);
        } finally {
            try {
                classLoader.close();
            } catch (IOException ex) {
                logger.warn("Failed to close artifact metadata class loader", ex);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Successfully generated {} property descriptors", descriptors.size());
        }

        return descriptors;
    }

    /**
     * Generates and writes {@link PropertyDescriptor} metadata extracted from the given collection
     * of {@link ArtifactMetadataResource}s to the given target {@link File}.
     * <p>
     * This method would create a {@link ClassLoader} that can resolve the Java types to construct
     * the {@link com.konfigyr.artifactory.JsonSchema} for each property using the specified collection
     * of classpath files, usually jars.
     *
     * @param metadata the collection of Spring Boot configuration metadata resources, cannot be {@literal null}.
     * @param classpath the collection of files used to create a {@link ClassLoader}, cannot be {@literal null}.
     * @param target the target file where the metadata should be written, cannot be {@literal null}.
     */
    public void writePropertyDescriptorMetadata(
            Iterable<? extends ArtifactMetadataResource> metadata,
            Iterable<? extends File> classpath,
            File target
    ) {
        mapper.writeValue(target, parsePropertyDescriptors(metadata, classpath));
    }

    /**
     * Creates an {@link ArtifactMetadata} instances for the given {@link Artifact} by loading the serialized
     * configuration metadata from the given {@link File}.
     *
     * @param artifact the artifact for which the metadata is created, cannot be {@literal null}.
     * @param metadata the file containing the serialized configuration metadata, cannot be {@literal null}.
     * @return the artifact metadata that should be published, never {@literal null}.
     */
    public ArtifactMetadata createArtifactMetadata(Artifact artifact, File metadata) {
        final JavaType descriptorsType = mapper.getTypeFactory().constructCollectionType(List.class, PropertyDescriptor.class);
        final List<PropertyDescriptor> descriptors = mapper.readValue(metadata, descriptorsType);

        if (logger.isDebugEnabled()) {
            logger.debug("Successfully loaded {} property descriptors for: {}", descriptors.size(), artifact);
        }

        return artifact.toMetadata(descriptors);
    }

    /**
     * Writes the given {@link ArtifactMetadata} to the given directory. The file that would be created, or
     * updated, would be using the following file name format: {@code ${groupId}-${artifactId}-${version}.json}
     *
     * @param metadata artifact metadata to be written
     * @param targetDirectory the target directory where the metadata should be written
     * @return the name of the file that was written, never {@literal null}.
     */
    public String writeArtifactMetadata(ArtifactMetadata metadata, File targetDirectory) {
        final String fileName = formatCoordinates(metadata, '-') + ".json";
        mapper.writeValue(new File(targetDirectory, fileName), metadata);
        return fileName;
    }

    /**
     * Writes the given {@link ArtifactMetadata} to the exact given target file, unlike
     * {@link #writeArtifactMetadata(ArtifactMetadata, File)} which derives a filename from the
     * artifact's coordinates inside a target directory. Used for the metadata of the artifact that
     * represents the service (project) itself, where there is always at most one file and no need
     * to derive its name.
     *
     * @param metadata artifact metadata to be written
     * @param target the exact file to write the metadata to
     */
    public void writeServiceArtifactMetadata(ArtifactMetadata metadata, File target) {
        mapper.writeValue(target, metadata);
    }

    /**
     * Reads a single {@link ArtifactMetadata} previously written by
     * {@link #writeServiceArtifactMetadata(ArtifactMetadata, File)}.
     *
     * @param metadata the exact file to read the metadata from, cannot be {@literal null}.
     * @return the artifact metadata, never {@literal null}.
     */
    public ArtifactMetadata readArtifactMetadata(File metadata) {
        return mapper.readValue(metadata, ArtifactMetadata.class);
    }

    /**
     * Reads {@link ArtifactMetadata} from the given directory that are present in the artifact manifest list.
     * <p>
     * This method should collect all the filenames in the artifact manifest file and then attempt to deserialize
     * the metadata files from the given output directory.
     *
     * @param manifest the manifest file that contains the list of metadata files to be uploaded
     * @param directory the directory where the metadata files are located
     * @return list of artifact metadata, never {@literal null}.
     * @throws IOException if an I/O error occurs while reading the metadata files.
     */
    public List<ArtifactMetadata> readArtifactMetadata(File manifest, File directory) throws IOException {
        try (final Stream<String> filenames = Files.lines(manifest.toPath())) {
            return filenames
                    .map(filename -> new File(directory, filename))
                    .map(file -> mapper.readValue(file, ArtifactMetadata.class))
                    .toList();
        }
    }

    /**
     * Attempts to publish a new {@link ServiceRelease} for the given service, against the named
     * registry. The namespace owning the service is resolved server-side from the registry's
     * authenticated access token, not asserted by the caller.
     *
     * @param registryName the registry to publish the release to, cannot be {@literal null}.
     * @param service the service this release is opened for, cannot be {@literal null}.
     * @param candidates the release candidate artifacts to be added to the new release, cannot be {@literal null}.
     * @return the service release, never {@literal null}
     */
    public ServiceRelease release(String registryName, String service, Collection<? extends ServiceReleaseCandidate> candidates) {
        final ServiceRelease release = resolveClient(registryName).release(service, candidates);

        logger.info("Successfully created release for service [id={}, state={}] with artifacts: {} on registry {}",
                release.id(), release.state(), release.artifacts(), registryName);

        return release;
    }

    /**
     * Uploads the given {@link ArtifactMetadata} for the given {@link ServiceRelease}, against the
     * named registry. Intended to be called from a {@link ServiceReleaseArtifactUploadAction}, one
     * artifact per work item.
     *
     * @param registryName the registry the release was opened against, cannot be {@literal null}.
     * @param service the service this release belongs to, cannot be {@literal null}.
     * @param release the service release this upload contributes to, cannot be {@literal null}.
     * @param metadata the artifact metadata payload to upload, cannot be {@literal null}.
     * @throws PublishException if the upload fails.
     */
    public void upload(String registryName, String service, ServiceRelease release, ArtifactMetadata metadata) {
        final String coordinates = formatCoordinates(metadata, '.');

        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to upload Artifact({}) metadata for service release {} on registry {}",
                    coordinates, release.id(), registryName);
        }

        try {
            resolveClient(registryName).upload(service, release, metadata);
        } catch (Exception ex) {
            throw new PublishException("Failed to upload Artifact(%s) metadata for service release %s on registry %s"
                    .formatted(coordinates, release.id(), registryName), ex);
        }

        logger.lifecycle("Successfully uploaded Artifact({}) metadata for service release {} on registry {}",
                coordinates, release.id(), registryName);
    }

    /**
     * Completes the given {@link ServiceRelease}, promoting it to the service's current {@link Manifest}
     * on the named registry. Every {@link ServiceReleaseEntry} requiring an upload must already have
     * been uploaded via {@link #upload(String, String, ServiceRelease, ArtifactMetadata)}
     * before this is called.
     *
     * @param registryName the registry the release was opened against, cannot be {@literal null}.
     * @param service the service this release belongs to, cannot be {@literal null}.
     * @param release the release to complete, cannot be {@literal null}.
     * @return the completed release, never {@literal null}.
     * @throws PublishException if the release could not be completed.
     */
    public ServiceRelease complete(String registryName, String service, ServiceRelease release) {
        final ServiceRelease completed;

        try {
            completed = resolveClient(registryName).complete(service, release);
        } catch (Exception ex) {
            throw new PublishException("Failed to complete service release " + release.id() + " on registry " + registryName, ex);
        }

        logger.info("Successfully completed service release [id={}, state={}] with artifacts: {} on registry {}",
                completed.id(), completed.state(), completed.artifacts(), registryName);

        return completed;
    }

    /**
     * Starts the publication process for the given {@link ArtifactMetadata} against the named
     * registry. This method would post the metadata to the Konfigyr Artifactory and then poll the
     * service until the publication state is either successfully published or failed.
     *
     * @param registryName the registry to publish the artifact metadata to, cannot be {@literal null}.
     * @param metadata the artifact metadata to publish, cannot be {@literal null}.
     * @param timeout the maximum time to wait for a successful poll of the release, cannot be {@literal null}.
     * @param interval the time interval between consecutive polling attempts, cannot be {@literal null}.
     * @throws PublishException if the poll process timed out or the artifact metadata upload fails.
     */
    public void publish(String registryName, ArtifactMetadata metadata, Duration timeout, Duration interval) {
        final ArtifactoryClient client = resolveClient(registryName);
        final String coordinates = formatCoordinates(metadata, '.');

        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to publish artifact metadata for Artifact({}) on registry {}", coordinates, registryName);
        }

        if (client.isPublished(metadata)) {
            logger.info("Artifact({}) is already published in the Artifactory on registry {}", coordinates, registryName);
            return;
        }

        Publication publication;

        try {
            publication = client.publish(metadata);
        } catch (Exception ex) {
            throw new PublishException("Failed to upload Artifact(%s) to Artifactory on registry %s"
                    .formatted(coordinates, registryName), ex);
        }

        final BackOffExecution execution = new BackOffExecution(interval.toMillis(), timeout.toMillis());

        while (publication.state() == PublicationState.PENDING) {
            final long backOff = execution.nextBackOff();

            if (backOff == BackOffExecution.STOP) {
                throw new PublishException("Publication is still pending for Artifact(%s) on registry %s after polling timeout is exceeded"
                        .formatted(coordinates, registryName));
            }

            logger.info("Artifact({}) is not yet published on registry {}, polling for status update...", coordinates, registryName);

            try {
                Thread.sleep(backOff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Artifact publication state polling interrupted", e);
            }

            publication = client.getPublication(metadata);
        }

        if (publication.state() == PublicationState.PUBLISHED) {
            logger.lifecycle("Publication has been successfully processed for Artifact({}) on registry {}", coordinates, registryName);
        } else {
            logger.warn("Could not create publication for Artifact({}) on registry {} with errors: {}",
                    coordinates, registryName, publication.errors());
        }
    }

    static URLClassLoader createClassLoader(Iterable<? extends File> files) {
        final URL[] classpath = StreamSupport.stream(files.spliterator(), false)
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (MalformedURLException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(URL[]::new);

        return new URLClassLoader(classpath, ClassLoader.getSystemClassLoader());
    }

    static String formatCoordinates(Artifact artifact, char joiner) {
        return artifact.groupId() + joiner + artifact.artifactId() + joiner + artifact.version();
    }

    /**
     * Build service parameters for {@link ArtifactoryService}, resolved once, lazily, when the shared
     * service is first realized. See {@link KonfigyrPlugin} for how these are populated.
     */
    interface Parameters extends BuildServiceParameters {

        /**
         * Every {@link Registry} to build a client for, keyed by registry name.
         *
         * @return the registry configurations, never {@literal null}.
         */
        MapProperty<String, Registry> getConfigurations();

    }

    /**
     * Exponential backoff calculator used by {@link #publish(String, ArtifactMetadata, Duration, Duration)}
     * while polling for a {@link Publication} to leave the {@code PENDING} state.
     */
    static final class BackOffExecution {
        static final long STOP = -1;

        private final long interval;
        private final long timeout;

        private long elapsed = 0;
        private int attempts = 0;

        /**
         * Creates a new {@link BackOffExecution}.
         *
         * @param interval the initial time interval, in milliseconds, between polling attempts.
         * @param timeout the overall time budget, in milliseconds, allowed for polling.
         */
        BackOffExecution(long interval, long timeout) {
            this.interval = interval;
            this.timeout = timeout;
        }

        /**
         * Returns the number of milliseconds to wait before the next polling attempt, or {@link #STOP}
         * once the timeout has been exceeded or too many attempts have been made.
         *
         * @return the next backoff delay in milliseconds, or {@link #STOP}.
         */
        long nextBackOff() {
            // we reached the max attempts, or the timeout is exceeded, stop polling...
            if (elapsed >= timeout || attempts >= 60) {
                return STOP;
            }
            attempts++;
            elapsed += interval;
            return interval;
        }
    }

}
