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

    ResolvedType type;
    List<ResolvedPropertyType> parameters;

    public ResolvedPropertyType(Class<?> type) {
        this(type, Collections.emptyList());
    }

    public ResolvedPropertyType(Class<?> type, List<ResolvedPropertyType> parameters) {
        Assert.notNull(type, "Type must not be null");
        Assert.notNull(parameters, "Type parameters must not be null");

        this.type = resolve(type, parameters);
        this.parameters = Collections.unmodifiableList(parameters);
    }

    @NonNull
    public String getTypeName() {
        return type.getTypeName();
    }

    private static ResolvedType resolve(Class<?> type, List<ResolvedPropertyType> parameters) {
        return TYPE_RESOLVER.resolve(type, parameters.stream()
                        .map(ResolvedPropertyType::getType)
                        .toArray(Type[]::new)
        );
    }

}
