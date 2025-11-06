package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import tools.jackson.databind.node.ObjectNode;

/**
 * Simple JSON Schema generator contract.
 * <p>
 * Implementations should use Java reflection to produce a JSON Schema (Draft 2020-12 flavored)
 * represented as a Jackson {@link ObjectNode}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public interface JsonSchemaGenerator {

    /**
     * Create a default implementation of the {@link JsonSchemaGenerator} instance.
     *
     * @return the default implementation, never {@literal null}.
     */
    @NonNull
    static JsonSchemaGenerator createDefaultGenerator() {
        return new DefaultJsonSchemaGenerator();
    }

    /**
     * Generate a JSON Schema for the given Java type.
     *
     * @param type resolved property type
     * @param metadata configuration property metadata for the given type
     * @return a non-null {@link ObjectNode} containing the JSON schema
     */
    @NonNull
    ObjectNode generateSchema(@NonNull ResolvedType type, @NonNull ConfigurationMetadataProperty metadata);

    /**
     * Convenience method to generate schema as a compact JSON string.
     *
     * @param type resolved property type
     * @param metadata configuration property metadata for the given type
     * @return a non-null {@link String} containing the JSON schema
     */
    @NonNull
    default String generateSchemaAsString(@NonNull ResolvedType type, @NonNull ConfigurationMetadataProperty metadata) {
        return generateSchema(type, metadata).toString();
    }
}
