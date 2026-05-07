package com.konfigyr.gradle;

import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.Manifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jspecify.annotations.NonNull;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Task that would extract the {@link ArtifactMetadata} from the metadata directory and publish
 * them to the Konfigyr Artifactory.
 * <p>
 * Artifact metadata is being published to the Konfigyr Artifactory using the {@link ArtifactUploadAction}
 * where each metadata file is uploaded individually and the number of concurrent uploads is managed
 * by the {@link WorkerExecutor}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@DisableCachingByDefault(because = "The task should always publish the latest artifact manifest")
public abstract class PublishArtifactMetadataTask extends DefaultTask {

    /**
     * The name of the task to be registered in the Gradle build.
     */
    static final String NAME = "publishArtifactMetadata";

    /**
     * Returns the {@link ArtifactoryService} to use for publishing the artifact metadata.
     *
     * @return the artifactory service to use, never {@literal null}.
     */
    @Internal
    public abstract Property<ArtifactoryService> getService();

    /**
     * The {@link WorkerExecutor} to use for executing the {@link ArtifactUploadAction} task.
     *
     * @return the worker task executor, never {@literal null}.
     */
    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    /**
     * The file containing the artifact metadata file locations used to generate the new {@link Manifest}.
     *
     * @return the artifact manifest file location, never {@literal null}.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getArtifactManifest();

    /**
     * The directory where serialized {@link ArtifactMetadata} is located.
     *
     * @return the metadata directory, never {@literal null}.
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getMetadataDirectory();

    /**
     * The maximum time in milliseconds to wait for a successful poll of a release
     *
     * @return the release timeout, never {@literal null}.
     * @see com.konfigyr.gradle.KonfigyrExtension#getReleasePollTimeout()
     */
    @Input
    public abstract Property<@NonNull Duration> getReleaseTimeout();

    /**
     * The initial time interval in milliseconds between consecutive polling attempts to check for a release.
     *
     * @return the release polling interval, never {@literal null}.
     * @see com.konfigyr.gradle.KonfigyrExtension#getReleasePollInterval()
     */
    @Input
    public abstract Property<@NonNull Duration> getReleasePollingInterval();

    @TaskAction
    void publishArtifactMetadata() throws IOException {
        final Project project = getProject();
        final ArtifactoryService service = getService().get();
        final WorkQueue queue = getWorkerExecutor().noIsolation();

        getLogger().debug("Attempting to download the Service Manifest for Gradle project {}", project.getName());

        // Retrieve the current service manifest that would be used to check if
        // an artifact metadata should be published to the artifactory or not
        final Manifest manifest = service.getManifest();

        // Extract the artifact metadata from the metadata directory collecting only the
        // artifacts that are registered in the manifest file...
        final List<ArtifactMetadata> artifacts = service.readArtifactMetadata(
                getArtifactManifest().get().getAsFile(),
                getMetadataDirectory().get().getAsFile()
        );

        // Publish the new artifact list to Konfigyr that would generate a new service manifest.
        // This must be executed before the artifact is published to the artifactory to prepare
        // the service configuration catalog for a new batch of artifact metadata changes...
        service.publish(artifacts);

        for (ArtifactMetadata candidate : artifacts) {
            if (manifest.contains(candidate)) {
                getLogger().info("Skipping Artifact({}:{}:{}) as it is already registered in the Service Manifest",
                        candidate.groupId(), candidate.artifactId(), candidate.version());
            } else {
                queue.submit(ArtifactUploadAction.class, parameters -> parameters.getArtifact().set(candidate));
            }
        }

        // wait until all submitted actions are complete to finalize the task action...
        queue.await();
    }

}
