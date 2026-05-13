package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.lang.reflect.Array;
import java.util.Map;

/**
 * Utility class for loading types from the given class loader.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
public final class TypeLoader {

    private static final Map<String, Class<?>> PRIMITIVE_TYPES = Map.of(
            "boolean", boolean.class,
            "byte", byte.class,
            "char", char.class,
            "short", short.class,
            "int", int.class,
            "long", long.class,
            "float", float.class,
            "double", double.class,
            "void", void.class
    );

    private final ClassLoader classLoader;

    /**
     * Creates a new {@link TypeLoader} instance using the current {@link ClassLoader} instance.
     */
    public TypeLoader() {
        this(TypeLoader.class.getClassLoader());
    }

    /**
     * Creates a new {@link TypeLoader} instance using the given {@link ClassLoader} instance.
     *
     * @param classLoader the class loader to use, cannot be {@literal null}.
     */
    public TypeLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Attempts to load the given type name using the given class loader.
     *
     * @param typeName the type name to be loaded, cannot be {@literal null} or empty.
     * @return the loaded type, never {@literal null}.
     * @param <T> the actual type to be loaded.
     * @throws ClassNotFoundException if the type cannot be found by the class loader.
     */
    public <T> Class<T> load(String typeName) throws ClassNotFoundException {
        try {
            return forName(typeName.strip(), classLoader);
        } catch (LinkageError e) {
            throw new ClassNotFoundException(e.getMessage(), e);
        }
    }

    /**
     * Attempts to load the given type using the given class loader if it is not already loaded.
     *
     * @param type the type to be loaded, cannot be {@literal null}.
     * @return the loaded type, never {@literal null}.
     * @param <T> the actual type to be loaded.
     * @throws ClassNotFoundException if the type cannot be found by the class loader.
     */
    public <T> Class<T> load(Class<T> type) throws ClassNotFoundException {
        if (classLoader.equals(type.getClassLoader())) {
            return type;
        }
        return load(type.getCanonicalName());
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> forName(String name, ClassLoader classLoader) throws ClassNotFoundException {
        // primitive types: int, long, boolean, void, etc.
        final Class<?> primitive = PRIMITIVE_TYPES.get(name);
        if (primitive != null) {
            return (Class<T>) primitive;
        }

        // array types, recursive so int[][] works too
        if (name.endsWith("[]")) {
            final Class<?> element = forName(name.substring(0, name.length() - 2), classLoader);
            return (Class<T>) Array.newInstance(element, 0).getClass();
        }

        // regular class first; if not found, try inner-class dot to dollar conversion
        // e.g. com.example.Outer.Inner → com.example.Outer$Inner
        try {
            return (Class<T>) Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException ex) {
            final int lastDot = name.lastIndexOf('.');
            if (lastDot > 0) {
                return forName(name.substring(0, lastDot) + '$' + name.substring(lastDot + 1), classLoader);
            }
            throw ex;
        }
    }
}
