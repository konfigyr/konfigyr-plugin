package com.konfigyr.gradle;

import com.konfigyr.ArtifactoryClient;
import com.konfigyr.HttpResponseException;
import com.konfigyr.artifactory.*;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.PublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactoryServiceTest {

    static final String REGISTRY = "registry";
    static final Duration TIMEOUT = Duration.ofSeconds(1);
    static final Duration INTERVAL = Duration.ofMillis(200);

    @Mock
    ArtifactoryClient client;

    ArtifactoryService service;

    @BeforeEach
    void setup() {
        service = new Service(Map.of(REGISTRY, client));
    }

    @Test
    @DisplayName("should create service release for collection of release artifact candidates")
    void createServiceRelease() {
        final var release = mock(ServiceRelease.class);
        final var candidate = mock(ServiceReleaseCandidate.class);

        doReturn(release).when(client).release(eq("konfigyr-test-service"), any());

        assertThat(service.release(REGISTRY, "konfigyr-test-service", List.of(candidate)))
                .isSameAs(release);

        verify(client).release("konfigyr-test-service", List.of(candidate));
    }

    @Test
    @DisplayName("should dispatch to the client registered under the given registry name")
    void dispatchesToNamedRegistry() {
        final var other = mock(ArtifactoryClient.class);
        final var multiRegistryService = new Service(Map.of(REGISTRY, client, "other", other));
        final var release = mock(ServiceRelease.class);

        doReturn(release).when(other).release(eq("konfigyr-test-service"), any());

        assertThat(multiRegistryService.release("other", "konfigyr-test-service", List.of()))
                .isSameAs(release);

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("should fail when no client is registered for the given registry name")
    void failsForUnknownRegistry() {
        assertThatExceptionOfType(GradleException.class)
                .isThrownBy(() -> service.release("unknown", "konfigyr-test-service", List.of()))
                .withMessageContaining("unknown");
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

        assertThatNoException().isThrownBy(() -> service.publish(REGISTRY, artifact, TIMEOUT, INTERVAL));

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

        assertThatNoException().isThrownBy(() -> service.publish(REGISTRY, artifact, TIMEOUT, INTERVAL));

        verify(client).publish(artifact);
        verify(client, times(3)).getPublication(artifact);
    }

    @Test
    @DisplayName("should not upload artifact metadata when it is already released")
    void ignoreWhenReleased() {
        final var artifact = mock(ArtifactMetadata.class);

        doReturn(true).when(client).isPublished(artifact);

        assertThatNoException().isThrownBy(() -> service.publish(REGISTRY, artifact, TIMEOUT, INTERVAL));

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
                .isThrownBy(() -> service.publish(REGISTRY, artifact, TIMEOUT, INTERVAL))
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
                .isThrownBy(() -> service.publish(REGISTRY, artifact, TIMEOUT, INTERVAL))
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

        private Service(Map<String, ArtifactoryClient> clients) {
            super(clients);
        }

        @Override
        public Parameters getParameters() {
            throw new UnsupportedOperationException();
        }
    }

}
