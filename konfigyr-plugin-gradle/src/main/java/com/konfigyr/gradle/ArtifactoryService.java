package com.konfigyr.gradle;

import com.konfigyr.ArtifactMetadataParser;
import com.konfigyr.ArtifactoryClient;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.DefaultArtifactoryClient;
import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.Manifest;
import com.konfigyr.artifactory.Release;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ArtifactMetadataParser parser;

    private Manifest manifest;

    public ArtifactoryService() {
        this.client = new DefaultArtifactoryClient(logger, getParameters().getConfiguration().get());
        this.parser = createParser(getParameters().getClasspath().get());
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
        if (manifest == null) {
            manifest = client.getManifest();
        }
        return manifest;
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
     * Starts the upload process for the given {@link GradleArtifact}.
     *
     * @param artifact the artifact to upload, cannot be {@literal null}.
     * @return the release state, never {@literal null}.
     */
    @NonNull
    public Release upload(@NonNull GradleArtifact artifact) {
        final Manifest manifest = getManifest();

        if (manifest.contains(artifact)) {
            throw new IllegalStateException("Artifact(%s) is already used by the service(%s)".formatted(
                    artifact.coordinates(), manifest.name()
            ));
        }

        final Set<Resource> resources = artifact.metadata().stream()
                .map(FileSystemResource::new)
                .collect(Collectors.toUnmodifiableSet());

        final ArtifactMetadata metadata = parser.parse(artifact, resources);

        logger.debug("Created artifact metadata from Artifact({})", artifact.coordinates());

        return client.upload(metadata);
    }

    /**
     * This method retrieves the manifest for the current Gradle project and serializes it to JSON byte array
     * after all the artifact metadata has been uploaded.
     * <p>
     * Do not confuse this method with {@link #getManifest()} which retrieves the manifest from the Konfigyr service
     * to compare the service artifact usage state.
     *
     * @return manifest as JSON byte array
     */
    public byte[] generateManifest() {
        final Manifest manifest = client.getManifest();
        return new JsonMapper().writeValueAsBytes(manifest);
    }

    static ArtifactMetadataParser createParser(@NonNull Iterable<File> files) {
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

        return new ArtifactMetadataParser(new URLClassLoader(classpath, ClassUtils.getDefaultClassLoader()));
    }

    interface Parameters extends BuildServiceParameters {

        Property<@NotNull ArtifactoryConfiguration> getConfiguration();

        ListProperty<@NotNull File> getClasspath();

    }

}
