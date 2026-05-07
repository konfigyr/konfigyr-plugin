package com.konfigyr;

import com.konfigyr.artifactory.ArraySchema;
import com.konfigyr.artifactory.BooleanSchema;
import com.konfigyr.artifactory.PropertyDescriptor;
import com.konfigyr.artifactory.StringSchema;
import com.konfigyr.test.PropertyDescriptorAssert;
import com.konfigyr.test.ResourceUtils;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactMetadataParserTest {

    ArtifactMetadataParser resolver;

    @BeforeEach
    void setup() {
        resolver = new ArtifactMetadataParser(ClassLoader.getSystemClassLoader());
    }

    @Test
    @DisplayName("should parse Spring Boot configuration metadata and create an Artifact Metadata for upload")
    void shouldParseMetadata() throws Exception {
        final List<PropertyDescriptor> descriptors = resolver.parse(
                ResourceUtils.loadResources(
                        "additional-spring-configuration-metadata.json",
                        "spring-configuration-metadata.json"

                )
        );

        assertThat(descriptors)
                .isNotNull()
                .isNotEmpty()
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.enabled")
                                .typeName(Boolean.class)
                                .schema(BooleanSchema.instance())
                                .hasNoDefaultValue()
                                .hasNoDescription()
                                .hasDeprecation("No longer needed", null),
                        Index.atIndex(0)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.enumeration")
                                .typeName(TestEnumeration.class)
                                .schema(StringSchema.builder()
                                        .enumeration("ON")
                                        .enumeration("OFF")
                                        .enumeration("MAYBE")
                                        .build()
                                )
                                .hasNoDefaultValue()
                                .description("Test enum provided hints.")
                                .isNotDeprecated(),
                        Index.atIndex(1)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.message")
                                .typeName(String.class)
                                .schema(StringSchema.instance())
                                .hasNoDefaultValue()
                                .description("A message property, can be used anywhere.")
                                .isNotDeprecated(),
                        Index.atIndex(2)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.profiles")
                                .typeName("java.util.List<java.lang.String>")
                                .schema(ArraySchema.builder()
                                        .items(StringSchema.builder()
                                                .example("production")
                                                .example("test")
                                                .build()
                                        ).build()
                                )
                                .hasNoDescription()
                                .hasDefaultValue("test")
                                .isNotDeprecated(),
                        Index.atIndex(3)
                )
                .satisfies(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.ttl")
                                .typeName(Duration.class)
                                .schema(StringSchema.builder().format("duration").build())
                                .isNotDeprecated(),
                        Index.atIndex(4)
                );
    }

    @Test
    @DisplayName("should fail to parse invalid Spring Boot configuration metadata files")
    void shouldFailToParseInvalidMetadataFiles() throws Exception {
        final var resource = ResourceUtils.loadResource("invalid-spring-configuration-metadata.json");

        assertThatThrownBy(() -> resolver.parse(resource))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should fail to parse unknown Spring Boot configuration metadata files")
    void shouldFailToParseNonExistingMetadataFiles() {
        final var resource = new FileSystemResource("non-existent.json");

        assertThatThrownBy(() -> resolver.parse(resource))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(FileNotFoundException.class);
    }

}