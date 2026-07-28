package com.konfigyr.gradle;

import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.ServiceRelease;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NonNull;

/**
 * Implementation of {@link WorkAction} that uploads the {@link ArtifactMetadata} for a single artifact
 * required by a {@link ServiceRelease} using the registered {@link ArtifactoryService}.
 *
 * @author Vladimir Spasic
 * @since 1.1.0
 */
public abstract class ServiceReleaseArtifactUploadAction implements WorkAction<ServiceReleaseArtifactUploadAction.Parameters> {

    /**
     * The shared {@link ArtifactoryService} this work item uploads through, injected by name rather
     * than passed via {@link Parameters} since a {@code BuildService} reference cannot be serialized
     * into work item parameters.
     *
     * @return the artifactory service to use, never {@literal null}.
     */
    @ServiceReference(KonfigyrPlugin.PLUGIN_NAME)
    abstract Property<@NonNull ArtifactoryService> getArtifactoryService();

    @Override
    public void execute() {
        final ArtifactoryService service = getArtifactoryService().get();
        final String registryName = getParameters().getRegistryName().get();
        final String serviceName = getParameters().getServiceName().get();
        final ServiceRelease release = getParameters().getRelease().get();
        final ArtifactMetadata artifact = getParameters().getArtifact().get();

        service.upload(registryName, serviceName, release, artifact);
    }

    /**
     * Work item parameters for a single {@link #execute()} call, one artifact upload per item,
     * submitted by {@link CreateServiceReleaseTask}.
     */
    interface Parameters extends WorkParameters {

        /**
         * The name of the registry the release was opened against.
         *
         * @return the registry name, never {@literal null}.
         */
        Property<@NonNull String> getRegistryName();

        /**
         * The name of the service this release belongs to.
         *
         * @return the service name, never {@literal null}.
         */
        Property<@NonNull String> getServiceName();

        /**
         * The service release this upload contributes to.
         *
         * @return the service release, never {@literal null}.
         */
        Property<@NonNull ServiceRelease> getRelease();

        /**
         * The artifact metadata payload to upload.
         *
         * @return the artifact metadata, never {@literal null}.
         */
        Property<@NonNull ArtifactMetadata> getArtifact();

    }
}
