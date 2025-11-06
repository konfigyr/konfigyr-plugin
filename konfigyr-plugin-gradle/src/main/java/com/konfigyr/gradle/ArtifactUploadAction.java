package com.konfigyr.gradle;

import com.konfigyr.artifactory.Release;
import com.konfigyr.artifactory.ReleaseState;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NonNull;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

/**
 * Implementation of {@link WorkAction} that uploads an {@link GradleArtifact} to Artifactory using the
 * registered {@link ArtifactoryService}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public abstract class ArtifactUploadAction implements WorkAction<ArtifactUploadAction.Parameters> {

    private final Logger logger = Logging.getLogger(getClass());

    @ServiceReference(KonfigyrPlugin.PLUGIN_NAME)
    abstract Property<@NonNull ArtifactoryService> getArtifactoryService();

    @Override
    public void execute() {
        final ArtifactoryService service = getArtifactoryService().get();
        final GradleArtifact artifact = getParameters().getArtifact().get();

        Release release;

        try {
            release = service.upload(artifact);
        } catch (Exception ex) {
            throw new PublishException("Failed to upload Artifact(%s) to Artifactory".formatted(artifact.coordinates()), ex);
        }

        final BackOffExecution execution = createBackOffExecution();

        while (release.state() == ReleaseState.PENDING) {
            final long timeout = execution.nextBackOff();

            if (timeout == BackOffExecution.STOP) {
                throw new PublishException("Release is still pending for Artifact(%s) after polling timeout is exceeded"
                        .formatted(artifact.coordinates()));
            }

            logger.info("Release is not yet complete for Artifact({}), polling for status update...", artifact.coordinates());

            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Artifact release state polling interrupted", e);
            }

            release = service.getRelease(artifact);
        }

        if (release.state() == ReleaseState.RELEASED) {
            logger.lifecycle("Release has been successfully processed for Artifact({})", artifact.coordinates());
        } else {
            logger.lifecycle("Could not process release for Artifact({}) with errors: {}",
                    artifact.coordinates(), release.errors());
        }
    }

    private BackOffExecution createBackOffExecution() {
        final ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(getParameters().getInterval().get().toMillis());
        backOff.setMaxElapsedTime(getParameters().getTimeout().get().toMillis());
        backOff.setMultiplier(1.75);
        backOff.setMaxAttempts(Integer.MAX_VALUE);
        return backOff.start();
    }

    interface Parameters extends WorkParameters {

        Property<@NonNull GradleArtifact> getArtifact();

        Property<@NonNull Duration> getTimeout();

        Property<@NonNull Duration> getInterval();

    }
}
