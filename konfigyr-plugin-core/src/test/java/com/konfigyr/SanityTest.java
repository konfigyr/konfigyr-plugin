package com.konfigyr;

import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.PropertyDescriptor;
import com.konfigyr.test.TestApplicationProperties;
import com.konfigyr.test.PropertyDescriptorAssert;
import com.konfigyr.test.ResourceUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;

class SanityTest {

    final Artifact artifact = Artifact.of("com.konfigyr", "konfigyr-test-application", "1.0.0");
    final ArtifactMetadataParser parser = new ArtifactMetadataParser(ClassLoader.getSystemClassLoader());

    @Test
    @DisplayName("should generate Artifact metadata for test application")
    void generateMetadata() throws IOException {
        final var resource = ResourceUtils.loadResource("META-INF/spring-configuration-metadata.json");

        assertThat(resource.exists())
                .as("Test application should have Spring Boot configuration metadata")
                .isTrue();

        final var metadata = parser.parse(artifact, resource);

        assertThatObject(metadata)
                .isNotNull()
                .returns(artifact.groupId(), Artifact::groupId)
                .returns(artifact.artifactId(), Artifact::artifactId)
                .returns(artifact.version(), Artifact::version);

        metadata.properties().forEach(System.out::println);

        assertThat(metadata.properties())
                .hasSize(10)
                .isSortedAccordingTo(PropertyDescriptor::compareTo)
                .satisfiesExactly(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.asserting-parties")
                                .typeName("java.util.Map<java.lang.String,org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties$AssertingParty>")
                                .schema("{\"type\":\"object\",\"propertyNames\":{\"type\":\"string\"}," +
                                        "\"additionalProperties\":{\"type\":\"object\",\"properties\":" +
                                        "{\"entityId\":{\"type\":\"string\"}," +
                                        "\"metadataUri\":{\"type\":\"string\"}," +
                                        "\"singlelogout\":{\"type\":\"object\",\"properties\":{}}," +
                                        "\"singlesignon\":{\"type\":\"object\",\"properties\":{}}," +
                                        "\"verification\":{\"type\":\"object\",\"properties\":{\"credentials\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}}}}")
                                .description("Property that would trigger {@code NoClassDefFoundError} for missing {@code Saml2MessageBinding} type.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.dynamic")
                                .typeName("java.util.Map<java.lang.String,com.konfigyr.test.DynamicConfigurationType>")
                                .schema("{\"type\":\"object\",\"propertyNames\":{\"type\":\"string\"}," +
                                        "\"additionalProperties\":{\"type\":\"object\",\"properties\":" +
                                        "{\"date\":{\"type\":\"string\",\"format\":\"date\"}," +
                                        "\"dateTime\":{\"type\":\"string\",\"format\":\"date-time\"}," +
                                        "\"name\":{\"type\":\"string\"}," +
                                        "\"resource\":{\"type\":\"string\",\"format\":\"resource\"}," +
                                        "\"size\":{\"type\":\"string\",\"format\":\"data-size\"}," +
                                        "\"time\":{\"type\":\"string\",\"format\":\"time\"}}}}")
                                .description("Dynamic configuration properties.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.embedded-database-connection")
                                .typeName("org.springframework.boot.jdbc.EmbeddedDatabaseConnection")
                                .schema("{\"type\":\"string\",\"enum\":[\"DERBY\",\"H2\",\"HSQLDB\",\"NONE\"]}")
                                .description("Property that would trigger {@code NoClassDefFoundError} for missing {@code EmbeddedDatabaseType} type.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.enumerated-map")
                                .typeName("java.util.Map<com.konfigyr.test.TestApplicationProperties$EnumeratedKey,com.konfigyr.test.TestApplicationProperties$EnumeratedValue>")
                                .schema("{\"type\":\"object\",\"propertyNames\":{\"type\":\"string\",\"enum\":[\"VALUE_1\",\"VALUE_2\",\"VALUE_3\"]},\"additionalProperties\":{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}}")
                                .description("The map using enumeration as a map key. This should produce a property definition per enumeration.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.enumerated-option")
                                .typeName(TestApplicationProperties.EnumeratedOptions.class)
                                .schema("{\"type\":\"string\",\"enum\":[\"OPTION_1\",\"OPTION_2\",\"OPTION_3\"]}")
                                .description("The value using the enumeration as a value. This should provide value hints for per enumeration value.")
                                .hasDefaultValue("option-1")
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.enumerated-options")
                                .typeName("java.util.List<com.konfigyr.test.TestApplicationProperties$EnumeratedOptions>")
                                .schema("{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"OPTION_1\",\"OPTION_2\",\"OPTION_3\"]}}")
                                .description("The value using the enumeration as a value in a collection. This should provide value hints for per enumeration value.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.host")
                                .typeName(String.class)
                                .schema("{\"type\":\"string\"}")
                                .description("The host for the Acme server.")
                                .hasNoDefaultValue()
                                .hasDeprecation("Deprecation reason", "acme.server.hostname"),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.hostname")
                                .typeName(String.class)
                                .schema("{\"type\":\"string\"}")
                                .description("The hostname for the Acme server.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.nested.duration")
                                .typeName(Duration.class)
                                .schema("{\"type\":\"string\",\"format\":\"duration\"}")
                                .description("Duration used by the nested property.")
                                .hasDefaultValue("10s")
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.port")
                                .typeName(Integer.class)
                                .schema("{\"type\":\"integer\",\"format\":\"int32\"}")
                                .description("The port number used by the Acme server. Defaults to 8080.")
                                .hasDefaultValue("8080")
                                .isNotDeprecated()
                );
    }

}
