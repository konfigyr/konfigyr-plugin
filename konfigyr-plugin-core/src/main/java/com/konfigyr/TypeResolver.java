package com.konfigyr;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class used to resolve Java types from Spring configuration property metadata. This resolver would return
 * a {@link ResolvableType} that tries its best to figure which property value type is defined.
 *
 * @author : vladimir.spasic@ebf.com
 * @since : 06.10.22, Thu
 **/
@RequiredArgsConstructor
final class TypeResolver {

    private static final Map<String, ResolvedType> RESOLVED_TYPE_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, ResolvedType> OVERRIDES = new ConcurrentHashMap<>();

    static {
        OVERRIDES.put(Properties.class, ResolvedType.from(String.class).isMap(true).build());
    }

    private final ClassLoader loader;

    @Nullable
    public synchronized ResolvedType resolve(String className) {
        Assert.hasText(className, "Class type name can not be blank");

        ResolvedType resolvedType = RESOLVED_TYPE_CACHE.get(className);

        if (resolvedType == null) {
            try {
                resolvedType = resolveType(className);
            } catch (ClassNotFoundException e) {
                return null;
            }

            RESOLVED_TYPE_CACHE.put(className, resolvedType);
        }

        return resolvedType;
    }

    private synchronized ResolvedType resolveType(String className) throws ClassNotFoundException {
        String normalizedClass = className;

        if (className.contains("<")) {
            normalizedClass = className.substring(0, className.indexOf("<"));
        }

        final Class<?> type = loadClass(normalizedClass);

        if (OVERRIDES.containsKey(type)) {
            return OVERRIDES.get(type);
        }

        return ResolvedType.from(resolvePropertyValueType(className, type))
                .isEnumeration(type.isEnum())
                .isMap(ClassUtils.isAssignable(Map.class, type))
                .isCollection(type.isArray() || ClassUtils.isAssignable(Collection.class, type))
                .build();
    }

    @Nonnull
    private Class<?> resolvePropertyValueType(String className, Class<?> type) throws ClassNotFoundException {
        if (type.isArray()) {
            return type.getComponentType();
        }

        if (ClassUtils.isAssignable(Collection.class, type)) {
            return loadClass(
                    className.substring(className.indexOf("<") + 1, className.indexOf(">"))
            );
        }

        if (ClassUtils.isAssignable(Map.class, type)) {
            return loadClass(
                    className.substring(className.indexOf(",") + 1, className.length() - 1)
            );
        }

        return type;
    }

    private Class<?> loadClass(String className) throws ClassNotFoundException {
        final Class<?> type;

        try {
            type = ClassUtils.forName(className, loader);
        } catch (LinkageError e) {
            throw new ClassNotFoundException(e.getMessage(), e);
        }

        return ResolvableType.forClass(type).resolve(Object.class);
    }

}
