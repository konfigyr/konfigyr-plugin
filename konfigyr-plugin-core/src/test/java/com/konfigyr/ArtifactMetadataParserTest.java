package com.konfigyr;

import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.test.PropertyDescriptorAssert;
import com.konfigyr.test.ResourceUtils;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.io.FileNotFoundException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class ArtifactMetadataParserTest {

    ArtifactMetadataParser resolver;
    Artifact artifact = Artifact.of("com.konfigyr", "konfigyr-crypto-api", "1.0.0");

    @BeforeEach
    void setup() {
        resolver = new ArtifactMetadataParser(ClassLoader.getSystemClassLoader());
    }

    @Test
    @DisplayName("should parse Spring Boot configuration metadata and create an Artifact Metadata for upload")
    void shouldParseMetadata() throws Exception {
        final ArtifactMetadata metadata = resolver.parse(
                artifact,
                ResourceUtils.loadResources(
                        "additional-spring-configuration-metadata.json",
                        "spring-configuration-metadata.json"

                )
        );

        assertThatObject(metadata)
                .returns(artifact.groupId(), Artifact::groupId)
                .returns(artifact.artifactId(), Artifact::artifactId)
                .returns(artifact.version(), Artifact::version);

        System.out.println(metadata.properties());

        assertThat(metadata.properties())
                .isNotNull()
                .isNotEmpty()
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.enabled")
                                .typeName(Boolean.class)
                                .schema("{\"type\":\"boolean\"}")
                                .hasNoDefaultValue()
                                .hasNoDescription()
                                .hasDeprecation("No longer needed", null),
                        Index.atIndex(0)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.enumeration")
                                .typeName(TestEnumeration.class)
                                .schema("{\"type\":\"string\",\"enum\":[\"MAYBE\",\"OFF\",\"ON\"]}")
                                .hasNoDefaultValue()
                                .description("Test enum provided hints.")
                                .isNotDeprecated(),
                        Index.atIndex(1)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.message")
                                .typeName(String.class)
                                .schema("{\"type\":\"string\"}")
                                .hasNoDefaultValue()
                                .description("A message property, can be used anywhere.")
                                .isNotDeprecated(),
                        Index.atIndex(2)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.profiles")
                                .typeName("java.util.List<java.lang.String>")
                                .schema("{\"type\":\"array\",\"items\":{\"type\":\"string\",\"examples\":[\"production\",\"test\"]}}")
                                .hasNoDescription()
                                .hasDefaultValue("test")
                                .isNotDeprecated(),
                        Index.atIndex(3)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.ttl")
                                .typeName(Duration.class)
                                .schema("{\"type\":\"string\",\"format\":\"duration\"}")
                                .isNotDeprecated(),
                        Index.atIndex(4)
                );
    }

    @Test
    @DisplayName("should fail to parse invalid Spring Boot configuration metadata files")
    void shouldFailToParseInvalidMetadataFiles() throws Exception {
        final var resource = ResourceUtils.loadResource("invalid-spring-configuration-metadata.json");

        assertThatThrownBy(() -> resolver.parse(artifact, resource))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should fail to parse unknown Spring Boot configuration metadata files")
    void shouldFailToParseNonExistingMetadataFiles() {
        final var resource = new FileSystemResource("non-existent.json");

        assertThatThrownBy(() -> resolver.parse(artifact, resource))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(FileNotFoundException.class);
    }

}