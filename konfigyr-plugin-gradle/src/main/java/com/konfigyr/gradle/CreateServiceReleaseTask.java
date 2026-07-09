package com.konfigyr.gradle;

import com.konfigyr.artifactory.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jspecify.annotations.NonNull;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that opens (or takes over) this service's current {@link ServiceRelease}, uploads metadata
 * for every candidate artifact the server reports as {@link ArtifactUploadStatus#UPLOAD_REQUIRED},
 * then completes the release so it becomes the service's current {@link Manifest}.
 * <p>
 * The candidate list submitted to the release is this project's own artifact metadata (from
 * {@link GenerateArtifactMetadataTask}, if it produces any) together with every dependency scanned
 * by {@link ResolveServiceDependenciesTask}. This task only runs when the project is configured as
 * a service (a {@code namespace} and {@code service} are set), see the {@code onlyIf} predicate
 * this task is registered with in {@link KonfigyrPlugin}.
 *
 * @author Vladimir Spasic
 * @since 1.1.0
 */
@DisableCachingByDefault(because = "Performs a network call against a stateful remote service")
public abstract class CreateServiceReleaseTask extends DefaultTask {

    /**
     * The name of the task to be registered in the Gradle build.
     */
    static final String NAME = "createServiceRelease";

    /**
     * Returns the {@link ArtifactoryService} to use for creating the service release.
     *
     * @return the artifactory service to use, never {@literal null}.
     */
    @Internal
    public abstract Property<ArtifactoryService> getService();

    /**
     * The {@link WorkerExecutor} to use for executing the {@link ServiceReleaseArtifactUploadAction}
     * for every artifact the release reports as requiring an upload.
     *
     * @return the worker task executor, never {@literal null}.
     */
    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    /**
     * The file containing this project's own serialized {@link ArtifactMetadata}, written by
     * {@link GenerateArtifactMetadataTask}. The file itself may not exist if this project's own jar
     * does not expose Spring Boot configuration metadata, in which case it is not part of the
     * candidate list.
     * <p>
     * It is declared as {@link Internal} rather than {@code @InputFile}, since Gradle's input
     * validation requires an {@code @InputFile} to exist whenever its property has a value, even if
     * marked {@code @Optional}; this task is already {@link DisableCachingByDefault} and never
     * eligible for up-to-date checks, so nothing is lost by not tracking it as a cacheable input.
     *
     * @return the artifact metadata file location, never {@literal null}.
     */
    @Internal
    public abstract RegularFileProperty getServiceArtifactMetadata();

    /**
     * The manifest file containing the dependency artifact metadata file locations, written by
     * {@link ResolveServiceDependenciesTask}.
     *
     * @return the dependency artifact manifest file location, never {@literal null}.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getDependencyManifest();

    /**
     * The directory where serialized dependency {@link ArtifactMetadata} is located, written by
     * {@link ResolveServiceDependenciesTask}.
     *
     * @return the dependency metadata directory, never {@literal null}.
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDependencyDirectory();

    /**
     * The Konfigyr namespace owning the service.
     *
     * @return the namespace, never {@literal null}.
     */
    @Input
    @Optional
    public abstract Property<@NonNull String> getNamespace();

    /**
     * The Konfigyr service name.
     *
     * @return the service name, never {@literal null}.
     */
    @Input
    @Optional
    public abstract Property<@NonNull String> getServiceName();

    @TaskAction
    void createServiceRelease() throws IOException {
        final String namespace = getNamespace().get();
        final String serviceName = getServiceName().get();
        final ArtifactoryService service = getService().get();

        final List<ArtifactMetadata> metadata = collectArtifactMetadataForRelease(service);

        final ServiceRelease release = service.release(namespace, serviceName,
                metadata.stream().map(ServiceReleaseCandidate::of).toList());

        final WorkQueue queue = getWorkerExecutor().noIsolation();

        for (ServiceReleaseEntry entry : release.artifacts()) {
            if (entry.status() != ArtifactUploadStatus.UPLOAD_REQUIRED) {
                continue;
            }

            final ArtifactMetadata artifact = metadata.stream()
                    .filter(candidate -> matches(entry, candidate))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No artifact metadata found for required upload: %s:%s:%s".formatted(
                                    entry.groupId(), entry.artifactId(), entry.version())));

            queue.submit(ServiceReleaseArtifactUploadAction.class, parameters -> {
                parameters.getNamespace().set(namespace);
                parameters.getServiceName().set(serviceName);
                parameters.getRelease().set(release);
                parameters.getArtifact().set(artifact);
            });
        }

        queue.await();

        service.complete(namespace, serviceName, release);
    }

    private List<ArtifactMetadata> collectArtifactMetadataForRelease(ArtifactoryService service) throws IOException {
        final List<ArtifactMetadata> candidates = new ArrayList<>();
        final File serviceArtifactMetadata = getServiceArtifactMetadata().get().getAsFile();

        if (serviceArtifactMetadata.exists()) {
            candidates.add(service.readArtifactMetadata(serviceArtifactMetadata));
        }

        candidates.addAll(service.readArtifactMetadata(
                getDependencyManifest().get().getAsFile(),
                getDependencyDirectory().get().getAsFile()
        ));

        return candidates;
    }

    private static boolean matches(Artifact a, Artifact b) {
        return a.groupId().equals(b.groupId())
                && a.artifactId().equals(b.artifactId())
                && a.version().equals(b.version());
    }

}
