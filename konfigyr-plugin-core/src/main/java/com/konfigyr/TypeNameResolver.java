package com.konfigyr;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class used to resolve Java types from Spring configuration property metadata type name.
 * <p>
 * This resolver is using the following to resolve a {@link ResolvedPropertyType}:
 * <ul>
 *     <li>
 *         The {@link JavaParser} to parse the literal type name to extract the type tokens.
 *     </li>
 *     <li>
 *         The {@link TypeResolver} to create a {@link ResolvedPropertyType} from the parsed type tokens.
 *     </li>
 * </ul>
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 **/
final class TypeNameResolver {

    private final ClassLoader loader;
    private final JavaParser parser = new JavaParser();
    private final TypeResolver resolver = new TypeResolver();
    private final Map<String, ResolvedPropertyType> cache = new ConcurrentHashMap<>(128, 0.5f, 4);

    public TypeNameResolver(@NonNull ClassLoader loader) {
        this.loader = loader;
    }

    @Nullable
    ResolvedPropertyType resolve(String className) {
        if (!StringUtils.hasText(className)) {
            return null;
        }

        ResolvedPropertyType resolvedPropertyType = cache.get(className);

        if (resolvedPropertyType == null) {
            try {
                resolvedPropertyType = resolveType(className);
            } catch (ClassNotFoundException e) {
                return null;
            }

            if (resolvedPropertyType != null) {
                cache.put(className, resolvedPropertyType);
            }
        }

        return resolvedPropertyType;
    }

    private synchronized ResolvedPropertyType resolveType(String className) throws ClassNotFoundException {
        final ParseResult<Type> result = parser.parseType(className);

        if (result.isSuccessful()) {
            final Type type = result.getResult().orElseThrow(() -> new IllegalStateException(
                    "Could not extract parsed type result for: " + className
            ));

            final ResolvedType resolvedType = resolve(type);

            if (resolvedType != null) {
                return createResolvedType(resolvedType);
            }
        }

        return null;
    }

    @Nullable
    private ResolvedType resolve(Type type) throws ClassNotFoundException {
        if (type.isArrayType() || type.isPrimitiveType()) {
            return resolver.resolve(loadClass(type.asString()));
        }

        if (type.isWildcardType()) {
            final WildcardType wildcardType = type.asWildcardType();

            if (wildcardType.getExtendedType().isPresent()) {
                return resolve(wildcardType.getExtendedType().get());
            }

            if (wildcardType.getSuperType().isPresent()) {
                return resolve(wildcardType.getSuperType().get());
            }

            return resolver.resolve(Object.class);
        }

        if (type.isClassOrInterfaceType()) {
            final ClassOrInterfaceType resolvedType = type.asClassOrInterfaceType();

            if (resolvedType.getTypeArguments().isPresent()) {
                final List<ResolvedType> parameters = new ArrayList<>();

                for (Type argument : resolvedType.getTypeArguments().get()) {
                    final ResolvedType resolvedArgumentType = resolve(argument);

                    if (resolvedArgumentType != null) {
                        parameters.add(resolvedArgumentType);
                    }
                }

                return resolver.resolve(
                        loadClass(resolvedType.getNameWithScope()),
                        parameters.toArray(new ResolvedType[0])
                );
            }

            return resolver.resolve(loadClass(resolvedType.getNameWithScope()));
        }

        throw new ClassNotFoundException("Could not resolve type for: " + type);
    }

    @NonNull
    private Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            return ClassUtils.forName(StringUtils.trimAllWhitespace(className), loader);
        } catch (LinkageError e) {
            throw new ClassNotFoundException(e.getMessage(), e);
        }
    }

    @NonNull
    static private ResolvedPropertyType createResolvedType(@NonNull ResolvedType type) {
        final List<ResolvedPropertyType> parameters = new ArrayList<>();

        if (!type.getTypeParameters().isEmpty()) {
            for (ResolvedType parameter : type.getTypeParameters()) {
                parameters.add(createResolvedType(parameter));
            }
        }

        return new ResolvedPropertyType(type.getErasedType(), parameters);
    }

}
