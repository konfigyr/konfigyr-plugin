package com.konfigyr.gradle;

import com.konfigyr.HttpResponseException;
import com.konfigyr.artifactory.Release;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.AbstractWiremockTest;
import lombok.RequiredArgsConstructor;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.provider.DefaultPropertyFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactUploadActionTest extends AbstractWiremockTest {

    @Mock
    GradleArtifact artifact;

    @Mock
    ArtifactoryService service;

    @Mock
    Release release;

    ArtifactUploadAction action;

    @BeforeEach
    void setup() {
        action = new Action(service, artifact);
    }

    @Test
    @DisplayName("should successfully perform release of an artifact and poll until it is released")
    void releaseAndPollUntilReleased() {
        doReturn(ReleaseState.PENDING, ReleaseState.PENDING, ReleaseState.RELEASED).when(release).state();
        doReturn(release).when(service).upload(artifact);
        doReturn(release).when(service).getRelease(artifact);

        assertThatNoException().isThrownBy(action::execute);

        verify(service).upload(artifact);
        verify(service, times(2)).getRelease(artifact);
    }

    @Test
    @DisplayName("should successfully perform release of an artifact and poll until it is failed")
    void releaseAndPollUntilFailed() {
        doReturn(ReleaseState.PENDING, ReleaseState.PENDING, ReleaseState.FAILED).when(release).state();
        doReturn(release).when(service).upload(artifact);
        doReturn(release).when(service).getRelease(artifact);

        assertThatNoException().isThrownBy(action::execute);

        verify(service).upload(artifact);
        verify(service, times(2)).getRelease(artifact);
    }

    @Test
    @DisplayName("should fail to perform release due to an HTTP response exception")
    void failToRelease() {
        Mockito.doThrow(HttpResponseException.class).when(service).upload(artifact);

        assertThatExceptionOfType(PublishException.class)
                .isThrownBy(action::execute)
                .withMessageContaining("Failed to upload Artifact(%s) to Artifactory".formatted(artifact.coordinates()))
                .withCauseInstanceOf(HttpResponseException.class);

        verify(service).upload(artifact);
        verify(service, never()).getRelease(artifact);
    }

    @Test
    @DisplayName("should fail to perform release due to timeout")
    void timeoutRelease() {
        doReturn(ReleaseState.PENDING).when(release).state();
        doReturn(release).when(service).upload(artifact);
        doReturn(release).when(service).getRelease(artifact);

        assertThatExceptionOfType(PublishException.class)
                .isThrownBy(action::execute)
                .withMessageContaining("Release is still pending for Artifact")
                .withNoCause();

        verify(service).upload(artifact);
        verify(service, atLeast(3)).getRelease(artifact);
    }

    @RequiredArgsConstructor
    private static final class Action extends ArtifactUploadAction {

        private final PropertyFactory propertyFactory = new DefaultPropertyFactory(PropertyHost.NO_OP);
        private final ArtifactoryService artifactoryService;
        private final GradleArtifact artifact;

        @Override
        Property<@NonNull ArtifactoryService> getArtifactoryService() {
            return propertyFactory.property(ArtifactoryService.class).value(artifactoryService);
        }

        @Override
        public Parameters getParameters() {
            return new Parameters() {
                @Override
                public Property<@NonNull GradleArtifact> getArtifact() {
                    return propertyFactory.property(GradleArtifact.class).value(artifact);
                }

                @Override
                public Property<@NonNull Duration> getTimeout() {
                    return propertyFactory.property(Duration.class).value(Duration.ofSeconds(1));
                }

                @Override
                public Property<@NonNull Duration> getInterval() {
                    return propertyFactory.property(Duration.class).value(Duration.ofMillis(200));
                }
            };
        }
    }
}