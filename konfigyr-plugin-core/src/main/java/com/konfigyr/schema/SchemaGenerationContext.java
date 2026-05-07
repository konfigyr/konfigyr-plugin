package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.konfigyr.TypeLoader;
import com.konfigyr.artifactory.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.Hints;
import org.springframework.boot.configurationmetadata.ValueHint;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Context for schema generation.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class SchemaGenerationContext {

    @Getter
    private final ConfigurationMetadataProperty configurationMetadataProperty;
    private final TypeResolver typeResolver;
    private final TypeLoader typeLoader;

    /**
     * Resolves the given type to a {@link ResolvedType}.
     *
     * @param type the type to be resolved, never {@literal null}.
     * @return the resolved type, never {@literal null}.
     */
    public ResolvedType resolveType(Class<?> type) {
        Class<?> loadedType;

        try {
            loadedType = typeLoader.load(type);
        } catch (ClassNotFoundException e) {
            try {
                loadedType = typeLoader.load(String.class.getCanonicalName());
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }

        return typeResolver.resolve(loadedType);
    }

    /**
     * Creates a new {@link JsonSchema} builder instance with a specified {@code JsonSchemaType}.
     *
     * @param type the JSON Schema type to be set, never {@literal null}.
     * @param <T> the concrete type of the JSON Schema.
     * @param <B> the concrete type of the JSON Schema builder.
     * @return the created {@link JsonSchema JSON Schema} builder, never {@literal null}.
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonSchema, B extends JsonSchema.Builder<T, B>> B createSchema(JsonSchemaType type) {
        return (B) switch (type) {
            case ARRAY -> ArraySchema.builder();
            case BOOLEAN -> BooleanSchema.builder();
            case INTEGER -> IntegerSchema.builder();
            case NUMBER -> NumberSchema.builder();
            case STRING -> StringSchema.builder();
            case OBJECT -> ObjectSchema.builder();
            default -> NullSchema.builder();
        };
    }

    /**
     * Extracts the key hints from the configuration metadata property that can be used to provide example
     * values for the JSON Schema type definition.
     *
     * @return the extracted key hints, or an empty {@link Optional} if no key hints are available.
     */
    public Optional<Collection<String>> extractKeyHints() {
        return extractHints(Hints::getKeyHints);
    }

    /**
     * Extracts the value hints from the configuration metadata property that can be used to provide example
     * values for the JSON Schema type definition.
     *
     * @return the extracted value hints, or an empty {@link Optional} if no key hints are available.
     */
    public Optional<Collection<String>> extractValueHints() {
        return extractHints(Hints::getValueHints);
    }

    private Optional<Collection<String>> extractHints(Function<Hints, Collection<ValueHint>> provider) {
        final Hints hints = configurationMetadataProperty.getHints();

        if (hints == null) {
            return Optional.empty();
        }

        final Collection<ValueHint> values = provider.apply(hints);

        if (values.isEmpty()) {
            return Optional.empty();
        }


        final List<String> examples = values.stream()
                .map(ValueHint::getValue)
                .map(value -> Objects.toString(value, null))
                .filter(Objects::nonNull)
                .filter(Predicate.not(String::isBlank))
                .sorted()
                .toList();

        return Optional.of(examples);
    }
}
