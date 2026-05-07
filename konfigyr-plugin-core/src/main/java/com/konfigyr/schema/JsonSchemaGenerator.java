package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.konfigyr.TypeLoader;
import com.konfigyr.artifactory.JsonSchema;
import org.jspecify.annotations.NullMarked;
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
@NullMarked
public interface JsonSchemaGenerator {

    /**
     * Create a default implementation of the {@link JsonSchemaGenerator} instance.
     *
     * @param typeLoader the type loader to use, never {@literal null}.
     * @param typeResolver the type resolver to use, never {@literal null}.
     * @return the default implementation, never {@literal null}.
     */
    static JsonSchemaGenerator createDefaultGenerator(TypeLoader typeLoader, TypeResolver typeResolver) {
        return new DefaultJsonSchemaGenerator(typeLoader, typeResolver);
    }

    /**
     * Generate a JSON Schema for the given Java type.
     *
     * @param type resolved property type
     * @param metadata configuration property metadata for the given type
     * @return a non-null {@link ObjectNode} containing the JSON schema
     */
    JsonSchema generateSchema(ResolvedType type, ConfigurationMetadataProperty metadata);

}
