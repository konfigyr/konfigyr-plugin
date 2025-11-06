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

@EqualsAndHashCode
final class PropertyCandidate implements Comparable<PropertyCandidate> {

    @NonNull
    private final Field field;

    @Nullable
    private final Method getter;

    PropertyCandidate(@NonNull ResolvedType type, @NonNull Field field) throws ReflectiveOperationException {
        this.field = field;
        this.getter = findGetter(type.getErasedType(), field);
    }

    @NonNull
    String getName() {
        return field.getName();
    }

    @NonNull
    Class<?> getType() {
        return field.getType();
    }

    boolean isTransient() {
        return Modifier.isTransient(field.getModifiers());
    }

    boolean isStatic() {
        return Modifier.isStatic(field.getModifiers());
    }

    boolean isRequired() {
        return getType().isPrimitive();
    }

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
