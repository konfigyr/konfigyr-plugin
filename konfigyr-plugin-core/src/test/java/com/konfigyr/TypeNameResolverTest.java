package com.konfigyr;

import com.fasterxml.classmate.ResolvedType;
import com.konfigyr.artifactory.Artifact;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TypeNameResolverTest {

    final TypeNameResolver resolver = new TypeNameResolver(ClassLoader.getSystemClassLoader());

    @MethodSource("primitive")
    @DisplayName("should resolve primitive types")
    @ParameterizedTest(name = "should resolve {1} type for: {0}")
    void resolvePrimitiveClassNames(String name, Class<?> type) {
        assertThatResolvedType(name)
                .satisfies(isTypeOf(type))
                .satisfies(hasNoTypeParameters());
    }

    static Stream<Arguments> primitive() {
        return Stream.of(
                Arguments.of("long", long.class),
                Arguments.of("int", int.class),
                Arguments.of("char", char.class),
                Arguments.of("short", short.class),
                Arguments.of("byte", byte.class)
        );
    }

    @MethodSource("simple")
    @DisplayName("should resolve types for simple class names")
    @ParameterizedTest(name = "should resolve {1} type for name: {0}")
    void resolveSimpleClassNames(String name, Class<?> type) {
        assertThatResolvedType(name)
                .satisfies(isTypeOf(type))
                .satisfies(hasNoTypeParameters())
                .returns(name, ResolvedPropertyType::getTypeName);
    }

    @MethodSource("simple")
    @DisplayName("should resolve array types")
    @ParameterizedTest(name = "should resolve {1} type for name: {0}[]")
    void resolveArrayTypes(String name, Class<?> type) {
        assertThatResolvedType(name + "[]")
                .satisfies(isTypeOf(type.arrayType()))
                .satisfies(hasNoTypeParameters());
    }

    static Stream<Arguments> simple() {
        return Stream.of(
                Arguments.of("java.lang.String", String.class),
                Arguments.of("java.lang.Integer", Integer.class),
                Arguments.of("java.lang.Boolean", Boolean.class),
                Arguments.of("java.time.Duration", Duration.class),
                Arguments.of("com.konfigyr.artifactory.Artifact", Artifact.class)
        );
    }

    @MethodSource("collection")
    @DisplayName("should resolve collection types")
    @ParameterizedTest(name = "should resolve {1} with element type of {2} for name: {0}")
    void resolveCollectionTypes(String name, Class<?> type, Class<?> elementType) {
        assertThatResolvedType(name)
                .satisfies(isTypeOf(type))
                .satisfies(hasTypeParameters(elementType));
    }

    static Stream<Arguments> collection() {
        return Stream.of(
                Arguments.of("java.util.List<java.lang.String>", List.class, String.class),
                Arguments.of("java.util.Set<java.lang.Integer>", Set.class, Integer.class),
                Arguments.of("java.util.Collection<java.lang.Boolean>", Collection.class, Boolean.class),
                Arguments.of("java.util.SortedSet<java.time.Duration>", SortedSet.class, Duration.class),
                Arguments.of("java.lang.Iterable<? extends java.lang.Class>", Iterable.class, Class.class),
                Arguments.of("java.util.Set<? super com.konfigyr.artifactory.Artifact>", Set.class, Artifact.class),
                Arguments.of("java.util.ArrayList<?>", ArrayList.class, Object.class)
        );
    }

    @MethodSource("map")
    @DisplayName("should resolve map types")
    @ParameterizedTest(name = "should resolve {1} with entries of {2}:{3} for name: {0}")
    void resolveMapTypes(String name, Class<?> type, Class<?> keyType, Class<?> valueType) {
        assertThatResolvedType(name)
                .satisfies(isTypeOf(type))
                .satisfies(hasTypeParameters(keyType, valueType));
    }

    static Stream<Arguments> map() {
        return Stream.of(
                Arguments.of("java.util.Map<java.lang.String, java.lang.Boolean>", Map.class, String.class, Boolean.class),
                Arguments.of("java.util.Map<java.lang.String,java.util.List<java.lang.String>>",
                        Map.class, String.class, List.class)
        );
    }

    @Test
    @DisplayName("should fail to resolve invalid type name")
    void invalidTypeName() {
        assertThatResolvedType("invalid type name")
                .isNull();
    }

    @Test
    @DisplayName("should fail to resolve unknown type name")
    void unknownTypeName() {
        assertThatResolvedType("com.konfigyr.UnknownType")
                .isNull();
    }

    @Test
    @DisplayName("should fail to resolve null, empty or blank type names")
    void blankTypeName() {
        assertThatResolvedType(null)
                .isNull();

        assertThatResolvedType("")
                .isNull();

        assertThatResolvedType("  ")
                .isNull();
    }

    ObjectAssert<ResolvedPropertyType> assertThatResolvedType(String className) {
        return assertThat(resolver.resolve(className));
    }

    static Consumer<ResolvedPropertyType> isTypeOf(Class<?> type) {
        return resolvedType -> assertThat(resolvedType.getType())
                .as("The resolved property should be of type %s, but was %s", type, resolvedType.getType())
                .isNotNull()
                .extracting(ResolvedType::getErasedType)
                .isEqualTo(type);
    }

    static Consumer<ResolvedPropertyType> hasTypeParameters(Class<?>... types) {
        return resolvedType -> assertThat(resolvedType.getParameters())
                .as("The resolved property should have following type parameters: %s", Arrays.asList(types))
                .map(ResolvedPropertyType::getType)
                .map(ResolvedType::getErasedType)
                .map(Class.class::cast)
                .containsExactly(types);
    }

    static Consumer<ResolvedPropertyType> hasNoTypeParameters() {
        return resolvedType -> assertThat(resolvedType.getParameters())
                .as("The resolved property should not have any type parameters")
                .isEmpty();
    }

}
