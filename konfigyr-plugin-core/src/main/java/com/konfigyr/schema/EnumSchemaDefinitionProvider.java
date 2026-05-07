package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.members.RawMember;
import com.konfigyr.artifactory.JsonSchemaType;
import com.konfigyr.artifactory.StringSchema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Implementation of {@link SchemaDefinitionProvider} that produces JSON Schema for enum types.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
class EnumSchemaDefinitionProvider implements SchemaDefinitionProvider<StringSchema, StringSchema.Builder> {

    @Override
    public StringSchema.@Nullable Builder provide(ResolvedType type, SchemaGenerationContext context) {
        if (type.getErasedType().isEnum()) {
            final StringSchema.Builder schema = context.createSchema(JsonSchemaType.STRING);

            Stream<String> values = enumConstantsFor(type);

            if (values == null) {
                values = enumFieldsFor(type);
            }

            if (values != null) {
                values.forEach(schema::enumeration);
            }

            return schema;
        }

        return null;
    }

    @Nullable
    private Stream<String> enumConstantsFor(ResolvedType type) {
        try {
            return Arrays.stream(type.getErasedType().getEnumConstants())
                    .map(Enum.class::cast)
                    .map(Enum::name);
        } catch (NoClassDefFoundError | Exception ex) {
            return null;
        }
    }

    @Nullable
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
