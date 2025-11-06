package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.types.ResolvedObjectType;
import com.fasterxml.classmate.util.ClassKey;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.util.Assert;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

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

    private final TypeResolver resolver = new TypeResolver();
    private final Set<ResolvedType> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

    private final List<SchemaDefinitionProvider> providers = List.of(
            new PrimitiveSchemaDefinitionProvider(),
            new EnumSchemaDefinitionProvider()
    );

    @NonNull
    @Override
    public ObjectNode generateSchema(@NonNull ResolvedType type, @NonNull ConfigurationMetadataProperty metadata) {
        Assert.notNull(type, "Type must not be null");
        Assert.notNull(metadata, "Configuration metadata must not be null");

        return generateSchema(type, new SchemaGenerationContext(metadata, JsonNodeFactory.instance, resolver));
    }

    private ObjectNode generateSchema(@NonNull ResolvedType type, @NonNull SchemaGenerationContext context) {
        ObjectNode schema = null;
        Iterator<SchemaDefinitionProvider> iterator = providers.iterator();

        while (iterator.hasNext() && schema == null) {
            schema = iterator.next().provide(type, context);
        }

        if (schema != null) {
            return schema;
        }

        // Prevent infinite recursion on self-referential types, just return a simple
        // JSON Schema with a string type and no properties.
        if (visiting.contains(type)) {
            return context.createSchema("string");
        }

        visiting.add(type);

        try {
            if (type.isArray()) {
                return context.createSchema("array").set("items", generateSchema(
                        type.getArrayElementType(), context
                ));
            }

            if (type.isInstanceOf(Optional.class)) {
                final ResolvedType itemType = type.getTypeParameters().isEmpty() ?
                        new ResolvedObjectType(String.class, null, null, Collections.emptyList())
                        : type.getTypeParameters().get(0);

                return generateSchema(itemType, context);
            }

            if (type.isInstanceOf(Collection.class)) {
                final ResolvedType itemType = type.getTypeParameters().isEmpty() ?
                        new ResolvedObjectType(String.class, null, null, Collections.emptyList())
                        : type.getTypeParameters().get(0);

                return context.createSchema("array").set("items", generateSchema(
                        itemType, context
                ));
            }

            if (type.isInstanceOf(Map.class)) {
                final ResolvedType keyType = type.getTypeParameters().isEmpty() ?
                        new ResolvedObjectType(String.class, null, null, Collections.emptyList())
                        : type.getTypeParameters().get(0);

                final ResolvedType valueType = type.getTypeParameters().isEmpty() ?
                        new ResolvedObjectType(String.class, null, null, Collections.emptyList())
                        : type.getTypeParameters().get(1);

                final ObjectNode propertyNames = generateSchema(keyType, context);
                context.extractKeyHints().ifPresent(examples -> propertyNames.set("examples", examples));

                final ObjectNode additionalProperties = generateSchema(valueType, context);
                context.extractValueHints().ifPresent(examples -> additionalProperties.set("examples", examples));

                final ObjectNode node = context.createSchema("object");
                node.set("propertyNames", propertyNames);
                node.set("additionalProperties", additionalProperties);
                return node;
            }

            return objectFromPojo(type, context);
        } finally {
            visiting.remove(type);
        }
    }

    private ObjectNode objectFromPojo(ResolvedType type, SchemaGenerationContext context) {
        final ObjectNode node = context.createSchema("object");
        final ObjectNode properties = context.getNodeFactory().objectNode();
        final ArrayNode required = context.getNodeFactory().arrayNode();

        for (PropertyCandidate candidate : collectPropertyCandidates(type)) {
            if (candidate.isStatic() || candidate.isTransient()) {
                continue;
            }

            final ObjectNode schema = generateSchema(
                    context.resolveType(candidate.getType()),
                    context
            );

            if (candidate.isDeprecated()) {
                schema.put("deprecated", true);
            }

            properties.set(candidate.getName(), schema);

            if (candidate.isRequired()) {
                required.add(candidate.getName());
            }
        }
        node.set("properties", properties);
        if (!required.isEmpty()) {
            node.set("required", required);
        }
        return node;
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
                // ignore the field if we can't access it
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
