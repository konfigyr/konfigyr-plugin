package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.node.ObjectNode;

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
final class PrimitiveSchemaDefinitionProvider implements SchemaDefinitionProvider {

    static final Set<PrimitiveSchemaDefinition> definitions = new LinkedHashSet<>();

    static {
        Stream.of(
                String.class, Character.class, char.class, CharSequence.class,
                Byte.class, byte.class, InetAddress.class, Class.class
        ).forEach(PrimitiveSchemaDefinitionProvider::registerStringType);

        Stream.of(URI.class, URL.class, File.class, Path.class)
                .forEach(javaType -> registerStringType(javaType, "uri"));

        /* Java time */
        registerStringType(LocalDate.class, "date");
        Stream.of(LocalDateTime.class, ZonedDateTime.class, OffsetDateTime.class, Instant.class, Date.class, Calendar.class)
                .forEach(javaType -> registerStringType(javaType, "date-time"));
        Stream.of(LocalTime.class, OffsetTime.class)
                .forEach(javaType -> registerStringType(javaType, "time"));
        Stream.of(Duration.class, Period.class)
                .forEach(javaType -> registerStringType(javaType, "duration"));

        /* Custom string type formats */
        registerStringType(UUID.class, "uuid");
        registerStringType(Charset.class, "charset");
        registerStringType(ZoneId.class, "time-zone");
        registerStringType(TimeZone.class, "time-zone");
        registerStringType(Locale.class, "language");
        registerStringType(Resource.class, "resource");
        registerStringType(MimeType.class, "mime-type");
        registerStringType(DataSize.class, "data-size");
        registerStringType(Inet4Address.class, "ipv4");
        registerStringType(Inet6Address.class, "ipv6");

        Stream.of(Boolean.class, boolean.class)
                .forEach(PrimitiveSchemaDefinitionProvider::registerBoolean);

        Stream.of(Integer.class, int.class)
                .forEach(javaType -> registerIntegerType(javaType, "int32"));
        Stream.of(Long.class, long.class)
                .forEach(javaType -> registerIntegerType(javaType, "int64"));
        Stream.of(BigInteger.class, Short.class, short.class)
                .forEach(javaType -> registerIntegerType(javaType, null));

        Stream.of(Double.class, double.class)
                .forEach(javaType -> registerNumberType(javaType, "double"));
        Stream.of(Float.class, float.class)
                .forEach(javaType -> registerNumberType(javaType, "float"));
        Stream.of(BigDecimal.class, Number.class)
                .forEach(javaType -> registerNumberType(javaType, null));
    }

    @Override
    public ObjectNode provide(@NonNull ResolvedType type, @NonNull SchemaGenerationContext context) {
        final PrimitiveSchemaDefinition definition = definitionFor(type);

        if (definition == null) {
            return null;
        }

        final ObjectNode schema = context.createSchema(definition.schemaType());

        if (definition.format != null) {
            schema.put("format", definition.format());
        }

        context.extractValueHints().ifPresent(examples -> schema.set("examples", examples));

        return schema;
    }

    private PrimitiveSchemaDefinition definitionFor(ResolvedType type) {
        // first find the definition by the exact type, if none found, try if it is a subtype of the given type
        return definitionFor(candidate -> candidate.equals(type.getErasedType()))
                .or(() -> definitionFor(type::isInstanceOf))
                .orElse(null);
    }

    private Optional<PrimitiveSchemaDefinition> definitionFor(Predicate<Class<?>> predicate) {
        return definitions.stream()
                .filter(definition -> predicate.test(definition.javaType))
                .findFirst();
    }

    static void registerStringType(Class<?> javaType) {
        definitions.add(new PrimitiveSchemaDefinition(javaType, "string"));
    }

    static void registerStringType(Class<?> javaType, String format) {
        definitions.add(new PrimitiveSchemaDefinition(javaType, "string", format));
    }

    static void registerIntegerType(Class<?> javaType, String format) {
        definitions.add(new PrimitiveSchemaDefinition(javaType, "integer", format));
    }

    static void registerNumberType(Class<?> javaType, String format) {
        definitions.add(new PrimitiveSchemaDefinition(javaType, "number", format));
    }

    static void registerBoolean(Class<?> javaType) {
        definitions.add(new PrimitiveSchemaDefinition(javaType, "boolean"));
    }

    record PrimitiveSchemaDefinition(Class<?> javaType, String schemaType, String format) {

        PrimitiveSchemaDefinition(Class<?> javaType, String schemaType) {
            this(javaType, schemaType, null);
        }
    }
}
