package com.konfigyr.gradle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.konfigyr.*;
import com.konfigyr.artifactory.*;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.DeserializationFeature;
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
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see ArtifactoryClient
 */
public abstract class ArtifactoryService implements BuildService<ArtifactoryService.@NonNull Parameters> {

    private final Logger logger = Logging.getLogger(ArtifactoryService.class);

    private final ArtifactoryClient client;
    private final JsonMapper mapper;

    /**
     * Creates a new {@link ArtifactoryService} instance.
     */
    public ArtifactoryService() {
        this.mapper = JsonMapper.builder()
                .addModule(new ArtifactoryJacksonModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(inclusion -> inclusion
                        .withContentInclusion(JsonInclude.Include.NON_EMPTY)
                        .withValueInclusion(JsonInclude.Include.NON_EMPTY)
                )
                .build();
        this.client = new DefaultArtifactoryClient(logger, getParameters().getConfiguration().get(), mapper);
    }

    @VisibleForTesting
    ArtifactoryService(ArtifactoryClient client) {
        this.client = client;
        this.mapper = JsonMapper.builder()
                .addModule(new ArtifactoryJacksonModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(inclusion -> inclusion
                        .withContentInclusion(JsonInclude.Include.NON_EMPTY)
                        .withValueInclusion(JsonInclude.Include.NON_EMPTY)
                )
                .build();
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
     * Attempts to publish a new {@link ServiceRelease} for the given namespace and service.
     *
     * @param namespace the namespace owning the service, cannot be {@literal null}.
     * @param service the service this release is opened for, cannot be {@literal null}.
     * @param candidates the release candidate artifacts to be added to the new release, cannot be {@literal null}.
     * @return the service release, never {@literal null}
     */
    public ServiceRelease release(@NonNull String namespace, @NonNull String service,
                                   @NonNull Collection<? extends ServiceReleaseCandidate> candidates) {
        final ServiceRelease release = client.release(namespace, service, candidates);

        logger.info("Successfully created release for service [id={}, state={}] with artifacts: {}",
                release.id(), release.state(), release.artifacts());

        return release;
    }

    /**
     * Uploads the given {@link ArtifactMetadata} for the given {@link ServiceRelease}. Intended to be
     * called from a {@link ServiceReleaseArtifactUploadAction}, one artifact per work item.
     *
     * @param namespace the namespace owning the service, cannot be {@literal null}.
     * @param service the service this release belongs to, cannot be {@literal null}.
     * @param release the service release this upload contributes to, cannot be {@literal null}.
     * @param metadata the artifact metadata payload to upload, cannot be {@literal null}.
     * @throws PublishException if the upload fails.
     */
    public void upload(@NonNull String namespace, @NonNull String service,
                        @NonNull ServiceRelease release, @NonNull ArtifactMetadata metadata) {
        final String coordinates = formatCoordinates(metadata, '.');

        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to upload Artifact({}) metadata for service release {}", coordinates, release.id());
        }

        try {
            client.upload(namespace, service, release, metadata);
        } catch (Exception ex) {
            throw new PublishException("Failed to upload Artifact(%s) metadata for service release %s"
                    .formatted(coordinates, release.id()), ex);
        }

        logger.lifecycle("Successfully uploaded Artifact({}) metadata for service release {}", coordinates, release.id());
    }

    /**
     * Completes the given {@link ServiceRelease}, promoting it to the service's current {@link Manifest}.
     * Every {@link ServiceReleaseEntry} requiring an upload must already have been uploaded via
     * {@link #upload(String, String, ServiceRelease, ArtifactMetadata)} before this is called.
     *
     * @param namespace the namespace owning the service, cannot be {@literal null}.
     * @param service the service this release belongs to, cannot be {@literal null}.
     * @param release the release to complete, cannot be {@literal null}.
     * @return the completed release, never {@literal null}.
     * @throws PublishException if the release could not be completed.
     */
    @NonNull
    public ServiceRelease complete(@NonNull String namespace, @NonNull String service, @NonNull ServiceRelease release) {
        final ServiceRelease completed;

        try {
            completed = client.complete(namespace, service, release);
        } catch (Exception ex) {
            throw new PublishException("Failed to complete service release " + release.id(), ex);
        }

        logger.info("Successfully completed service release [id={}, state={}] with artifacts: {}",
                completed.id(), completed.state(), completed.artifacts());

        return completed;
    }

    /**
     * Starts the publication process for the given {@link ArtifactMetadata}. This method would post the
     * metadata to the Konfigyr Artifactory and then poll the service until the publication state is either
     * successfully published or failed.
     *
     * @param metadata the artifact metadata to publish, cannot be {@literal null}.
     * @param timeout the maximum time to wait for a successful poll of the release, cannot be {@literal null}.
     * @param interval the time interval between consecutive polling attempts, cannot be {@literal null}.
     * @throws PublishException if the poll process timed out or the artifact metadata upload fails.
     */
    public void publish(@NonNull ArtifactMetadata metadata, @NonNull Duration timeout, @NonNull Duration interval) {
        final String coordinates = formatCoordinates(metadata, '.');

        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to publish artifact metadata for Artifact({})", coordinates);
        }

        if (client.isPublished(metadata)) {
            logger.lifecycle("Artifact({}) is already published in the Artifactory", coordinates);
            return;
        }

        Publication publication;

        try {
            publication = client.publish(metadata);
        } catch (Exception ex) {
            throw new PublishException("Failed to upload Artifact(%s) to Artifactory".formatted(coordinates), ex);
        }

        final BackOffExecution execution = new BackOffExecution(interval.toMillis(), timeout.toMillis());

        while (publication.state() == PublicationState.PENDING) {
            final long backOff = execution.nextBackOff();

            if (backOff == BackOffExecution.STOP) {
                throw new PublishException("Publication is still pending for Artifact(%s) after polling timeout is exceeded"
                        .formatted(coordinates));
            }

            logger.info("Artifact({}) is not yet published, polling for status update...", coordinates);

            try {
                Thread.sleep(backOff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Artifact publication state polling interrupted", e);
            }

            publication = client.getPublication(metadata);
        }

        if (publication.state() == PublicationState.PUBLISHED) {
            logger.lifecycle("Publication has been successfully processed for Artifact({})", coordinates);
        } else {
            logger.warn("Could not create publication for Artifact({}) with errors: {}", coordinates, publication.errors());
        }
    }

    static URLClassLoader createClassLoader(@NonNull Iterable<? extends File> files) {
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

    interface Parameters extends BuildServiceParameters {

        Property<@NotNull ArtifactoryConfiguration> getConfiguration();

    }

    static final class BackOffExecution {
        static final long STOP = -1;

        private final long interval;
        private final long timeout;

        private long elapsed = 0;
        private int attempts = 0;

        BackOffExecution(long interval, long timeout) {
            this.interval = interval;
            this.timeout = timeout;
        }

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
