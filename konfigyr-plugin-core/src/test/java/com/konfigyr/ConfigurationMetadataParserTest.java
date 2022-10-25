package com.konfigyr;

import com.konfigyr.test.PropertyMetadataAssert;
import com.konfigyr.test.TestFactories;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 07.10.22, Fri
 **/
class ConfigurationMetadataParserTest {

    ConfigurationMetadataParser resolver;
    Artifact artifact = TestFactories.artifact();

    @BeforeEach
    void setup() {
        resolver = new ConfigurationMetadataParser(TestFactories.classLoader());
    }

    @Test
    void shouldParseMetadata() throws Exception {
        final ConfigurationMetadata metadata = resolver.parse(
                artifact,
                TestFactories.loadResources(
                        "additional-spring-configuration-metadata.json",
                        "spring-configuration-metadata.json"

                )
        );

        assertThat(metadata)
                .isNotNull()
                .isNotEmpty()
                .satisfiesExactlyInAnyOrder(
                        it -> PropertyMetadataAssert.assertThat(it)
                                .name("konfigyr.message")
                                .type(String.class)
                                .kind(PropertyKind.STRING)
                                .isNotDeprecated()
                                .isNotCollection()
                                .isNotMap(),
                        it -> PropertyMetadataAssert.assertThat(it)
                                .name("konfigyr.enabled")
                                .type(Boolean.class)
                                .kind(PropertyKind.BOOLEAN)
                                .isNotDeprecated()
                                .isNotCollection()
                                .isNotMap(),
                        it -> PropertyMetadataAssert.assertThat(it)
                                .name("konfigyr.profiles")
                                .type("java.util.List<java.lang.String>")
                                .kind(PropertyKind.STRING)
                                .isNotDeprecated()
                                .isCollection()
                                .isNotMap(),
                        it -> PropertyMetadataAssert.assertThat(it)
                                .name("konfigyr.ttl")
                                .type(Duration.class)
                                .kind(PropertyKind.DURATION)
                                .isNotDeprecated()
                                .isNotCollection()
                                .isNotMap()
                );

        assertThat(metadata.getArtifact())
                .isEqualTo(artifact);
    }

    @Test
    void shouldFailToParseInvalidMetadataFiles() {
        assertThatThrownBy(() -> resolver.parse(
                artifact,
                TestFactories.loadResources(
                        "invalid-spring-configuration-metadata.json"
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(JSONException.class);
    }

    @Test
    void shouldFailToParseNonExistingMetadataFiles() {
        assertThatThrownBy(() -> resolver.parse(
                artifact,
                Collections.singleton(new File("non-existent.json"))
        )).isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(FileNotFoundException.class);
    }

}