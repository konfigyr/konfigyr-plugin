package com.konfigyr.gradle;

import com.konfigyr.ArtifactMetadataResource;
import com.konfigyr.ArtifactMetadataScanner;
import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.PropertyDescriptor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that scans this project's built JAR file archive for Spring Boot configuration metadata
 * and, if found, writes it out as {@link ArtifactMetadata} describing this project's artifact.
 * <p>
 * This is the shared first step of both publishing scenarios: {@link PublishArtifactMetadataTask}
 * publishes this metadata directly to the Artifactory, and {@link CreateServiceReleaseTask}
 * includes it as one of the candidates for the service's own release, alongside every scanned
 * dependency from {@link ResolveServiceDependenciesTask}. If this project's jar does not expose
 * any Spring Boot configuration metadata, no output is written and both downstream tasks treat
 * that artifact as absent.
 *
 * @author Vladimir Spasic
 * @since 1.1.0
 */
@CacheableTask
public abstract class GenerateArtifactMetadataTask extends DefaultTask {

    /**
     * The name of the task to be registered in the Gradle build.
     */
    static final String NAME = "generateArtifactMetadata";

    /**
     * Returns the {@link ArtifactoryService} to use for constructing and writing the artifact metadata.
     *
     * @return the artifactory service to use, never {@literal null}.
     */
    @Internal
    public abstract Property<ArtifactoryService> getService();

    /**
     * This project's built jar, scanned for Spring Boot configuration metadata.
     *
     * @return this project's built jar, never {@literal null}.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectArchive();

    /**
     * This project's own compile/runtime classpath, used purely to build a {@link ClassLoader}
     * capable of resolving Java types referenced by this project's own Spring Boot configuration
     * metadata (e.g. a property whose type is declared in one of this project's dependencies rather
     * than in its own compiled classes). Never scanned for its own resource files — unlike
     * {@link #getProjectArchive()} — so {@code @CompileClasspath} normalization (ABI-only, ignores
     * resources) is correct here, same as {@link ArtifactMetadataTransform#getDependencies()}.
     *
     * @return this project's classpath, never {@literal null}.
     */
    @InputFiles
    @CompileClasspath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /**
     * This project's Maven coordinates, used to build the {@link ArtifactMetadata} when this
     * project's jar exposes Spring Boot configuration metadata. Absent if this project has no
     * resolvable {@code groupId}/{@code artifactId}/{@code version} (e.g. a root project with no
     * {@code group}/{@code version} of its own), in which case this task has nothing to generate
     * regardless of what its jar contains.
     *
     * @return this project's artifact coordinates, never {@literal null}.
     */
    @Input
    @Optional
    public abstract Property<Artifact> getProjectArtifact();

    /**
     * The file this project's {@link ArtifactMetadata} is written to, if {@link #getProjectArchive()}
     * exposes Spring Boot configuration metadata. Left unwritten otherwise.
     *
     * @return the metadata output file location, never {@literal null}.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getMetadata();

    @TaskAction
    void generateArtifactMetadata() throws IOException {
        final File archive = getProjectArchive().get().getAsFile();
        final List<ArtifactMetadataResource> candidates = ArtifactMetadataScanner.scan(archive);

        if (candidates.isEmpty()) {
            getLogger().debug("No Spring Boot configuration metadata found for this project's artifact: {}", archive);
            return;
        }

        if (!getProjectArtifact().isPresent()) {
            getLogger().debug("This project has no resolvable groupId/artifactId/version, nothing to generate");
            return;
        }

        final ArtifactoryService service = getService().get();
        final Artifact projectArtifact = getProjectArtifact().get();

        final List<File> classpath = new ArrayList<>(getRuntimeClasspath().getFiles());
        classpath.add(archive);

        final List<PropertyDescriptor> descriptors = service.parsePropertyDescriptors(candidates, classpath);
        final ArtifactMetadata metadata = projectArtifact.toMetadata(descriptors);

        getLogger().debug("Successfully generated artifact metadata for current project: {}:{}:{}",
                metadata.groupId(), metadata.artifactId(), metadata.version());

        service.writeServiceArtifactMetadata(metadata, getMetadata().get().getAsFile());
    }

}
