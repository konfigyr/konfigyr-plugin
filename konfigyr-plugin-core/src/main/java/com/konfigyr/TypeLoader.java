package com.konfigyr;

import org.jspecify.annotations.NullMarked;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Utility class for loading types from the given class loader.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
public final class TypeLoader {

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
    @SuppressWarnings("unchecked")
    public <T> Class<T> load(String typeName) throws ClassNotFoundException {
        try {
            return (Class<T>) ClassUtils.forName(StringUtils.trimAllWhitespace(typeName), classLoader);
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
}
