package com.konfigyr;

import lombok.Builder;
import lombok.Value;
import org.springframework.boot.configurationmetadata.Deprecation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 03.10.22, Mon
 **/
@Value
@Builder
public class PropertyMetadata implements Serializable {
    private static final long serialVersionUID = -6239000038357532847L;

    /**
     * Java type name of the value for this property, can't be {@code null}
     */
    @Nonnull
    String type;

    @Nonnull
    PropertyKind kind;

    /**
     * Configuration property group, can be {@code null}
     */
    @Nullable
    String group;

    /**
     * Configuration property source, can be {@code null}
     */
    @Nullable
    String source;

    /**
     * Configuration property name, can't be {@code null}
     */
    @Nonnull
    String name;

    /**
     * Configuration property description, can be {@code null}
     */
    @Nullable
    String description;

    /**
     * Default value of the property, can be {@code null}
     */
    @Nullable
    Object defaultValue;

    /**
     * List of supported values for this property, useful when the property type is an
     * enumeration. Can be empty but it can't be {@code null}.
     */
    @Nonnull
    @Builder.Default
    List<String> hints = Collections.emptyList();

    /**
     * Property that when set to {@code true} means that this property can have multiple values.
     */
    @Builder.Default
    boolean isCollection = false;

    /**
     * Property that when set to {@code true} means that this property is a Map one, meaning
     * that the name of the property can be extended by the value of the map key.
     */
    @Builder.Default
    boolean isMap = false;

    /**
     * Deprecation warnings for this property, can be {@code null}.
     */
    @Nullable
    Deprecation deprecation;

}
