package com.konfigyr.test;

import com.konfigyr.PropertyKind;
import com.konfigyr.PropertyMetadata;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 15.10.22, Sat
 **/
public class PropertyMetadataAssert extends AbstractAssert<PropertyMetadataAssert, PropertyMetadata> {

    public static PropertyMetadataAssert assertThat(@Nullable PropertyMetadata metadata) {
        return new PropertyMetadataAssert(metadata);
    }

    PropertyMetadataAssert(PropertyMetadata metadata) {
        super(metadata, PropertyMetadataAssert.class);
    }

    public PropertyMetadataAssert name(String name) {
        return check(
                Objects.equals(actual.getName(), name),
                "Expecting that Property metadata name is %s, but was: %s",
                name, actual.getName()
        );
    }

    public PropertyMetadataAssert type(Class<?> type) {
        return type(type.getTypeName());
    }

    public PropertyMetadataAssert type(String type) {
        return check(
                Objects.equals(actual.getType(), type),
                "Expecting that Property metadata type is %s, but was: %s",
                type, actual.getType()
        );
    }

    public PropertyMetadataAssert kind(PropertyKind kind) {
        return check(
                Objects.equals(actual.getKind(), kind),
                "Expecting that Property metadata kind is %s, but was: %s",
                kind, actual.getKind()
        );
    }

    public PropertyMetadataAssert isCollection() {
        return isCollection(true);
    }

    public PropertyMetadataAssert isNotCollection() {
        return isCollection(false);
    }

    public PropertyMetadataAssert isCollection(boolean isCollection) {
        return check(
                actual.isCollection() == isCollection,
                "Expecting that Property metadata kind is " + (isCollection ? "" : "not") + " a collection"
        );
    }

    public PropertyMetadataAssert isMap() {
        return isMap(true);
    }

    public PropertyMetadataAssert isNotMap() {
        return isMap(false);
    }

    public PropertyMetadataAssert isMap(boolean isMap) {
        return check(
                actual.isMap() == isMap,
                "Expecting that Property metadata kind is " + (isMap ? "" : "not") + " a map"
        );
    }

    public PropertyMetadataAssert isDeprecated() {
        return isDeprecated(true);
    }

    public PropertyMetadataAssert isNotDeprecated() {
        return isDeprecated(false);
    }

    public PropertyMetadataAssert hints(String... hints) {
        return hints(Arrays.asList(hints));
    }

    public PropertyMetadataAssert hints(Iterable<String> hints) {
        Assertions.assertThat(hints).containsExactlyInAnyOrderElementsOf(hints);

        return this;
    }

    public PropertyMetadataAssert isDeprecated(boolean isDeprecated) {
        return check(
                Objects.isNull(actual.getDeprecation()) != isDeprecated,
                "Expecting that Property metadata kind is " + (isDeprecated ? "" : "not") + " deprecated"
        );
    }

    private PropertyMetadataAssert check(boolean check, String message, Object... args) {
        isNotNull();

        if (!check) {
            failWithMessage(String.format(message, args) + ".\n%s", Objects.toString(actual));
        }

        return this;
    }
}
