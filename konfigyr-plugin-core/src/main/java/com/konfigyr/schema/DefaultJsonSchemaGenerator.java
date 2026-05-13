package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.util.ClassKey;
import com.konfigyr.TypeLoader;
import com.konfigyr.artifactory.ArraySchema;
import com.konfigyr.artifactory.JsonSchema;
import com.konfigyr.artifactory.JsonSchemaType;
import com.konfigyr.artifactory.ObjectSchema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;

import java.lang.reflect.Field;
import java.util.*;

/**
 * A small, dependency-free (besides Jackson) JSON Schema generator that uses Java reflection.
 * <p>
 * This is intentionally basic: it covers primitives, wrappers, strings, numbers, booleans,
 * enums, arrays, collections, maps, and simple POJOs (by inspecting their declared fields).
 * It emits Draft 2020-12 compatible keywords (type, properties, items, additionalProperties, required, enum, format).
 */
final class DefaultJsonSchemaGenerator implements JsonSchemaGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultJsonSchemaGenerator.class);

    private final TypeLoader typeLoader;
    private final TypeResolver typeResolver;
    private final List<SchemaDefinitionProvider<?, ?>> providers;

    DefaultJsonSchemaGenerator(TypeLoader typeLoader, TypeResolver typeResolver) {
        this.typeLoader = typeLoader;
        this.typeResolver = typeResolver;
        this.providers = List.of(
                new PrimitiveSchemaDefinitionProvider<>(typeLoader),
                new SpringSchemaDefinitionProvider<>(typeLoader),
                new EnumSchemaDefinitionProvider()
        );
    }

    @NonNull
    @Override
    public JsonSchema generateSchema(@NonNull ResolvedType type, @NonNull ConfigurationMetadataProperty metadata) {
        return generateSchema(type, new SchemaGenerationContext(metadata, typeResolver, typeLoader), new HashSet<>());
    }

    private JsonSchema generateSchema(
            @NonNull ResolvedType type,
            @NonNull SchemaGenerationContext context,
            @NonNull Set<Class<?>> visiting
    ) {
        JsonSchema.Builder<?, ?> builder = generateSchemaBuilder(type, context, visiting);

        if (builder == null) {
            logger.warn("Could not generate schema for type '{}', using the default 'string' JSON Schema", type);
            builder = context.createSchema(JsonSchemaType.STRING);
        }

        return builder.build();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private <T extends JsonSchema, B extends JsonSchema.Builder<T, B>> B generateSchemaBuilder(
            @NonNull ResolvedType type,
            @NonNull SchemaGenerationContext context,
            @NonNull Set<Class<?>> visiting
    ) {
        JsonSchema.Builder<?, ?> schema = null;
        Iterator<SchemaDefinitionProvider<?, ?>> iterator = providers.iterator();

        while (iterator.hasNext() && schema == null) {
            schema = iterator.next().provide(type, context);
        }

        if (schema != null) {
            return (B) schema;
        }

        // Prevent infinite recursion on self-referential types, just return a simple
        // JSON Schema with a string type and no properties.
        if (!visiting.add(type.getErasedType())) {
            return context.createSchema(JsonSchemaType.STRING);
        }

        try {
            if (type.isArray()) {
                final ArraySchema.Builder builder = context.createSchema(JsonSchemaType.ARRAY);

                return (B) builder.items(generateSchema(
                        type.getArrayElementType(), context, visiting
                ));
            }

            if (type.isInstanceOf(Optional.class)) {
                final ResolvedType itemType = type.getTypeParameters().isEmpty() ?
                        context.resolveType(String.class) : type.getTypeParameters().getFirst();

                return generateSchemaBuilder(itemType, context, visiting);
            }

            if (type.isInstanceOf(Collection.class)) {
                final ResolvedType itemType = type.getTypeParameters().isEmpty() ?
                        context.resolveType(String.class) : type.getTypeParameters().getFirst();

                final ArraySchema.Builder builder = context.createSchema(JsonSchemaType.ARRAY);
                return (B) builder.items(generateSchema(itemType, context, visiting));
            }

            if (type.isInstanceOf(Map.class)) {
                final ResolvedType keyType = type.getTypeParameters().isEmpty() ?
                        context.resolveType(String.class) : type.getTypeParameters().get(0);

                final ResolvedType valueType = type.getTypeParameters().isEmpty() ?
                        context.resolveType(String.class) : type.getTypeParameters().get(1);

                final ObjectSchema.Builder builder = context.createSchema(JsonSchemaType.OBJECT);
                final JsonSchema.Builder<?, ?> propertyNames = generateSchemaBuilder(keyType, context, visiting);
                final JsonSchema.Builder<?, ?> additionalProperties = generateSchemaBuilder(valueType, context, visiting);

                if (propertyNames != null) {
                    context.extractKeyHints().ifPresent(propertyNames::examples);
                    builder.propertyNames(propertyNames.build());
                }

                if (additionalProperties != null) {
                    context.extractValueHints().ifPresent(additionalProperties::examples);
                    builder.additionalProperties(additionalProperties.build());
                }

                return (B) builder;
            }

            return (B) objectFromPojo(type, context, visiting);
        } finally {
            visiting.remove(type.getErasedType());
        }
    }

    private ObjectSchema.Builder objectFromPojo(
            ResolvedType type,
            SchemaGenerationContext context,
            Set<Class<?>> visiting
    ) {
        final ObjectSchema.Builder builder = context.createSchema(JsonSchemaType.OBJECT);

        for (PropertyCandidate candidate : collectPropertyCandidates(type)) {
            if (candidate.isStatic() || candidate.isTransient()) {
                continue;
            }

            final JsonSchema.Builder<?, ?> schema = generateSchemaBuilder(
                    context.resolveType(candidate.getType()),
                    context,
                    visiting
            );

            if (schema == null) {
                continue;
            }

            if (candidate.isDeprecated()) {
                schema.deprecated(candidate.isDeprecated());
            }

            builder.property(candidate.getName(), schema.build());

            if (candidate.isRequired()) {
                builder.required(candidate.getName());
            }
        }

        return builder;
    }

    private List<PropertyCandidate> collectPropertyCandidates(ResolvedType type) {
        final List<ResolvedType> types;
        final HashSet<ClassKey> seen = new HashSet<>();

        // if the type is an object, no need to gather types, just use it
        if (type.getErasedType() == Object.class) {
            types = Collections.singletonList(type);
        } else {
            types = new ArrayList<>();
            gatherTypes(type, seen, types);
        }

        final List<PropertyCandidate> candidates = new ArrayList<>();

        for (var resolved : types) {
            try {
                for (Field f : resolved.getErasedType().getDeclaredFields()) {
                    candidates.add(new PropertyCandidate(type, f));
                }
            } catch (ReflectiveOperationException | NoClassDefFoundError e) {
                logger.debug("Failed to collect property candidates for type '{}': {}", resolved, e.getMessage(), e);
            }
        }

        // sort the candidates...
        candidates.sort(PropertyCandidate::compareTo);

        return candidates;
    }

    private static void gatherTypes(ResolvedType type, Set<ClassKey> seen, List<ResolvedType> resolved) {
        // may get called with null if no parent type
        if (type == null) {
            return;
        }
        final Class<?> raw = type.getErasedType();
        // Also, don't include Object.class
        if (raw == Object.class) {
            return;
        }
        // Finally, only include the first instance of an interface, so:
        final ClassKey key = new ClassKey(type.getErasedType());
        if (!seen.add(key)) {
            return;
        }
        // If all good so far, append
        resolved.add(type);
        /* and check supertypes; starting with interfaces. Why interfaces?
         * So that "highest" interfaces get priority; otherwise we'd recurse
         *  the super-class stack and actually start with the bottom. Usually makes
         * little difference, but in cases where it does, this seems like the
         * correct order.
         */
        for (ResolvedType t : type.getImplementedInterfaces()) {
            gatherTypes(t, seen, resolved);
        }
        // and then superclass
        gatherTypes(type.getParentClass(), seen, resolved);
    }

}
