package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.konfigyr.TestEnumeration;
import com.konfigyr.TypeLoader;
import com.konfigyr.artifactory.*;
import lombok.Value;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ObjectAssert;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatObject;

@ExtendWith(MockitoExtension.class)
class JsonSchemaGeneratorTest {

    final TypeResolver typeResolver = new TypeResolver();
    final DefaultJsonSchemaGenerator generator = new DefaultJsonSchemaGenerator(new TypeLoader(), typeResolver);

    @Mock
    ConfigurationMetadataProperty metadata;

    @ValueSource(classes = {
            String.class, Character.class, char.class, CharSequence.class,
            Byte.class, byte.class, InetAddress.class, Class.class
    })
    @ParameterizedTest(name = "should generate string schema for \"{0}\"")
    @DisplayName("should generate schema for string like types without any format or pattern")
    void generatesSchemaForString(Class<?> type) {
        assertThatSchema(type)
                .isEqualTo(StringSchema.instance());
    }

    @ValueSource(classes = LocalDate.class)
    @ParameterizedTest(name = "should generate date string schema for \"{0}\"")
    @DisplayName("should generate string schema with date format")
    void generatesDateStringSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.STRING, JsonSchema::type)
                .isInstanceOf(StringSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(StringSchema.class))
                .returns("date", StringSchema::format)
                .returns(null, StringSchema::pattern);
    }

    @ValueSource(classes = {
            LocalDateTime.class, ZonedDateTime.class, OffsetDateTime.class,
            Instant.class, Date.class, Calendar.class
    })
    @ParameterizedTest(name = "should generate date-time string schema for \"{0}\"")
    @DisplayName("should generate string schema with date-time format")
    void generatesDateTimeStringSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.STRING, JsonSchema::type)
                .isInstanceOf(StringSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(StringSchema.class))
                .returns("date-time", StringSchema::format)
                .returns(null, StringSchema::pattern);
    }

    @ValueSource(classes = { LocalTime.class, OffsetTime.class })
    @ParameterizedTest(name = "should generate time string schema for \"{0}\"")
    @DisplayName("should generate string schema with time format")
    void generatesTimeStringSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.STRING, JsonSchema::type)
                .isInstanceOf(StringSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(StringSchema.class))
                .returns("time", StringSchema::format)
                .returns(null, StringSchema::pattern);
    }

    @ValueSource(classes = { Duration.class, Period.class })
    @ParameterizedTest(name = "should generate duration string schema for \"{0}\"")
    @DisplayName("should generate string schema with duration format")
    void generatesDurationStringSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.STRING, JsonSchema::type)
                .isInstanceOf(StringSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(StringSchema.class))
                .returns("duration", StringSchema::format)
                .returns(null, StringSchema::pattern);
    }

    @MethodSource("customStringFormats")
    @ParameterizedTest(name = "should generate string schema for \"{0}\" with {1} format")
    @DisplayName("should generate string schema with a custom format")
    void generatesDurationStringSchema(Class<?> type, String format) {
        assertThatSchema(type)
                .returns(JsonSchemaType.STRING, JsonSchema::type)
                .isInstanceOf(StringSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(StringSchema.class))
                .returns(format, StringSchema::format)
                .returns(null, StringSchema::pattern);
    }

    @ValueSource(classes = { Boolean.class, boolean.class })
    @ParameterizedTest(name = "should generate boolean schema for \"{0}\"")
    @DisplayName("should generate simple boolean schema")
    void generatesBooleanSchema(Class<?> type) {
        assertThatSchema(type)
                .isEqualTo(BooleanSchema.instance());
    }

    @ValueSource(classes = { BigInteger.class, Short.class, short.class })
    @ParameterizedTest(name = "should generate integer schema for \"{0}\"")
    @DisplayName("should generate simple integer schema without formats")
    void generatesIntegerSchema(Class<?> type) {
        assertThatSchema(type)
                .isEqualTo(IntegerSchema.instance());
    }

    @ValueSource(classes = { Integer.class, int.class })
    @ParameterizedTest(name = "should generate integer schema for \"{0}\" with int32 format")
    @DisplayName("should generate int32 format based integer schema")
    void generatesShortIntegerSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.INTEGER, JsonSchema::type)
                .isInstanceOf(IntegerSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(IntegerSchema.class))
                .returns("int32", IntegerSchema::format);
    }

    @ValueSource(classes = { Long.class, long.class })
    @ParameterizedTest(name = "should generate integer schema for \"{0}\" with int64 format")
    @DisplayName("should generate int64 format based integer schema")
    void generatesLongIntegerSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.INTEGER, JsonSchema::type)
                .isInstanceOf(IntegerSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(IntegerSchema.class))
                .returns("int64", IntegerSchema::format);
    }

    @ValueSource(classes = { BigDecimal.class, Number.class })
    @ParameterizedTest(name = "should generate number schema for \"{0}\"")
    @DisplayName("should generate simple number schema without formats")
    void generatesNumberSchema(Class<?> type) {
        assertThatSchema(type)
                .isEqualTo(NumberSchema.instance());
    }

    @ValueSource(classes = { Double.class, double.class })
    @ParameterizedTest(name = "should generate number schema for \"{0}\" with double format")
    @DisplayName("should generate double format based number schema")
    void generatesDoubleNumberSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.NUMBER, JsonSchema::type)
                .isInstanceOf(NumberSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(NumberSchema.class))
                .returns("double", NumberSchema::format);
    }

    @ValueSource(classes = { Float.class, float.class })
    @ParameterizedTest(name = "should generate number schema for \"{0}\" with float format")
    @DisplayName("should generate float format based number schema")
    void generatesFloatNumberSchema(Class<?> type) {
        assertThatSchema(type)
                .returns(JsonSchemaType.NUMBER, JsonSchema::type)
                .isInstanceOf(NumberSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(NumberSchema.class))
                .returns("float", NumberSchema::format);
    }

    @Test
    @DisplayName("should generate schema for enumerations")
    void generatesEnumSchema() {
        assertThatSchema(TestEnumeration.class)
                .returns(JsonSchemaType.STRING, JsonSchema::type)
                .isInstanceOf(StringSchema.class)
                .returns(List.of("MAYBE", "OFF", "ON"), JsonSchema::enumerations);
    }

    @Test
    @DisplayName("should generate schema for list of strings")
    void generatesSchemaForListOfStrings() {
        assertThatSchema(typeResolver.resolve(List.class, String.class))
                .returns(JsonSchemaType.ARRAY, JsonSchema::type)
                .isInstanceOf(ArraySchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ArraySchema.class))
                .returns(StringSchema.instance(), ArraySchema::items);
    }

    @Test
    @DisplayName("should generate schema for list of big decimals")
    void generatesSchemaForListOfIntegers() {
        assertThatSchema(typeResolver.resolve(List.class, BigDecimal.class))
                .returns(JsonSchemaType.ARRAY, JsonSchema::type)
                .isInstanceOf(ArraySchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ArraySchema.class))
                .returns(NumberSchema.instance(), ArraySchema::items);
    }

    @Test
    @DisplayName("should generate schema for a map of strings to integers")
    void generatesObjectForMapOfStringToInt() {
        assertThatSchema(typeResolver.resolve(Map.class, String.class, int.class))
                .returns(JsonSchemaType.OBJECT, JsonSchema::type)
                .isInstanceOf(ObjectSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ObjectSchema.class))
                .returns(IntegerSchema.builder().format("int32").build(), ObjectSchema::additionalProperties);
    }

    @Test
    @DisplayName("should generate schema for a Java object instance")
    void generatesForObject() {
        assertThatSchema(Object.class)
                .isEqualTo(ObjectSchema.instance());
    }

    @Test
    @DisplayName("should generate schema for a plain Java object")
    void generatesForType() {
        assertThatSchema(TestPojo.class)
                .returns(JsonSchemaType.OBJECT, JsonSchema::type)
                .isInstanceOf(ObjectSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ObjectSchema.class))
                .returns(List.of("active", "age"), ObjectSchema::required)
                .extracting(ObjectSchema::properties, InstanceOfAssertFactories.map(String.class, JsonSchema.class))
                .containsEntry("active", BooleanSchema.builder().deprecated(true).build())
                .containsEntry("age", IntegerSchema.builder().format("int32").build())
                .containsEntry("name", StringSchema.instance());
    }

    @Test
    @DisplayName("should generate schema for a record Java object")
    void generatesForRecord() {
        assertThatSchema(TestRecord.class)
                .returns(JsonSchemaType.OBJECT, JsonSchema::type)
                .isInstanceOf(ObjectSchema.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ObjectSchema.class))
                .returns(List.of("active", "age"), ObjectSchema::required)
                .extracting(ObjectSchema::properties, InstanceOfAssertFactories.map(String.class, JsonSchema.class))
                .containsEntry("active", BooleanSchema.builder().deprecated(true).build())
                .containsEntry("age", IntegerSchema.builder().format("int32").build())
                .containsEntry("name", StringSchema.instance());
    }

    ObjectAssert<JsonSchema> assertThatSchema(Class<?> type) {
        return assertThatSchema(typeResolver.resolve(type));
    }

    ObjectAssert<JsonSchema> assertThatSchema(ResolvedType resolvedType) {
        return assertThatObject(generator.generateSchema(resolvedType, metadata))
                .isNotNull();
    }

    static Stream<Arguments> customStringFormats() {
        return Stream.of(
                Arguments.of(UUID.class, "uuid"),
                Arguments.of(Charset.class, "charset"),
                Arguments.of(ZoneId.class, "time-zone"),
                Arguments.of(TimeZone.class, "time-zone"),
                Arguments.of(Locale.class, "language"),
                Arguments.of(Resource.class, "resource"),
                Arguments.of(MimeType.class, "mime-type"),
                Arguments.of(DataSize.class, "data-size"),
                Arguments.of(Inet4Address.class, "ipv4"),
                Arguments.of(Inet6Address.class, "ipv6")
        );
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
