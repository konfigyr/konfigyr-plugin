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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // the type used to represent an unknown type in the cache
    static final ResolvedPropertyType UNKNOWN_TYPE = new ResolvedPropertyType(TypeNameResolver.class);

    static final Logger logger = LoggerFactory.getLogger(TypeNameResolver.class);

    private final TypeLoader loader;
    private final TypeResolver resolver;
    private final JavaParser parser = new JavaParser();
    private final Map<String, ResolvedPropertyType> cache = new ConcurrentHashMap<>(128, 0.5f, 4);

    TypeNameResolver(@NonNull ClassLoader loader) {
        this(new TypeLoader(loader), new TypeResolver());
    }

    TypeNameResolver(@NonNull TypeLoader loader, @NonNull TypeResolver resolver) {
        this.loader = loader;
        this.resolver = resolver;
    }

    @Nullable
    synchronized ResolvedPropertyType resolve(String className) {
        if (!StringUtils.hasText(className)) {
            return null;
        }

        ResolvedPropertyType resolvedPropertyType = cache.get(className);

        if (resolvedPropertyType == null) {
            try {
                resolvedPropertyType = resolveType(className);
            } catch (ClassNotFoundException ex) {
                logger.debug("Could not resolve actual Java type for '{}' type name", className, ex);
            }

            if (resolvedPropertyType == null) {
                resolvedPropertyType = UNKNOWN_TYPE;
            }

            cache.put(className, resolvedPropertyType);
        }

        if (resolvedPropertyType == UNKNOWN_TYPE) {
            return null;
        }

        return resolvedPropertyType;
    }

    private ResolvedPropertyType resolveType(String className) throws ClassNotFoundException {
        final ParseResult<Type> result = parser.parseType(className);

        if (result.isSuccessful()) {
            final Type type = result.getResult().orElseThrow(() -> new IllegalStateException(
                    "Could not extract parsed type result for: " + className
            ));

            final ResolvedType resolvedType = resolve(type);

            if (resolvedType != null) {
                final ResolvedPropertyType resolvedPropertyType = createResolvedType(resolvedType);
                logger.debug("Successfully resolved type '{}' to: {}", className, resolvedPropertyType);
                return resolvedPropertyType;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Could not resolve type '{}' to a Java type. {}", className, result);
        }

        return null;
    }

    @Nullable
    private ResolvedType resolve(Type type) throws ClassNotFoundException {
        if (type.isArrayType() || type.isPrimitiveType()) {
            return resolver.resolve(loader.load(type.asString()));
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
                        loader.load(resolvedType.getNameWithScope()),
                        parameters.toArray(new ResolvedType[0])
                );
            }

            return resolver.resolve(loader.load(resolvedType.getNameWithScope()));
        }

        throw new ClassNotFoundException("Could not resolve type for: " + type);
    }

    @NonNull
    static ResolvedPropertyType createResolvedType(@NonNull ResolvedType type) {
        final List<ResolvedPropertyType> parameters = new ArrayList<>();

        if (!type.getTypeParameters().isEmpty()) {
            for (ResolvedType parameter : type.getTypeParameters()) {
                parameters.add(createResolvedType(parameter));
            }
        }

        return new ResolvedPropertyType(type.getErasedType(), parameters);
    }

}
