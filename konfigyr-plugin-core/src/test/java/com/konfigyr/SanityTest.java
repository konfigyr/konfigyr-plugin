package com.konfigyr;

import com.konfigyr.artifactory.*;
import com.konfigyr.test.PropertyDescriptorAssert;
import com.konfigyr.test.ResourceUtils;
import com.konfigyr.test.TestApplicationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SanityTest {

    final ArtifactMetadataParser parser = new ArtifactMetadataParser(ClassLoader.getSystemClassLoader());

    @Test
    @DisplayName("should generate Artifact metadata for test application")
    void generateMetadata() throws IOException {
        final var resource = ResourceUtils.loadResource("META-INF/spring-configuration-metadata.json");

        assertThat(resource.exists())
                .as("Test application should have Spring Boot configuration metadata")
                .isTrue();

        final var descriptors = parser.parse(resource);

        assertThat(descriptors)
                .hasSize(14)
                .isSortedAccordingTo(PropertyDescriptor::compareTo)
                .satisfiesExactly(
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.allowed-ips")
                                .typeName(Inet4Address.class.arrayType())
                                .schema(ArraySchema.builder().items(StringSchema.builder().format("ipv4").build()).build())
                                .description("Array of allowed IP addresses.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.blocked-ips")
                                .typeName(Inet6Address.class.arrayType())
                                .schema(ArraySchema.builder().items(StringSchema.builder().format("ipv6").build()).build())
                                .description("Array of blocked IP addresses.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.connections")
                                .typeName("java.util.Map<java.lang.String,com.konfigyr.test.TestApplicationProperties$ConnectionConfiguration>")
                                .schema(ObjectSchema.builder()
                                        .propertyNames(StringSchema.instance())
                                        .additionalProperties(
                                                ObjectSchema.builder()
                                                        .property("key", StringSchema.builder().format("uri").build())
                                                        .property("size", StringSchema.builder().format("data-size").build())
                                                        .property("ttl", StringSchema.builder().format("duration").build())
                                                        .property("maxPoolSize", IntegerSchema.instance())
                                                        .property("missing", ObjectSchema.instance())
                                                        .build()
                                        )
                                        .build())
                                .description("Property that would trigger {@code NoClassDefFoundError} for missing {@code DataJdbcDatabaseDialect} type.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.dynamic")
                                .typeName("java.util.Map<java.lang.String,com.konfigyr.test.DynamicConfigurationType>")
                                .schema(ObjectSchema.builder()
                                        .propertyNames(StringSchema.instance())
                                        .additionalProperties(
                                                ObjectSchema.builder()
                                                        .property("date", StringSchema.builder().format("date").build())
                                                        .property("dateTime", StringSchema.builder().format("date-time").build())
                                                        .property("name", StringSchema.instance())
                                                        .property("resource", StringSchema.builder().format("resource").build())
                                                        .property("size", StringSchema.builder().format("data-size").build())
                                                        .property("time", StringSchema.builder().format("time").build())
                                                        .build()
                                        ).build()
                                )
                                .description("Dynamic configuration properties.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.embedded-database-connection")
                                .typeName("org.springframework.boot.jdbc.EmbeddedDatabaseConnection")
                                .schema(StringSchema.instance())
                                .description("Property that would trigger {@code NoClassDefFoundError} for missing {@code EmbeddedDatabaseType} type.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.enumerated-map")
                                .typeName("java.util.Map<com.konfigyr.test.TestApplicationProperties$EnumeratedKey,com.konfigyr.test.TestApplicationProperties$EnumeratedValue>")
                                .schema(ObjectSchema.builder()
                                        .propertyNames(
                                                StringSchema.builder()
                                                        .enumeration("VALUE_1")
                                                        .enumeration("VALUE_2")
                                                        .enumeration("VALUE_3")
                                                        .build()
                                        )
                                        .additionalProperties(
                                                ObjectSchema.builder()
                                                        .property("value", StringSchema.instance())
                                                        .build()
                                        )
                                        .build()
                                )
                                .description("The map using enumeration as a map key. This should produce a property definition per enumeration.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.enumerated-option")
                                .typeName(TestApplicationProperties.EnumeratedOptions.class)
                                .schema(StringSchema.builder()
                                        .enumeration("OPTION_1")
                                        .enumeration("OPTION_2")
                                        .enumeration("OPTION_3")
                                        .build()
                                )
                                .description("The value using the enumeration as a value. This should provide value hints for per enumeration value.")
                                .hasDefaultValue("option-1")
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.enumerated-options")
                                .typeName("java.util.List<com.konfigyr.test.TestApplicationProperties$EnumeratedOptions>")
                                .schema(ArraySchema.builder()
                                        .items(StringSchema.builder()
                                                .enumeration("OPTION_1")
                                                .enumeration("OPTION_2")
                                                .enumeration("OPTION_3")
                                                .build()
                                        )
                                        .build()
                                )
                                .description("The value using the enumeration as a value in a collection. This should provide value hints for per enumeration value.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.host")
                                .typeName(String.class)
                                .schema(StringSchema.instance())
                                .description("The host for the Acme server.")
                                .hasNoDefaultValue()
                                .hasDeprecation("Deprecation reason", "acme.server.hostname"),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.hostname")
                                .typeName(String.class)
                                .schema(StringSchema.instance())
                                .description("The hostname for the Acme server.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.nested.content-type")
                                .typeName("org.springframework.http.MediaType")
                                .schema(StringSchema.builder().format("mime-type").build())
                                .description("Content type of the resource.")
                                .hasDefaultValue("application-json")
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.nested.duration")
                                .typeName(Duration.class)
                                .schema(StringSchema.builder().format("duration").build())
                                .description("Duration used by the nested property.")
                                .hasDefaultValue("10s")
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.nested.resource")
                                .typeName(Resource.class)
                                .schema(StringSchema.builder().format("resource").build())
                                .description("Resource property.")
                                .hasNoDefaultValue()
                                .isNotDeprecated(),
                        it -> PropertyDescriptorAssert.assertThat(it)
                                .name("konfigyr.server.port")
                                .typeName(Integer.class)
                                .schema(IntegerSchema.builder().format("int32").build())
                                .description("The port number used by the Acme server. Defaults to 8080.")
                                .hasDefaultValue("8080")
                                .isNotDeprecated()
                );
    }

}
