package com.konfigyr;

import org.apache.hc.core5.util.Asserts;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.unit.DataSize;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.*;
import java.util.*;

/**
 * Enumeration that defines a list of supported property types that is supported by the Konfigyr Config Server.
 *
 * @author : vladimir.spasic@ebf.com
 * @since : 03.10.22, Mon
 **/
public enum PropertyKind {

    STRING(String.class, Character.class, File.class, Class.class),
    NUMBER(Number.class),
    BOOLEAN(Boolean.class),
    DURATION(Duration.class),
    TIME_ZONE(TimeZone.class),
    DATE(LocalDate.class),
    DATE_TIME(Date.class, Instant.class, LocalDateTime.class, ZonedDateTime.class, OffsetDateTime.class),
    URI(java.net.URI.class, URL.class),
    INTERNET_ADDRESS(InetAddress.class),
    DATA_SIZE(DataSize.class),
    MIME_TYPE(MimeType.class),
    RESOURCE(Resource.class),
    CHARSET(Charset.class),
    LOCALE(Locale.class);

    final Collection<Class<?>> types;

    PropertyKind(Class<?>... types) {
        this.types = Arrays.asList(types);
    }

    public static PropertyKind from(Class<?> candidate) {
        Asserts.notNull(candidate, "Candidate property java type");

        // for object types simply return a String kind always
        if (candidate.getTypeName().equals(Object.class.getTypeName())) {
            return STRING;
        }

        for (PropertyKind value : values()) {
            if (isTypeOf(value.types, candidate)) {
                return value;
            }
        }

        return STRING;
    }

    private static boolean isTypeOf(Collection<Class<?>> types, Class<?> candidate) {
        return types.stream().anyMatch(type ->
                type.equals(candidate)
                        || type.getTypeName().equals(candidate.getTypeName())
                        || type.isAssignableFrom(candidate)
        );
    }

}
