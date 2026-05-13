package com.konfigyr.gradle;

import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that generates {@link ArtifactMetadata} for each resolved dependency and for the
 * current project itself, then writes the results to the output directory.
 * <p>
 * The current project and all other projects in the build are identified through
 * {@link #getProjectPath()} and {@link #getProjectArtifacts()}. Both properties are populated
 * at configuration time, avoiding any access to {@link org.gradle.api.Project} objects during
 * task execution and keeping the task compatible with the Gradle configuration cache.
 * <p>
 * The resolved artifact metadata locations are recorded in the manifest file. This manifest
 * is later consumed by {@link PublishArtifactMetadataTask} to upload only the affected
 * entries to the Konfigyr Artifactory.
 * <p>
 * Output files are named after the artifact coordinates, for example:
 * {@code com.konfigyr-konfigyr-artifactory-1.0.0.json}
 *
 * @author Vladimir Spasic
 * @since 1.0.0
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
     * Collection that contains transformed artifact metadata from the {@link ArtifactMetadataTransform}.
     * <p>
     * Each {@link org.gradle.api.artifacts.result.ResolvedArtifactResult} should be assocated with
     * the {@code metadata.json} file containing the serialized {@link com.konfigyr.artifactory.PropertyDescriptor}s.
     *
     * @return the transformed artifact metadata, never {@literal null}.
     */
    @Internal
    public abstract Property<ArtifactCollection> getArtifacts();

    /**
     * The collection of files that represent the classpath of the current project. This classpath file
     * collection is used to create a custom {@link ClassLoader} that would generate the {@link ArtifactMetadata}
     * for the current Gradle project.
     *
     * @return the project's classpath, never {@literal null}.
     */
    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /**
     * The collection of files that represent the classpath of the current project. This classpath file
     * collection is used to create a custom {@link ClassLoader} that would generate the {@link ArtifactMetadata}
     * for the current Gradle project.
     *
     * @return the project's classpath, never {@literal null}.
     */
    @InputFiles
    @CompileClasspath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The manifest file that should be written that contains all the processed artifacts.
     *
     * @return the output manifest file location, never {@literal null}.
     */
    @OutputFile
    public abstract RegularFileProperty getManifest();

    /**
     * The output directory where serialized {@link ArtifactMetadata} should be written.
     *
     * @return the output directory, never {@literal null}.
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    /**
     * The Gradle path of the project this task belongs to (e.g. {@code :orders}).
     * <p>
     * Used as the lookup key into {@link #getProjectArtifacts()} to identify the current
     * project when generating its {@link ArtifactMetadata}.
     *
     * @return the project path, never {@literal null}.
     */
    @Input
    public abstract Property<String> getProjectPath();

    /**
     * A map of every project in the build, keyed by Gradle project path, with each value
     * being a pre-built {@link Artifact} descriptor.
     * <p>
     * The map is populated at configuration time so the task action never needs to access
     * live {@link org.gradle.api.Project} objects, keeping this task compatible with the
     * Gradle configuration cache.
     *
     * @return the project artifacts map, never {@literal null}.
     */
    @Input
    public abstract MapProperty<String, Artifact> getProjectArtifacts();

    @TaskAction
    void generateArtifactMetadata() throws IOException {
        final List<String> locations = new ArrayList<>();
        final ArtifactoryService service = getService().get();
        final ArtifactMetadata projectArtifact = createArtifactMetadataForCurrentProject();

        if (projectArtifact != null) {
            getLogger().debug("Attempting to generate artifact metadata for current project: {}:{}:{}",
                    projectArtifact.groupId(), projectArtifact.artifactId(), projectArtifact.version());

            locations.add(service.writeArtifactMetadata(projectArtifact, getOutput().get().getAsFile()));
        }

        getArtifacts().get().forEach(artifact -> {
            final Artifact candidate = createArtifact(artifact.getId());

            if (candidate == null) {
                return;
            }

            getLogger().debug("Attempting to generate artifact metadata for dependency: {}:{}:{}",
                    candidate.groupId(), candidate.artifactId(), candidate.version());

            // load the property descriptor metadata from the transformed artifact and
            // create the artifact metadata that should be later uploaded to the artifactory
            final ArtifactMetadata metadata = service.createArtifactMetadata(candidate, artifact.getFile());
            locations.add(service.writeArtifactMetadata(metadata, getOutput().get().getAsFile()));
        });

        getLogger().debug("Generating artifact location manifest using: {}", locations);

        // store the artifact metadata locations in the manifest file
        Files.writeString(
                getManifest().get().getAsFile().toPath(),
                String.join("\n", locations)
        );
    }

    @Nullable
    private ArtifactMetadata createArtifactMetadataForCurrentProject() {
        final List<Resource> candidates = new ArrayList<>();

        getClasspath().getAsFileTree().matching(spec -> spec.include(
                ArtifactMetadataTransform.METADATA_PATHS
        )).forEach(file -> candidates.add(new FileSystemResource(file)));

        if (candidates.isEmpty()) {
            return null;
        }

        final Artifact artifact = getProjectArtifacts().get().get(getProjectPath().get());

        if (artifact == null) {
            return null;
        }

        return artifact.toMetadata(getService().get().parsePropertyDescriptors(
                candidates, getClasspath()
        ));
    }

    @Nullable
    private Artifact createArtifact(Object identifier) {
        if (identifier instanceof ModuleComponentIdentifier module) {
            return Artifact.of(module.getGroup(), module.getModule(), module.getVersion());
        } else if (identifier instanceof ProjectComponentIdentifier module) {
            return getProjectArtifacts().get().get(module.getProjectPath());
        } else if (identifier instanceof TransformedComponentFileArtifactIdentifier module) {
            return createArtifact(module.getComponentIdentifier());
        } else {
            return null;
        }
    }

}
