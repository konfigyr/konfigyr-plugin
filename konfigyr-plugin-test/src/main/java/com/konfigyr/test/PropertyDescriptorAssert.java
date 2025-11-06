package com.konfigyr.test;

import com.konfigyr.artifactory.*;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Custom assertion type for {@link PropertyDescriptor}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 **/
public class PropertyDescriptorAssert extends AbstractAssert<PropertyDescriptorAssert, PropertyDescriptor> {

    public static PropertyDescriptorAssert assertThat(@Nullable PropertyDescriptor metadata) {
        return new PropertyDescriptorAssert(metadata);
    }

    PropertyDescriptorAssert(PropertyDescriptor metadata) {
        super(metadata, PropertyDescriptorAssert.class);
    }

    public PropertyDescriptorAssert name(String name) {
        return check(
                Objects.equals(actual.name(), name),
                "Expecting that property name is %s, but was: %s",
                name, actual.name()
        );
    }

    public PropertyDescriptorAssert schema(String schema) {
        return check(
                Objects.equals(actual.schema(), schema),
                "Expecting that property schema is %s, but was: %s",
                schema, actual.schema()
        );
    }

    public PropertyDescriptorAssert typeName(Class<?> type) {
        return typeName(type == null ? null : type.getTypeName());
    }

    public PropertyDescriptorAssert typeName(String typeName) {
        return check(
                Objects.equals(actual.typeName(), typeName),
                "Expecting that property typeName is %s, but was: %s",
                typeName, actual.typeName()
        );
    }

    public PropertyDescriptorAssert hasNoDefaultValue() {
        return check(
                Objects.equals(actual.defaultValue(), null),
                "Expecting that property has no default value, but was: %s",
                actual.defaultValue()
        );
    }

    public PropertyDescriptorAssert hasDefaultValue(String defaultValue) {
        return check(
                Objects.equals(actual.defaultValue(), defaultValue),
                "Expecting that property default value is %s, but was: %s",
                defaultValue, actual.defaultValue()
        );
    }

    public PropertyDescriptorAssert hasNoDescription() {
        return check(
                Objects.equals(actual.description(), null),
                "Expecting that property has no description, but was: %s",
                actual.description()
        );
    }

    public PropertyDescriptorAssert description(String description) {
        return check(
                Objects.equals(actual.description(), description),
                "Expecting that property description is %s, but was: %s",
                description, actual.description()
        );
    }

    public PropertyDescriptorAssert isDeprecated() {
        return isDeprecated(true);
    }

    public PropertyDescriptorAssert isNotDeprecated() {
        return isDeprecated(false);
    }

    public PropertyDescriptorAssert hasDeprecation(String reason, String replacement) {
        return hasDeprecation(new Deprecation(reason, replacement));
    }

    public PropertyDescriptorAssert hasDeprecation(Deprecation deprecation) {
        return check(
                Objects.equals(actual.deprecation(), deprecation),
                "Expecting that property deprecation is %s, but was: %s",
                deprecation, actual.deprecation()
        );
    }

    public PropertyDescriptorAssert isDeprecated(boolean isDeprecated) {
        return check(
                Objects.isNull(actual.deprecation()) != isDeprecated,
                "Expecting that Property metadata is " + (isDeprecated ? "" : "not") + " deprecated"
        );
    }

    private PropertyDescriptorAssert check(boolean check, String message, Object... args) {
        isNotNull();

        if (!check) {
            failWithMessage(String.format(message, args) + ".\n%s", Objects.toString(actual));
        }

        return this;
    }
}
