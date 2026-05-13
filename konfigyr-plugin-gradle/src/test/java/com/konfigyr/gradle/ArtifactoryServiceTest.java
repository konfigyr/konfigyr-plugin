package com.konfigyr.gradle;

import com.konfigyr.ArtifactoryClient;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.HttpResponseException;
import com.konfigyr.artifactory.*;
import com.konfigyr.test.AbstractWiremockTest;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactoryServiceTest extends AbstractWiremockTest {

    @Mock
    ArtifactoryClient client;

    ArtifactoryService service;

    @BeforeEach
    void setup() {
        service = new Service(client);
    }

    @Test
    @DisplayName("should resolve the Manifest for the current project")
    void resolveManifest() {
        final var manifest = mock(Manifest.class);
        doReturn(manifest).when(client).getManifest();

        assertThat(service.getManifest())
                .isEqualTo(manifest);

        verify(client).getManifest();
    }

    @Test
    @DisplayName("should publish the Manifest for collection of artifacts")
    void publishManifest() {
        final var manifest = mock(Manifest.class);
        final var artifact = mock(Artifact.class);

        doReturn(manifest).when(client).publish(any());

        assertThatNoException().isThrownBy(() -> service.publish(List.of(artifact)));

        verify(client).publish(List.of(artifact));
    }

    @Test
    @DisplayName("should retrieve the release for the artifact")
    void resolveRelease() {
        final var artifact = mock(Artifact.class);
        final var release = mock(Release.class);

        doReturn(release).when(client).getRelease(artifact);

        assertThat(service.getRelease(artifact))
                .isEqualTo(release);

        verify(client).getRelease(artifact);
    }

    @Test
    @DisplayName("should successfully upload artifact metadata and poll until it is released")
    void releaseAndPollUntilReleased() {
        final var artifact = mock(ArtifactMetadata.class);
        final var release = mock(Release.class);

        doReturn(ReleaseState.PENDING, ReleaseState.PENDING, ReleaseState.RELEASED).when(release).state();
        doReturn(release).when(client).upload(artifact);
        doReturn(release).when(client).getRelease(artifact);

        assertThatNoException().isThrownBy(() -> service.upload(artifact));

        verify(client).upload(artifact);
        verify(client, times(2)).getRelease(artifact);
    }

    @Test
    @DisplayName("should successfully upload artifact metadata and poll until it is failed")
    void releaseAndPollUntilFailed() {
        final var artifact = mock(ArtifactMetadata.class);
        final var release = mock(Release.class);

        doReturn(ReleaseState.PENDING, ReleaseState.PENDING, ReleaseState.PENDING, ReleaseState.FAILED).when(release).state();
        doReturn(release).when(client).upload(artifact);
        doReturn(release).when(client).getRelease(artifact);

        assertThatNoException().isThrownBy(() -> service.upload(artifact));

        verify(client).upload(artifact);
        verify(client, times(3)).getRelease(artifact);
    }

    @Test
    @DisplayName("should not upload artifact metadata when it is already released")
    void ignoreWhenReleased() {
        final var artifact = mock(ArtifactMetadata.class);

        doReturn(true).when(client).isReleased(artifact);

        assertThatNoException().isThrownBy(() -> service.upload(artifact));

        verify(client).isReleased(artifact);
        verify(client, never()).upload(artifact);
        verify(client, never()).getRelease(artifact);
    }

    @Test
    @DisplayName("should fail to upload artifact metadata due to an HTTP response exception")
    void failToRelease() {
        final var artifact = mock(ArtifactMetadata.class);

        Mockito.doThrow(HttpResponseException.class).when(client).upload(artifact);

        assertThatExceptionOfType(PublishException.class)
                .isThrownBy(() -> service.upload(artifact))
                .withCauseInstanceOf(HttpResponseException.class);

        verify(client).upload(artifact);
        verify(client, never()).getRelease(artifact);
    }

    @Test
    @DisplayName("should fail to upload artifact metadata due to poll timeout exceeded")
    void timeoutRelease() {
        final var artifact = mock(ArtifactMetadata.class);
        final var release = mock(Release.class);

        doReturn(ReleaseState.PENDING).when(release).state();
        doReturn(release).when(client).upload(artifact);
        doReturn(release).when(client).getRelease(artifact);

        assertThatExceptionOfType(PublishException.class)
                .isThrownBy(() -> service.upload(artifact))
                .withMessageContaining("Release is still pending for Artifact")
                .withNoCause();

        verify(client).upload(artifact);
        verify(client, atLeast(3)).getRelease(artifact);
    }

    @Test
    @DisplayName("backoff execution should stop when max number of attempts is made")
    void stopBackoffExecutionWhenMaxAttemptsReached() {
        final var backoff = new ArtifactoryService.BackOffExecution(100, Duration.ofMinutes(5).toMillis());

        for (int i = 0; i < 60; i++) {
            assertThat(backoff.nextBackOff())
                    .as("the next backoff interval must be fixed and equal to 100ms")
                    .isEqualTo(100);
        }

        assertThat(backoff.nextBackOff())
                .as("the next backoff interval must be STOP")
                .isEqualTo(ArtifactoryService.BackOffExecution.STOP);

    }

    @Test
    @DisplayName("backoff execution should stop when timeout period is reached")
    void stopBackoffExecutionWhenTimeoutReached() {
        final var backoff = new ArtifactoryService.BackOffExecution(100, 500);

        for (int i = 0; i < 5; i++) {
            assertThat(backoff.nextBackOff())
                    .as("the next backoff interval must be fixed and equal to 100ms")
                    .isEqualTo(100);
        }

        assertThat(backoff.nextBackOff())
                .as("the next backoff interval must be STOP")
                .isEqualTo(ArtifactoryService.BackOffExecution.STOP);

    }

    private static final class Service extends ArtifactoryService {

        private static final ObjectFactory OBJECTS = ProjectBuilder.builder().build().getObjects();

        private Service(ArtifactoryClient client) {
            super(client);
        }

        @Override
        public Parameters getParameters() {
            return new Parameters() {
                @Override
                public Property<@NotNull ArtifactoryConfiguration> getConfiguration() {
                    return OBJECTS.property(ArtifactoryConfiguration.class);
                }

                @Override
                public Property<@NonNull Duration> getTimeout() {
                    return OBJECTS.property(Duration.class).value(Duration.ofSeconds(1));
                }

                @Override
                public Property<@NonNull Duration> getInterval() {
                    return OBJECTS.property(Duration.class).value(Duration.ofMillis(200));
                }
            };
        }
    }

}