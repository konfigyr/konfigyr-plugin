package com.konfigyr;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.lang.reflect.Type;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 06.10.22, Thu
 **/
@Value
@Builder(builderMethodName = "")
public class ResolvedType implements Type, Serializable {
    private static final long serialVersionUID = -7621006446277743496L;

    Class<?> type;

    @Builder.Default
    boolean isEnumeration = false;

    @Builder.Default
    boolean isCollection = false;

    @Builder.Default
    boolean isMap = false;

    @Override
    public String getTypeName() {
        return type.getTypeName();
    }

    public static <T> ResolvedTypeBuilder from(Class<T> type) {
        return new ResolvedTypeBuilder().type(type);
    }

}
