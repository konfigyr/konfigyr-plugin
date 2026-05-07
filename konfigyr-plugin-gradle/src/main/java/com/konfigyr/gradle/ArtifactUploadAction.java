package com.konfigyr.gradle;

import com.konfigyr.artifactory.ArtifactMetadata;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NonNull;

/**
 * Implementation of {@link WorkAction} that uploads an {@link ArtifactMetadata} to Artifactory using the
 * registered {@link ArtifactoryService}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public abstract class ArtifactUploadAction implements WorkAction<ArtifactUploadAction.Parameters> {

    @ServiceReference(KonfigyrPlugin.PLUGIN_NAME)
    abstract Property<@NonNull ArtifactoryService> getArtifactoryService();

    @Override
    public void execute() {
        final ArtifactoryService service = getArtifactoryService().get();
        final ArtifactMetadata artifact = getParameters().getArtifact().get();

        service.upload(artifact);
    }

    interface Parameters extends WorkParameters {

        Property<@NonNull ArtifactMetadata> getArtifact();

    }
}
