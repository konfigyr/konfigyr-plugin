package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.konfigyr.TypeLoader;
import com.konfigyr.artifactory.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Implementation of {@link SchemaDefinitionProvider} that provides schema definitions for primitive types.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
final class PrimitiveSchemaDefinitionProvider<T extends JsonSchema, B extends JsonSchema.Builder<T, B>> implements SchemaDefinitionProvider<T, B> {

    private final Set<PrimitiveSchemaDefinition> definitions;

    PrimitiveSchemaDefinitionProvider(TypeLoader loader) {
        definitions = createDefinitions(loader);
    }

    @Nullable
    @Override
    public B provide(ResolvedType type, SchemaGenerationContext context) {
        final PrimitiveSchemaDefinition definition = definitionFor(type);

        if (definition == null) {
            return null;
        }

        final B schema = context.createSchema(definition.schemaType());

        if (definition.format != null) {
            if (schema instanceof StringSchema.Builder builder) {
                builder.format(definition.format());
            }
            if (schema instanceof IntegerSchema.Builder builder) {
                builder.format(definition.format());
            }
            if (schema instanceof NumberSchema.Builder builder) {
                builder.format(definition.format());
            }
        }

        context.extractValueHints().ifPresent(schema::examples);

        return schema;
    }

    @Nullable
    private PrimitiveSchemaDefinition definitionFor(ResolvedType type) {
        // first find the definition by the exact type, if none found, try if it is a subtype of the given type
        return definitionFor(candidate -> candidate.equals(type.getErasedType()))
                .or(() -> definitionFor(type::isInstanceOf))
                .or(() -> definitionFor(it -> type.getTypeName().equals(it.getCanonicalName())))
                .orElse(null);
    }

    private Optional<PrimitiveSchemaDefinition> definitionFor(Predicate<Class<?>> predicate) {
        return definitions.stream()
                .filter(definition -> predicate.test(definition.javaType))
                .findFirst();
    }

    static Set<PrimitiveSchemaDefinition> createDefinitions(TypeLoader loader) {
        final Set<PrimitiveSchemaDefinition> definitions = new LinkedHashSet<>();

        Stream.of(
                String.class, Character.class, char.class, CharSequence.class,
                Byte.class, byte.class, InetAddress.class, Class.class
        ).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.string(loader, javaType)
        ));

        Stream.of(URI.class, URL.class, File.class, Path.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.string(loader, javaType, "uri")
        ));

        /* Java time */
        definitions.add(PrimitiveSchemaDefinition.string(loader, LocalDate.class, "date"));
        Stream.of(LocalDateTime.class, ZonedDateTime.class, OffsetDateTime.class, Instant.class, Date.class, Calendar.class)
                .forEach(javaType -> definitions.add(
                        PrimitiveSchemaDefinition.string(loader, javaType, "date-time")
                ));
        Stream.of(LocalTime.class, OffsetTime.class)
                .forEach(javaType -> definitions.add(
                        PrimitiveSchemaDefinition.string(loader, javaType, "time")
                ));
        Stream.of(Duration.class, Period.class)
                .forEach(javaType -> definitions.add(
                        PrimitiveSchemaDefinition.string(loader, javaType, "duration")
                ));

        /* Custom string type formats */
        definitions.add(PrimitiveSchemaDefinition.string(loader, UUID.class, "uuid"));
        definitions.add(PrimitiveSchemaDefinition.string(loader, Charset.class, "charset"));
        definitions.add(PrimitiveSchemaDefinition.string(loader, ZoneId.class, "time-zone"));
        definitions.add(PrimitiveSchemaDefinition.string(loader, TimeZone.class, "time-zone"));
        definitions.add(PrimitiveSchemaDefinition.string(loader, Locale.class, "language"));
        definitions.add(PrimitiveSchemaDefinition.string(loader, Inet4Address.class, "ipv4"));
        definitions.add(PrimitiveSchemaDefinition.string(loader, Inet6Address.class, "ipv6"));

        Stream.of(Boolean.class, boolean.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.of(loader, javaType, JsonSchemaType.BOOLEAN)
        ));

        Stream.of(Integer.class, int.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.integer(loader, javaType, "int32")
        ));
        Stream.of(Long.class, long.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.integer(loader, javaType, "int64")
        ));
        Stream.of(BigInteger.class, Short.class, short.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.integer(loader, javaType, null)
        ));

        Stream.of(Double.class, double.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.number(loader, javaType, "double")
        ));
        Stream.of(Float.class, float.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.number(loader, javaType, "float")
        ));
        Stream.of(BigDecimal.class, Number.class).forEach(javaType -> definitions.add(
                PrimitiveSchemaDefinition.number(loader, javaType, null)
        ));
        return Collections.unmodifiableSet(definitions);
    }

    record PrimitiveSchemaDefinition(Class<?> javaType, JsonSchemaType schemaType, @Nullable String format) {

        static PrimitiveSchemaDefinition string(TypeLoader loader, Class<?> javaType) {
            return of(loader, javaType, JsonSchemaType.STRING);
        }

        static PrimitiveSchemaDefinition string(TypeLoader loader, Class<?> javaType, @Nullable String format) {
            return of(loader, javaType, JsonSchemaType.STRING, format);
        }

        static PrimitiveSchemaDefinition integer(TypeLoader loader, Class<?> javaType, @Nullable String format) {
            return of(loader, javaType, JsonSchemaType.INTEGER, format);
        }

        static PrimitiveSchemaDefinition number(TypeLoader loader, Class<?> javaType, @Nullable String format) {
            return of(loader, javaType, JsonSchemaType.NUMBER, format);
        }

        static PrimitiveSchemaDefinition of(TypeLoader loader, Class<?> javaType, JsonSchemaType schemaType) {
            return of(loader, javaType, schemaType, null);
        }

        static PrimitiveSchemaDefinition of(TypeLoader loader, Class<?> javaType, JsonSchemaType schemaType, @Nullable String format) {
            try {
                return new PrimitiveSchemaDefinition(loader.load(javaType), schemaType, format);
            } catch (ClassNotFoundException e) {
                return new PrimitiveSchemaDefinition(javaType, schemaType, format);
            }
        }
    }
}
