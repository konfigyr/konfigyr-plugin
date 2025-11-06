package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.Hints;
import org.springframework.boot.configurationmetadata.ValueHint;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collection;
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
    private final JsonNodeFactory jsonNodeFactory;
    private final TypeResolver typeResolver;

    /**
     * Resolves the given type to a {@link ResolvedType}.
     *
     * @param type the type to be resolved, never {@literal null}.
     * @return the resolved type, never {@literal null}.
     */
    public ResolvedType resolveType(Class<?> type) {
        return typeResolver.resolve(type);
    }

    /**
     * Creates a new {@link ObjectNode} with a single {@code type} property set to the given value.
     *
     * @param type the JSON Schema type to be set, never {@literal null}.
     * @return the created {@link ObjectNode JSON Schema}, never {@literal null}.
     */
    public ObjectNode createSchema(String type) {
        return jsonNodeFactory.objectNode().put("type", type);
    }

    /**
     * Returns the {@link JsonNodeFactory} used to create JSON nodes required for schema generation.
     *
     * @return the JSON node factory, never {@literal null}.
     */
    public JsonNodeFactory getNodeFactory() {
        return jsonNodeFactory;
    }

    /**
     * Extracts the key hints from the configuration metadata property that can be used to provide example
     * values for the JSON Schema type definition.
     *
     * @return the extracted key hints, or an empty {@link Optional} if no key hints are available.
     */
    public Optional<ArrayNode> extractKeyHints() {
        return extractHints(Hints::getKeyHints);
    }

    /**
     * Extracts the value hints from the configuration metadata property that can be used to provide example
     * values for the JSON Schema type definition.
     *
     * @return the extracted value hints, or an empty {@link Optional} if no key hints are available.
     */
    public Optional<ArrayNode> extractValueHints() {
        return extractHints(Hints::getValueHints);
    }

    private Optional<ArrayNode> extractHints(Function<Hints, Collection<ValueHint>> provider) {
        final Hints hints = configurationMetadataProperty.getHints();

        if (hints == null) {
            return Optional.empty();
        }

        final Collection<ValueHint> values = provider.apply(hints);

        if (values.isEmpty()) {
            return Optional.empty();
        }

        final ArrayNode examples = jsonNodeFactory.arrayNode(values.size());

        values.stream()
                .map(ValueHint::getValue)
                .map(value -> Objects.toString(value, null))
                .filter(Objects::nonNull)
                .filter(Predicate.not(String::isBlank))
                .sorted()
                .forEachOrdered(examples::add);

        return Optional.of(examples);
    }
}
