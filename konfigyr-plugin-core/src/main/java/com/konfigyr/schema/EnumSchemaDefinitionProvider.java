package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.members.RawMember;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Implementation of {@link SchemaDefinitionProvider} that produces JSON Schema for enum types.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public class EnumSchemaDefinitionProvider implements SchemaDefinitionProvider {

    @Override
    public ObjectNode provide(@NonNull ResolvedType type, @NonNull SchemaGenerationContext context) {
        if (type.isInstanceOf(Enum.class)) {
            final ObjectNode schema = context.createSchema("string");

            Stream<String> values = enumConstantsFor(type);

            if (values == null) {
                values = enumFieldsFor(type);
            }

            if (values == null) {
                values = Stream.empty();
            }

            final ArrayNode array = context.getNodeFactory().arrayNode();
            values.sorted().forEach(array::add);

            return schema.set("enum", array);
        }

        return null;
    }

    private Stream<String> enumConstantsFor(ResolvedType type) {
        try {
            return Arrays.stream(type.getErasedType().getEnumConstants())
                    .map(Enum.class::cast)
                    .map(Enum::name);
        } catch (NoClassDefFoundError | Exception ex) {
            return null;
        }
    }

    private Stream<String> enumFieldsFor(ResolvedType type) {
        try {
            return type.getStaticFields().stream()
                    .filter(member -> member.getRawMember().isEnumConstant())
                    .map(RawMember::getName);
        } catch (NoClassDefFoundError | Exception ex2) {
            return null;
        }
    }
}
