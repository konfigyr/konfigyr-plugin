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

    static final Duration TIMEOUT = Duration.ofSeconds(1);
    static final Duration INTERVAL = Duration.ofMillis(200);

    @Mock
    ArtifactoryClient client;

    ArtifactoryService service;

    @BeforeEach
    void setup() {
        service = new Service(client);
    }

    @Test
    @DisplayName("should create service release for collection of release artifact candidates")
    void createServiceRelease() {
        final var release = mock(ServiceRelease.class);
        final var candidate = mock(ServiceReleaseCandidate.class);

        doReturn(release).when(client).release(eq("konfigyr"), eq("konfigyr-test-service"), any());

        assertThat(service.release("konfigyr", "konfigyr-test-service", List.of(candidate)))
                .isSameAs(release);

        verify(client).release("konfigyr", "konfigyr-test-service", List.of(candidate));
    }

    @Test
    @DisplayName("should successfully upload artifact metadata and poll until it is released")
    void releaseAndPollUntilReleased() {
        final var artifact = mock(ArtifactMetadata.class);
        final var publication = mock(Publication.class);

        doReturn(PublicationState.PENDING, PublicationState.PENDING, PublicationState.PUBLISHED)
                .when(publication).state();
        doReturn(publication).when(client).publish(artifact);
        doReturn(publication).when(client).getPublication(artifact);

        assertThatNoException().isThrownBy(() -> service.publish(artifact, TIMEOUT, INTERVAL));

        verify(client).publish(artifact);
        verify(client, times(2)).getPublication(artifact);
    }

    @Test
    @DisplayName("should successfully upload artifact metadata and poll until it is failed")
    void releaseAndPollUntilFailed() {
        final var artifact = mock(ArtifactMetadata.class);
        final var publication = mock(Publication.class);

        doReturn(PublicationState.PENDING, PublicationState.PENDING, PublicationState.PENDING, PublicationState.FAILED)
                .when(publication).state();
        doReturn(publication).when(client).publish(artifact);
        doReturn(publication).when(client).getPublication(artifact);

        assertThatNoException().isThrownBy(() -> service.publish(artifact, TIMEOUT, INTERVAL));

        verify(client).publish(artifact);
        verify(client, times(3)).getPublication(artifact);
    }

    @Test
    @DisplayName("should not upload artifact metadata when it is already released")
    void ignoreWhenReleased() {
        final var artifact = mock(ArtifactMetadata.class);

        doReturn(true).when(client).isPublished(artifact);

        assertThatNoException().isThrownBy(() -> service.publish(artifact, TIMEOUT, INTERVAL));

        verify(client).isPublished(artifact);
        verify(client, never()).publish(artifact);
        verify(client, never()).getPublication(artifact);
    }

    @Test
    @DisplayName("should fail to upload artifact metadata due to an HTTP response exception")
    void failToRelease() {
        final var artifact = mock(ArtifactMetadata.class);

        Mockito.doThrow(HttpResponseException.class).when(client).publish(artifact);

        assertThatExceptionOfType(PublishException.class)
                .isThrownBy(() -> service.publish(artifact, TIMEOUT, INTERVAL))
                .withCauseInstanceOf(HttpResponseException.class);

        verify(client).publish(artifact);
        verify(client, never()).getPublication(artifact);
    }

    @Test
    @DisplayName("should fail to upload artifact metadata due to poll timeout exceeded")
    void timeoutRelease() {
        final var artifact = mock(ArtifactMetadata.class);
        final var publication = mock(Publication.class);

        doReturn(PublicationState.PENDING).when(publication).state();
        doReturn(publication).when(client).publish(artifact);
        doReturn(publication).when(client).getPublication(artifact);

        assertThatExceptionOfType(PublishException.class)
                .isThrownBy(() -> service.publish(artifact, TIMEOUT, INTERVAL))
                .withMessageContaining("Publication is still pending for Artifact")
                .withNoCause();

        verify(client).publish(artifact);
        verify(client, atLeast(3)).getPublication(artifact);
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
            };
        }
    }

}