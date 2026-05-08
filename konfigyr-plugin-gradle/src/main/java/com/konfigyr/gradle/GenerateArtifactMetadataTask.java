package com.konfigyr.gradle;

import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
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
 * Task that would generate the {@link ArtifactMetadata} for each resolved {@link Project}
 * dependency and the {@link Project} itself and write them to output directory.
 * <p>
 * The resolved artifact metadata locations are added to the manifest file written to the
 * output directory. This manifest file is later used to publish only the affected artifact
 * metadata to the Konfigyr Artifactory.
 * <p>
 * The output directory should contain the artifact metadata files that are later uploaded
 * to the Konfigyr Artifactory. The files are named after the artifact coordinates, for example:
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

        return createArtifact(getProject()).toMetadata(getService().get().parsePropertyDescriptors(
                candidates, getClasspath()
        ));
    }

    @Nullable
    private Artifact createArtifact(Object identifier) {
        if (identifier instanceof ModuleComponentIdentifier module) {
            return Artifact.of(module.getGroup(), module.getModule(), module.getVersion());
        } else if (identifier instanceof ProjectComponentIdentifier module) {
            return createArtifact(getProject().project(module.getProjectPath()));
        } else if (identifier instanceof TransformedComponentFileArtifactIdentifier module) {
            return createArtifact(module.getComponentIdentifier());
        } else {
            return null;
        }
    }

    private static Artifact createArtifact(Project project) {
        return Artifact.builder()
                .groupId(String.valueOf(project.getGroup()))
                .artifactId(project.getName())
                .version(String.valueOf(project.getVersion()))
                .name(project.getDisplayName())
                .description(project.getDescription())
                .build();
    }

}
