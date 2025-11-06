package com.konfigyr.gradle;

import com.konfigyr.artifactory.Artifact;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.file.FileTree;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serial;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * Implementation of an {@link Artifact} that is derived from the Gradle {@link ModuleComponentIdentifier}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 **/
public record GradleArtifact(
        String groupId,
        String artifactId,
        String version,
        String name,
        String description,
        Set<File> metadata
) implements Artifact, Iterable<File> {

    @Serial
    private static final long serialVersionUID = -2122654973223976222L;

    /**
     * Creates an {@link GradleArtifact} from the current Gradle {@link Project} and {@link FileTree classpath}.
     *
     * @param project the Gradle project, cannot be {@literal null}.
     * @param files the project classpath, can be {@literal null}.
     * @return the created {@link GradleArtifact}, never {@literal null}.
     */
    @Nullable
    static GradleArtifact create(@NonNull Project project, @Nullable FileTree files) {
        final Set<File> metadata = extractConfigurationMetadata(files);

        if (metadata.isEmpty()) {
            return null;
        }

        return new GradleArtifact(
                String.valueOf(project.getGroup()),
                project.getName(),
                String.valueOf(project.getVersion()),
                project.getName(),
                project.getDescription(),
                metadata
        );
    }

    /**
     * Creates an {@link GradleArtifact} from the {@link ModuleComponentIdentifier Project dependency identifier}
     * and it's {@link FileTree classpath}.
     *
     * @param identifier the project dependency identifier, cannot be {@literal null}.
     * @param files the dependency classpath, can be {@literal null}.
     * @return the created {@link GradleArtifact}, never {@literal null}.
     */
    @Nullable
    static GradleArtifact create(@NonNull ModuleComponentIdentifier identifier, @Nullable FileTree files) {
        final Set<File> metadata = extractConfigurationMetadata(files);

        if (metadata.isEmpty()) {
            return null;
        }

        return new GradleArtifact(
                identifier.getGroup(),
                identifier.getModule(),
                identifier.getVersion(),
                null,
                null,
                metadata
        );
    }

    @NonNull
    public String coordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Nullable
    @Override
    public URI website() {
        return null;
    }

    @Nullable
    @Override
    public URI repository() {
        return null;
    }

    @NonNull
    @Override
    public Iterator<File> iterator() {
        return metadata.iterator();
    }

    @NonNull
    private static Set<File> extractConfigurationMetadata(@Nullable FileTree files) {
        if (files == null) {
            return Collections.emptySet();
        }

        return files.matching(spec -> spec.include(
                "**/spring-configuration-metadata.json",
                "**/additional-spring-configuration-metadata.json"
        )).getFiles();
    }
}
