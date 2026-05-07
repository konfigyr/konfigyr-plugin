package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.konfigyr.artifactory.JsonSchema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Interface for providing a JSON Schema definition for a given {@link ResolvedType}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
public interface SchemaDefinitionProvider<T extends JsonSchema, B extends JsonSchema.Builder<T, B>> {

    /**
     * Attempts to provide a JSON Schema definition for the given {@link ResolvedType}.
     *
     * @param type the resolved type for which a schema definition is requested, never {@literal null}.
     * @param context the schema generation context, never {@literal null}.
     * @return the resolved JSON Schema definition, or {@literal null} if no schema is available for the given type.
     */
    @Nullable
    B provide(ResolvedType type, SchemaGenerationContext context);

}
