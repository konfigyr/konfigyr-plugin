package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.konfigyr.TypeLoader;
import com.konfigyr.artifactory.JsonSchema;
import com.konfigyr.artifactory.StringSchema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Implementation of {@link SchemaDefinitionProvider} that provides schema definitions for Spring
 * specific types, like {@code Resource}, {@code DataSize} or {@code MimeType}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
class SpringSchemaDefinitionProvider<T extends JsonSchema, B extends JsonSchema.Builder<T, B>> implements SchemaDefinitionProvider<T, B> {

    private final Map<Class<?>, JsonSchema.Builder<?, ?>> schemas;

    SpringSchemaDefinitionProvider(TypeLoader typeLoader) {
        schemas = new LinkedHashMap<>();
        register(schemas, typeLoader, "org.springframework.core.io.Resource", () -> StringSchema.builder().format("resource"));
        register(schemas, typeLoader, "org.springframework.util.MimeType", () -> StringSchema.builder().format("mime-type"));
        register(schemas, typeLoader, "org.springframework.util.unit.DataSize", () -> StringSchema.builder().format("data-size"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable B provide(ResolvedType type, SchemaGenerationContext context) {
        for (Map.Entry<Class<?>, JsonSchema.Builder<?, ?>> entry : schemas.entrySet()) {
            if (type.isInstanceOf(entry.getKey())) {
                return (B) entry.getValue();
            }
        }
        return null;
    }

    static void register(
            Map<Class<?>, JsonSchema.Builder<?, ?>> builders,
            TypeLoader typeLoader,
            String typeName,
            Supplier<JsonSchema.Builder<?, ?>> supplier
    ) {
        final Class<?> type;

        try {
            type = typeLoader.load(typeName);
        } catch (ClassNotFoundException ignore) {
            return;
        }

        builders.put(type, supplier.get());
    }
}
