package com.konfigyr;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.springframework.util.Assert;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * The resolved type information extracted from the Spring Boot configuration property metadata.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see TypeNameResolver
 * @see org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty
 **/
@Value
public class ResolvedPropertyType implements Serializable {

    @Serial
    private static final long serialVersionUID = -7621006446277743496L;

    static final TypeResolver TYPE_RESOLVER = new TypeResolver();
    static final ResolvedPropertyType STRING_TYPE = new ResolvedPropertyType(String.class);

    /**
     * The resolved type information for this property.
     */
    ResolvedType type;

    /**
     * The list of type parameters for generic types. Empty list if the type is not generic.
     */
    List<ResolvedPropertyType> parameters;

    /**
     * Creates a new {@link ResolvedPropertyType} for a non-generic type.
     *
     * @param type the class type, cannot be {@literal null}.
     */
    public ResolvedPropertyType(Class<?> type) {
        this(type, Collections.emptyList());
    }

    /**
     * Creates a new {@link ResolvedPropertyType} for a potentially generic type.
     *
     * @param type       the class type, cannot be {@literal null}.
     * @param parameters the list of generic type parameters, cannot be {@literal null}.
     */
    public ResolvedPropertyType(Class<?> type, List<ResolvedPropertyType> parameters) {
        Assert.notNull(type, "Type must not be null");
        Assert.notNull(parameters, "Type parameters must not be null");

        this.type = resolve(type, parameters);
        this.parameters = Collections.unmodifiableList(parameters);
    }

    /**
     * Returns the fully qualified type name of this resolved property type.
     *
     * @return the type name, never {@literal null}.
     */
    @NonNull
    public String getTypeName() {
        return type.getTypeName();
    }

    /**
     * Resolves the type with its generic parameters using the {@link TypeResolver}.
     *
     * @param type       the class type to resolve, cannot be {@literal null}.
     * @param parameters the list of generic type parameters, cannot be {@literal null}.
     * @return the resolved type, never {@literal null}.
     */
    private static ResolvedType resolve(Class<?> type, List<ResolvedPropertyType> parameters) {
        return TYPE_RESOLVER.resolve(type, parameters.stream()
                        .map(ResolvedPropertyType::getType)
                        .toArray(Type[]::new)
        );
    }

}
