package com.konfigyr;

import com.konfigyr.test.TestFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 06.10.22, Thu
 **/
class TypeResolverTest {

    private TypeResolver resolver;

    @BeforeEach
    void setup() {
        resolver = new TypeResolver(TestFactories.classLoader());
    }

    @ParameterizedTest
    @ValueSource(classes = { String.class, Integer.class, Double.class, Duration.class, Boolean.class })
    void shouldResolveSimpleTypes(Class<?> type) {
        assertThat(resolver.resolve(type.getName()))
                .isEqualTo(ResolvedType.from(type).build())
                .returns(type, ResolvedType::getType)
                .returns(type.getTypeName(), ResolvedType::getTypeName)
                .returns(false, ResolvedType::isEnumeration)
                .returns(false, ResolvedType::isCollection)
                .returns(false, ResolvedType::isMap);
    }

    @Test
    void shouldResolveCollections() {
        assertThat(resolver.resolve("java.lang.String[]"))
                .returns(String.class, ResolvedType::getType)
                .returns(true, ResolvedType::isCollection)
                .returns(false, ResolvedType::isEnumeration)
                .returns(false, ResolvedType::isMap);

        assertThat(resolver.resolve("java.util.List<java.lang.Boolean>"))
                .returns(Boolean.class, ResolvedType::getType)
                .returns(true, ResolvedType::isCollection)
                .returns(false, ResolvedType::isEnumeration)
                .returns(false, ResolvedType::isMap);

        assertThat(resolver.resolve("java.util.Set<java.lang.String>"))
                .returns(String.class, ResolvedType::getType)
                .returns(true, ResolvedType::isCollection)
                .returns(false, ResolvedType::isEnumeration)
                .returns(false, ResolvedType::isMap);

        assertThat(resolver.resolve("java.util.Collection<java.lang.Integer>"))
                .returns(Integer.class, ResolvedType::getType)
                .returns(true, ResolvedType::isCollection)
                .returns(false, ResolvedType::isEnumeration)
                .returns(false, ResolvedType::isMap);
    }

    @Test
    void shouldResolveMaps() {
        assertThat(resolver.resolve("java.util.Map<java.lang.String,java.lang.Boolean>"))
                .returns(Boolean.class, ResolvedType::getType)
                .returns(false, ResolvedType::isCollection)
                .returns(false, ResolvedType::isEnumeration)
                .returns(true, ResolvedType::isMap);

//        assertThat(resolver.resolve("java.util.Map<java.lang.String,java.util.List<java.lang.Boolean>>"))
//                .returns(Boolean.class, ResolvedType::getType)
//                .returns(false, ResolvedType::isCollection)
//                .returns(false, ResolvedType::isEnumeration)
//                .returns(true, ResolvedType::isMap);

        assertThat(resolver.resolve("java.util.Map<java.lang.String,com.konfigyr.TypeResolverTest$TestEnum>"))
                .returns(TestEnum.class, ResolvedType::getType)
                .returns(false, ResolvedType::isCollection)
                .returns(false, ResolvedType::isEnumeration)
                .returns(true, ResolvedType::isMap);
    }

    @Test
    void shouldFailToResolveType() {
        assertThat(resolver.resolve("unknown")).isNull();
    }

    public enum TestEnum {
        ON,
        OFF,
        MAYBE
    }

}