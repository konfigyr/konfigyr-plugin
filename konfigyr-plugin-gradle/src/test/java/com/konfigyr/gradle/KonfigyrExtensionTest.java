package com.konfigyr.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Verifies {@link KonfigyrExtension}'s {@link RegistrySpec} factories and container wiring, without
 * going through a real Gradle build.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 */
class KonfigyrExtensionTest {

    private KonfigyrExtension extension;

    @BeforeEach
    void setup() {
        final Project project = ProjectBuilder.builder().build();
        extension = new KonfigyrExtension(project, project.getObjects());
    }

    @Test
    @DisplayName("registries container starts out empty")
    void registriesEmptyByDefault() {
        assertThat(extension.getRegistries()).isEmpty();
    }

    @Test
    @DisplayName("konfigyrCentral() registers a registry under the reserved name")
    void konfigyrCentralRegistersReservedName() {
        final RegistrySpec spec = extension.konfigyrCentral();

        assertThat(spec.getName()).isEqualTo(KonfigyrExtension.CENTRAL_REGISTRY_NAME);
        assertThat(extension.getRegistries()).containsExactly(spec);
    }

    @Test
    @DisplayName("konfigyrCentral() is idempotent, returning the same spec on repeat calls")
    void konfigyrCentralIsIdempotent() {
        final RegistrySpec first = extension.konfigyrCentral();
        final RegistrySpec second = extension.konfigyrCentral();

        assertThat(first).isSameAs(second);
        assertThat(extension.getRegistries()).hasSize(1);
    }

    @Test
    @DisplayName("registry(name, action) registers a custom registry under that name")
    void registryRegistersCustomName() {
        final RegistrySpec spec = extension.registry("staging", ignored -> { });

        assertThat(spec.getName()).isEqualTo("staging");
        assertThat(extension.getRegistries()).containsExactly(spec);
    }

    @Test
    @DisplayName("registry() rejects the reserved konfigyrCentral name")
    void registryRejectsReservedName() {
        assertThatExceptionOfType(GradleException.class)
                .isThrownBy(() -> extension.registry(KonfigyrExtension.CENTRAL_REGISTRY_NAME, ignored -> { }))
                .withMessageContaining(KonfigyrExtension.CENTRAL_REGISTRY_NAME);

        assertThat(extension.getRegistries()).isEmpty();
    }

    @Test
    @DisplayName("registries { } configures the same container returned by getRegistries()")
    void registriesBlockConfiguresSameContainer() {
        extension.registries(container -> container.create("staging"));

        assertThat(extension.getRegistries().getByName("staging")).isNotNull();
    }

}
