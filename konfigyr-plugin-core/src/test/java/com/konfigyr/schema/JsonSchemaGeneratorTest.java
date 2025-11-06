package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.konfigyr.TestEnumeration;
import lombok.Value;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ObjectAssert;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatObject;

@ExtendWith(MockitoExtension.class)
class JsonSchemaGeneratorTest {

    final TypeResolver typeResolver = new TypeResolver();
    final DefaultJsonSchemaGenerator generator = new DefaultJsonSchemaGenerator();

    @Mock
    ConfigurationMetadataProperty metadata;

    @Test
    @DisplayName("should generate schema for String")
    void generatesSchemaForString() {
        assertThatSchema(String.class)
                .satisfies(isOfType("string"))
                .satisfies(hasNoEntry("format"));
    }

    @Test
    @DisplayName("should generate schema for Duration")
    void generatesSchemaForDuration() {
        assertThatSchema(Duration.class)
                .satisfies(isOfType("string"))
                .satisfies(hasFormat("duration"));
    }

    @Test
    @DisplayName("should generate schema for enumerations")
    void generatesEnumSchema() {
        assertThatSchema(TestEnumeration.class)
                .satisfies(isOfType("string"))
                .extracting(node -> node.get("enum"))
                .returns(true, JsonNode::isArray)
                .returns(3, JsonNode::size)
                .returns("[\"MAYBE\",\"OFF\",\"ON\"]", JsonNode::toString);
    }

    @Test
    @DisplayName("should generate schema for list of strings")
    void generatesSchemaForListOfStrings() {
        assertThatSchema(typeResolver.resolve(List.class, String.class))
                .satisfies(isOfType("array"))
                .extracting(node -> node.get("items"))
                .returns(true, JsonNode::isObject)
                .satisfies(isOfType("string"))
                .satisfies(hasNoEntry("format"));
    }

    @Test
    @DisplayName("should generate schema for a map of strings to integers")
    void generatesObjectForMapOfStringToInt() {
        assertThatSchema(typeResolver.resolve(Map.class, String.class, int.class))
                .satisfies(isOfType("object"))
                .extracting(node -> node.get("additionalProperties"))
                .returns(true, JsonNode::isObject)
                .satisfies(isOfType("integer"))
                .satisfies(hasFormat("int32"));
    }

    @Test
    @DisplayName("should generate schema for a Java object instance")
    void generatesForObject() {
        assertThatSchema(Object.class)
                .satisfies(isOfType("object"))
                .extracting(node -> node.get("properties"))
                .returns(true, JsonNode::isObject)
                .returns(true, JsonNode::isEmpty);
    }

    @Test
    @DisplayName("should generate schema for a plain Java object")
    void generatesForType() {
        assertThatSchema(TestPojo.class)
                .satisfies(isOfType("object"))
                .satisfies(hasRequiredProperties("active", "age"))
                .extracting(node -> node.get("properties"))
                .returns(true, JsonNode::isObject)
                .extracting(JsonNode::toString)
                .isEqualTo("{\"active\":{\"type\":\"boolean\",\"deprecated\":true},\"age\":{\"type\":\"integer\",\"format\":\"int32\"},\"name\":{\"type\":\"string\"}}");
    }

    @Test
    @DisplayName("should generate schema for a record Java object")
    void generatesForRecord() {
        assertThatSchema(TestRecord.class)
                .satisfies(isOfType("object"))
                .satisfies(hasRequiredProperties("active", "age"))
                .extracting(node -> node.get("properties"))
                .returns(true, JsonNode::isObject)
                .extracting(JsonNode::toString)
                .isEqualTo("{\"active\":{\"type\":\"boolean\",\"deprecated\":true},\"age\":{\"type\":\"integer\",\"format\":\"int32\"},\"name\":{\"type\":\"string\"}}");
    }

    ObjectAssert<ObjectNode> assertThatSchema(Class<?> type) {
        return assertThatSchema(typeResolver.resolve(type));
    }

    ObjectAssert<ObjectNode> assertThatSchema(ResolvedType resolvedType) {
        return assertThatObject(generator.generateSchema(resolvedType, metadata))
                .isNotNull();
    }

    static Consumer<JsonNode> isOfType(String type) {
        return hasEntry("type", type);
    }

    static Consumer<JsonNode> hasFormat(String format) {
        return hasEntry("format", format);
    }

    static Consumer<JsonNode> hasRequiredProperties(String... properties) {
        return node -> assertThatObject(node.get("required"))
                .as("JSON node should have following required properties '%s'", Arrays.toString(properties))
                .isNotNull()
                .returns(true, JsonNode::isArray)
                .asInstanceOf(InstanceOfAssertFactories.iterable(JsonNode.class))
                .map(JsonNode::asString)
                .containsExactlyInAnyOrder(properties);
    }

    static Consumer<JsonNode> hasEntry(String key, String value) {
        return node -> assertThatObject(node.get(key))
                .as("JSON node should have a property '%s' with value '%s'", key, value)
                .isNotNull()
                .extracting(JsonNode::asString)
                .isEqualTo(value);
    }

    static Consumer<JsonNode> hasNoEntry(String key) {
        return node -> assertThatObject(node.get(key))
                .as("JSON node should not have a property '%s'", key)
                .isNull();
    }

    interface Named {
        String getName();
    }

    @Value
    static class TestPojo implements Named, Comparable<TestPojo> {
        String name;
        int age;
        @Deprecated
        boolean active;

        @Override
        public int compareTo(@NonNull TestPojo o) {
            return name.compareTo(o.name);
        }
    }

    record TestRecord(String name, int age, @Deprecated boolean active) {

    }

}
