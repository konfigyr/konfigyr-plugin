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

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that generates {@link ArtifactMetadata} for each of this project's resolved runtime
 * dependencies that expose Spring Boot configuration metadata, then writes the results to the
 * output directory.
 * <p>
 * This is the dependency-scanning half of the Service Release flow: the {@link ArtifactMetadata}
 * produced here, together with this project's own metadata from
 * {@link GenerateArtifactMetadataTask}, form the full candidate list
 * {@link CreateServiceReleaseTask} submits to open a service's release. This task is only ever
 * needed when the project is configured as a service (a {@code namespace} and {@code service} are
 * set) — otherwise it does not run.
 * <p>
 * Every project in the build is identified through {@link #getProjectArtifacts()}, populated at
 * configuration time so this task never needs to access {@link org.gradle.api.Project} objects
 * during execution, keeping it compatible with the Gradle configuration cache.
 * <p>
 * Output files are named after the artifact coordinates, for example:
 * {@code com.konfigyr-konfigyr-artifactory-1.0.0.json}
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@CacheableTask
public abstract class ResolveServiceDependenciesTask extends DefaultTask {

    /**
     * The name of the task to be registered in the Gradle build.
     */
    static final String NAME = "resolveServiceDependencies";

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
     * Each {@link org.gradle.api.artifacts.result.ResolvedArtifactResult} should be associated with
     * the {@code metadata.json} file containing the serialized {@link com.konfigyr.artifactory.PropertyDescriptor}s.
     *
     * @return the transformed artifact metadata, never {@literal null}.
     */
    @Internal
    public abstract Property<ArtifactCollection> getArtifacts();

    /**
     * The collection of files that represent the runtime classpath of the current project. This is
     * declared purely as a stable, cacheable proxy for {@link #getArtifacts()} — an
     * {@link ArtifactCollection} is not itself a safe cache key.
     *
     * @return the project's runtime classpath, never {@literal null}.
     */
    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

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

    /**
     * The Konfigyr namespace owning the service. Only used to gate whether this task should run at
     * all — not otherwise consumed by the task action.
     *
     * @return the namespace, never {@literal null}.
     */
    @Input
    @Optional
    public abstract Property<String> getNamespace();

    /**
     * The Konfigyr service name. Only used to gate whether this task should run at all — not
     * otherwise consumed by the task action.
     *
     * @return the service name, never {@literal null}.
     */
    @Input
    @Optional
    public abstract Property<String> getServiceName();

    /**
     * The manifest file that should be written that contains all the processed dependency artifacts.
     *
     * @return the output manifest file location, never {@literal null}.
     */
    @OutputFile
    public abstract RegularFileProperty getDependencyManifest();

    /**
     * The output directory where serialized {@link ArtifactMetadata} for each dependency should be
     * written.
     *
     * @return the output dependency artifact directory, never {@literal null}.
     */
    @OutputDirectory
    public abstract DirectoryProperty getDependencyDirectory();

    @TaskAction
    void resolveServiceDependencies() throws IOException {
        final List<String> locations = new ArrayList<>();
        final ArtifactoryService service = getService().get();

        getArtifacts().get().forEach(artifact -> {
            final Artifact candidate = createArtifact(artifact.getId());

            if (candidate == null) {
                return;
            }

            getLogger().debug("Attempting to generate artifact metadata for dependency: {}:{}:{}",
                    candidate.groupId(), candidate.artifactId(), candidate.version());

            final ArtifactMetadata metadata = service.createArtifactMetadata(candidate, artifact.getFile());
            locations.add(service.writeArtifactMetadata(metadata, getDependencyDirectory().get().getAsFile()));
        });

        getLogger().debug("Generating dependency artifact location manifest using: {}", locations);

        Files.writeString(
                getDependencyManifest().get().getAsFile().toPath(),
                String.join("\n", locations)
        );
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
