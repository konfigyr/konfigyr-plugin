package com.konfigyr.gradle;

import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.artifactory.Manifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.Incremental;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jspecify.annotations.NonNull;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.function.Predicate;

/**
 * The Gradle task is responsible for processing and uploading artifacts to Artifactory.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 **/
public abstract class KonfigyrUploadTask extends DefaultTask {

    @Input
    public abstract Property<@NonNull ArtifactoryConfiguration> getConfiguration();

    @Input
    public abstract ListProperty<@NonNull GradleArtifact> getArtifacts();

    @Input
    public abstract Property<@NonNull Duration> getReleaseTimeout();

    @Input
    public abstract Property<@NonNull Duration> getReleasePollingInterval();

    @InputFiles
    @Incremental
    public abstract Property<@NonNull FileCollection> getClasspath();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @ServiceReference(KonfigyrPlugin.PLUGIN_NAME)
    public abstract Property<@NonNull ArtifactoryService> getArtifactoryService();

    @TaskAction
    void upload() throws IOException {
        final Project project = getProject();
        final WorkQueue queue = getWorkerExecutor().noIsolation();
        final ArtifactoryService service = getArtifactoryService().get();

        getLogger().debug("Attempting to download the Artifactory Manifest for Gradle Project {}", project.getName());

        final Manifest manifest = service.getManifest();

        getLogger().debug("Registering artifact for current Gradle Project {}", project.getName());

        final GradleArtifact artifact = GradleArtifact.create(project, getClasspath().get().getAsFileTree());

        if (artifact != null && !manifest.contains(artifact)) {
            queue.submit(ArtifactUploadAction.class, parameters -> {
                parameters.getArtifact().set(artifact);
                parameters.getTimeout().set(getReleaseTimeout());
                parameters.getInterval().set(getReleasePollingInterval());
            });
        }

        getArtifacts()
                .getOrElse(Collections.emptyList())
                .stream()
                .filter(Predicate.not(manifest::contains))
                .forEach(it -> queue.submit(ArtifactUploadAction.class, parameters -> {
                    parameters.getArtifact().set(it);
                    parameters.getTimeout().set(getReleaseTimeout());
                    parameters.getInterval().set(getReleasePollingInterval());
                }));

        // wait until all submitted actions are complete before
        // writing the new manifest to the output file...
        queue.await();

        Files.write(getOutput().get().getAsFile().toPath(), service.generateManifest());
    }

}
