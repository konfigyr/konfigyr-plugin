package com.konfigyr.schema;

import com.fasterxml.classmate.ResolvedType;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * A single declared field of a POJO being turned into an {@code object} JSON Schema by
 * {@link DefaultJsonSchemaGenerator}, paired with its JavaBeans getter (if one can be found), used
 * to read annotations that may be declared on the getter rather than the field itself (e.g.
 * {@link Deprecated}).
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@EqualsAndHashCode
final class PropertyCandidate implements Comparable<PropertyCandidate> {

    @NonNull
    private final Field field;

    @Nullable
    private final Method getter;

    /**
     * Creates a new {@link PropertyCandidate} for the given field, locating its JavaBeans getter, if
     * any, via {@link #findGetter(Class, Field)}.
     *
     * @param type the resolved type the field is declared on, cannot be {@literal null}.
     * @param field the field this candidate wraps, cannot be {@literal null}.
     * @throws ReflectiveOperationException if the getter lookup fails.
     */
    PropertyCandidate(@NonNull ResolvedType type, @NonNull Field field) throws ReflectiveOperationException {
        this.field = field;
        this.getter = findGetter(type.getErasedType(), field);
    }

    /**
     * The field's name, used as the JSON Schema property name.
     *
     * @return the field name, never {@literal null}.
     */
    @NonNull
    String getName() {
        return field.getName();
    }

    /**
     * The field's declared type, used to generate its JSON Schema.
     *
     * @return the field type, never {@literal null}.
     */
    @NonNull
    Class<?> getType() {
        return field.getType();
    }

    /**
     * Checks whether this field is declared {@code transient}, excluding it from the generated schema.
     *
     * @return {@literal true} if the field is transient.
     */
    boolean isTransient() {
        return Modifier.isTransient(field.getModifiers());
    }

    /**
     * Checks whether this field is declared {@code static}, excluding it from the generated schema.
     *
     * @return {@literal true} if the field is static.
     */
    boolean isStatic() {
        return Modifier.isStatic(field.getModifiers());
    }

    /**
     * Checks whether this field should be marked as a required property, currently always the case
     * for primitive types, which cannot themselves represent absence.
     *
     * @return {@literal true} if the field is required.
     */
    boolean isRequired() {
        return getType().isPrimitive();
    }

    /**
     * Checks whether this field, or its {@link #findGetter(Class, Field) getter}, is annotated
     * {@link Deprecated}.
     *
     * @return {@literal true} if the field is deprecated.
     */
    boolean isDeprecated() {
        return annotationFor(Deprecated.class) != null;
    }

    @Override
    public int compareTo(@NonNull PropertyCandidate o) {
        return getName().compareTo(o.getName());
    }

    private <A extends Annotation> A annotationFor(Class<A> annotationType) {
        A annotation = field.getAnnotation(annotationType);

        if (annotation == null && getter != null) {
            annotation = getter.getAnnotation(annotationType);
        }

        return annotation;
    }

    /**
     * Looks up the JavaBeans-style getter for the given field, trying every plausible getter name in
     * turn, or the field's own name directly for a {@link Class#isRecord() record} component.
     *
     * @param type the type the field is declared on, cannot be {@literal null}.
     * @param field the field to find a getter for, cannot be {@literal null}.
     * @return the found getter method, or {@literal null} if none of the candidate names match a
     *         public method.
     * @throws ReflectiveOperationException if the record component accessor cannot be found.
     */
    static Method findGetter(Class<?> type, Field field) throws ReflectiveOperationException {
        // "non-prefix" naming convention of Java 14 java.lang.Record types
        if (type.isRecord()) {
            return type.getMethod(field.getName());
        }

        final Iterator<String> candidates = resolvePossibleGetterNames(field.getName())
                .iterator();

        while (candidates.hasNext()) {
            final Method method;

            try {
                method = type.getDeclaredMethod(candidates.next());
            } catch (NoSuchMethodException | NoClassDefFoundError e) {
                // proceed to the next candidate...
                continue;
            }

            if (Modifier.isPublic(method.getModifiers())) {
                return method;
            }
        }

        return null;
    }

    private static Stream<String> resolvePossibleGetterNames(String fieldName) {
        final Stream.Builder<String> builder = Stream.builder();

        // for a field like "xIndex" also consider "getxIndex()" as getter method (according to JavaBeans specification)
        if (fieldName.length() > 1 && Character.isUpperCase(fieldName.charAt(1))) {
            builder.add("get" + fieldName);
            builder.add("is" + fieldName);
        }

        // common naming convention: capitalize the first character and leave the rest as-is
        final String capitalisedFieldName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        builder.add("get" + capitalisedFieldName);
        builder.add("is" + capitalisedFieldName);

        // for a field like "isBool" also consider "isBool()" as potential getter method
        boolean fieldNameStartsWithIs = fieldName.startsWith("is") && fieldName.length() > 2 && Character.isUpperCase(fieldName.charAt(2));
        if (fieldNameStartsWithIs) {
            builder.add(fieldName);
        }

        return builder.build();
    }

}
