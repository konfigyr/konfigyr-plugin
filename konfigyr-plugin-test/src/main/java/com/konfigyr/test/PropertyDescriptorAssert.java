package com.konfigyr.test;

import com.konfigyr.artifactory.*;
import org.assertj.core.annotation.CanIgnoreReturnValue;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Custom assertion type for {@link PropertyDescriptor}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 **/
@CanIgnoreReturnValue
public class PropertyDescriptorAssert extends AbstractAssert<PropertyDescriptorAssert, PropertyDescriptor> {

    /**
     * Creates a new instance of {@link PropertyDescriptorAssert} for the given {@link PropertyDescriptor}.
     *
     * @param metadata the property descriptor to be asserted, can be {@literal null}.
     * @return the assertion instance, never {@literal null}.
     */
    public static PropertyDescriptorAssert assertThat(@Nullable PropertyDescriptor metadata) {
        return new PropertyDescriptorAssert(metadata);
    }

    /**
     * Creates a new instance of {@link PropertyDescriptorAssert}.
     *
     * @param metadata the property descriptor to be asserted, can be {@literal null}.
     */
    PropertyDescriptorAssert(PropertyDescriptor metadata) {
        super(metadata, PropertyDescriptorAssert.class);
    }

    /**
     * Verifies that the property descriptor has the expected name.
     *
     * @param name the expected property name, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert name(String name) {
        return check(
                Objects.equals(actual.name(), name),
                "Expecting that property name is %s, but was: %s",
                name, actual.name()
        );
    }

    /**
     * Verifies that the property descriptor has the expected JSON schema.
     *
     * @param schema the expected JSON schema, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert schema(JsonSchema schema) {
        return check(
                Objects.equals(actual.schema(), schema),
                "Expecting that property JSON schema is %s, but was: %s",
                schema, actual.schema()
        );
    }

    /**
     * Verifies that the property descriptor has the expected type name derived from the given class.
     *
     * @param type the expected type class, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert typeName(Class<?> type) {
        return typeName(type == null ? null : type.getTypeName());
    }

    /**
     * Verifies that the property descriptor has the expected type name.
     *
     * @param typeName the expected type name, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert typeName(String typeName) {
        return check(
                Objects.equals(actual.typeName(), typeName),
                "Expecting that property typeName is %s, but was: %s",
                typeName, actual.typeName()
        );
    }

    /**
     * Verifies that the property descriptor has no default value.
     *
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert hasNoDefaultValue() {
        return check(
                Objects.equals(actual.defaultValue(), null),
                "Expecting that property has no default value, but was: %s",
                actual.defaultValue()
        );
    }

    /**
     * Verifies that the property descriptor has the expected default value.
     *
     * @param defaultValue the expected default value, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert hasDefaultValue(String defaultValue) {
        return check(
                Objects.equals(actual.defaultValue(), defaultValue),
                "Expecting that property default value is %s, but was: %s",
                defaultValue, actual.defaultValue()
        );
    }

    /**
     * Verifies that the property descriptor has no description.
     *
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert hasNoDescription() {
        return check(
                Objects.equals(actual.description(), null),
                "Expecting that property has no description, but was: %s",
                actual.description()
        );
    }

    /**
     * Verifies that the property descriptor has the expected description.
     *
     * @param description the expected description, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert description(String description) {
        return check(
                Objects.equals(actual.description(), description),
                "Expecting that property description is %s, but was: %s",
                description, actual.description()
        );
    }

    /**
     * Verifies that the property descriptor is deprecated.
     *
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert isDeprecated() {
        return isDeprecated(true);
    }

    /**
     * Verifies that the property descriptor is not deprecated.
     *
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert isNotDeprecated() {
        return isDeprecated(false);
    }

    /**
     * Verifies that the property descriptor has the expected deprecation with the given reason and replacement.
     *
     * @param reason      the expected deprecation reason, can be {@literal null}.
     * @param replacement the expected deprecation replacement, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert hasDeprecation(String reason, String replacement) {
        return hasDeprecation(new Deprecation(reason, replacement));
    }

    /**
     * Verifies that the property descriptor has the expected deprecation.
     *
     * @param deprecation the expected deprecation, can be {@literal null}.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert hasDeprecation(Deprecation deprecation) {
        return check(
                Objects.equals(actual.deprecation(), deprecation),
                "Expecting that property deprecation is %s, but was: %s",
                deprecation, actual.deprecation()
        );
    }

    /**
     * Verifies that the property descriptor deprecation status matches the expected value.
     *
     * @param isDeprecated the expected deprecation status.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    public PropertyDescriptorAssert isDeprecated(boolean isDeprecated) {
        return check(
                Objects.isNull(actual.deprecation()) != isDeprecated,
                "Expecting that Property metadata is " + (isDeprecated ? "" : "not") + " deprecated"
        );
    }

    /**
     * Performs a validation check and fails with a formatted message if the check fails.
     *
     * @param check   the boolean condition to verify.
     * @param message the error message format string.
     * @param args    the arguments for the message format string.
     * @return this assertion instance for method chaining, never {@literal null}.
     */
    private PropertyDescriptorAssert check(boolean check, String message, Object... args) {
        isNotNull();

        if (!check) {
            failWithMessage(String.format(message, args) + ".\n%s", Objects.toString(actual));
        }

        return this;
    }
}
