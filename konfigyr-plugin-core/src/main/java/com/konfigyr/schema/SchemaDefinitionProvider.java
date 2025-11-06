package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Interface for providing a JSON Schema definition for a given {@link ResolvedType}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public interface SchemaDefinitionProvider {

    /**
     * Attempts to provide a JSON Schema definition for the given {@link ResolvedType}.
     *
     * @param type the resolved type for which a schema definition is requested, never {@literal null}.
     * @param context the schema generation context, never {@literal null}.
     * @return the resolved JSON Schema definition, or {@literal null} if no schema is available for the given type.
     */
    @Nullable
    ObjectNode provide(@NonNull ResolvedType type, @NonNull SchemaGenerationContext context);

}
