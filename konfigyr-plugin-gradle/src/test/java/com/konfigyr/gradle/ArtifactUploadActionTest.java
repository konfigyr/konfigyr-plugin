package com.konfigyr.gradle;

import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.test.AbstractWiremockTest;
import lombok.RequiredArgsConstructor;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArtifactUploadActionTest extends AbstractWiremockTest {

    @Mock
    ArtifactMetadata artifact;

    @Mock
    ArtifactoryService service;

    ArtifactUploadAction action;

    @BeforeEach
    void setup() {
        action = new Action(service, artifact);
    }

    @Test
    @DisplayName("should successfully perform a release of artifact metadata")
    void releaseArtifact() {
        assertThatNoException().isThrownBy(action::execute);

        verify(service).upload(artifact);
    }

    @Test
    @DisplayName("should fail to perform a release of artifact metadata")
    void releaseArtifactFailure() {
        doThrow(PublishException.class).when(service).upload(artifact);

        assertThatExceptionOfType(PublishException.class)
                .isThrownBy(action::execute);

        verify(service).upload(artifact);
    }

    @RequiredArgsConstructor
    private static final class Action extends ArtifactUploadAction {

        private static final ObjectFactory OBJECTS = ProjectBuilder.builder().build().getObjects();

        private final ArtifactoryService service;
        private final ArtifactMetadata artifact;

        @Override
        Property<@NonNull ArtifactoryService> getArtifactoryService() {
            return OBJECTS.property(ArtifactoryService.class).value(service);
        }

        @Override
        public Parameters getParameters() {
            return () -> OBJECTS.property(ArtifactMetadata.class).value(artifact);
        }
    }
}