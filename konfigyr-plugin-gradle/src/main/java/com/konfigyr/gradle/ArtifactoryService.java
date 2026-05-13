package com.konfigyr.gradle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.konfigyr.ArtifactMetadataParser;
import com.konfigyr.ArtifactoryClient;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.DefaultArtifactoryClient;
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
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;
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
     * Attempts to parse the given collection of {@link Resource}s into a list of {@link PropertyDescriptor}s.
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
            Iterable<? extends Resource> metadata,
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
     * of {@link Resource}s to the given target {@link File}.
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
            Iterable<? extends Resource> metadata,
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
     * Download the manifest for the current Gradle project that matches the Konfigyr service.
     * <p>
     * This manifest is used to check if this plugin should upload a new state of configuration metadata for the
     * Konfigyr service.
     *
     * @return the current manifest, never {@literal null}.
     */
    @NonNull
    public Manifest getManifest() {
        return client.getManifest();
    }

    /**
     * Retrieves the release state for the uploaded {@link ArtifactMetadata}.
     *
     * @param artifact the artifact for which release is retrieved, cannot be {@literal null}.
     * @return the release state, never {@literal null}.
     */
    @NonNull
    public Release getRelease(@NonNull Artifact artifact) {
        return client.getRelease(artifact);
    }

    /**
     * Attempts to publish a new {@link Manifest} for the current Gradle project.
     *
     * @param artifacts the artifacts to be added to the new manifest, cannot be {@literal null}.
     */
    public void publish(@NonNull Collection<? extends Artifact> artifacts) {
        final Manifest manifest = client.publish(artifacts);

        logger.info("Successfully published Manifest for service [id={}, name={}] with artifacts: {}",
                manifest.id(), manifest.name(), manifest.artifacts());
    }

    /**
     * Starts the upload process for the given {@link ArtifactMetadata}. This method would post the
     * metadata to the Konfigyr Artifactory and then poll the service until the release state is either
     * successfully released or failed.
     *
     * @param metadata the artifact metadata to upload, cannot be {@literal null}.
     * @throws PublishException if the poll process timed out or the artifact metadata upload fails.
     */
    public void upload(@NonNull ArtifactMetadata metadata) {
        final String coordinates = formatCoordinates(metadata, '.');

        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to upload artifact metadata for Artifact({})", coordinates);
        }

        if (client.isReleased(metadata)) {
            logger.lifecycle("Release for Artifact({}) is already present in the Artifactory", coordinates);
            return;
        }

        Release release;

        try {
            release = client.upload(metadata);
        } catch (Exception ex) {
            throw new PublishException("Failed to upload Artifact(%s) to Artifactory".formatted(coordinates), ex);
        }

        final BackOffExecution execution = createBackOffExecution(getParameters());

        while (release.state() == ReleaseState.PENDING) {
            final long timeout = execution.nextBackOff();

            if (timeout == BackOffExecution.STOP) {
                throw new PublishException("Release is still pending for Artifact(%s) after polling timeout is exceeded"
                        .formatted(coordinates));
            }

            logger.info("Release is not yet complete for Artifact({}), polling for status update...", coordinates);

            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Artifact release state polling interrupted", e);
            }

            release = client.getRelease(metadata);
        }

        if (release.state() == ReleaseState.RELEASED) {
            logger.lifecycle("Release has been successfully processed for Artifact({})", coordinates);
        } else {
            logger.warn("Could not process release for Artifact({}) with errors: {}", coordinates, release.errors());
        }
    }

    static BackOffExecution createBackOffExecution(ArtifactoryService.Parameters parameters) {
        final ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(parameters.getInterval().get().toMillis());
        backOff.setMaxElapsedTime(parameters.getTimeout().get().toMillis());
        backOff.setMultiplier(1.75);
        backOff.setMaxAttempts(Integer.MAX_VALUE);
        return backOff.start();
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

        return new URLClassLoader(classpath, ClassUtils.getDefaultClassLoader());
    }

    static String formatCoordinates(Artifact artifact, char joiner) {
        return artifact.groupId() + joiner + artifact.artifactId() + joiner + artifact.version();
    }

    interface Parameters extends BuildServiceParameters {

        Property<@NotNull ArtifactoryConfiguration> getConfiguration();

        Property<@NonNull Duration> getTimeout();

        Property<@NonNull Duration> getInterval();

    }

}
