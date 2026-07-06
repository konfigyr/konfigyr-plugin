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
 * @since 1.0.0
 */
public abstract class ServiceReleaseArtifactUploadAction implements WorkAction<ServiceReleaseArtifactUploadAction.Parameters> {

    @ServiceReference(KonfigyrPlugin.PLUGIN_NAME)
    abstract Property<@NonNull ArtifactoryService> getArtifactoryService();

    @Override
    public void execute() {
        final ArtifactoryService service = getArtifactoryService().get();
        final String namespace = getParameters().getNamespace().get();
        final String serviceName = getParameters().getServiceName().get();
        final ServiceRelease release = getParameters().getRelease().get();
        final ArtifactMetadata artifact = getParameters().getArtifact().get();

        service.upload(namespace, serviceName, release, artifact);
    }

    interface Parameters extends WorkParameters {

        Property<@NonNull String> getNamespace();

        Property<@NonNull String> getServiceName();

        Property<@NonNull ServiceRelease> getRelease();

        Property<@NonNull ArtifactMetadata> getArtifact();

    }
}
