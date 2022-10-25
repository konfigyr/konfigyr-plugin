package com.konfigyr;

import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.unit.DataSize;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.*;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 03.10.22, Mon
 **/
class PropertyKindTest {

    @ParameterizedTest
    @MethodSource("kinds")
    void shouldResolvePropertyKind(Class<?> candidate, PropertyKind kind) {
        final PropertyKind resolved = PropertyKind.from(candidate);

        assertNotNull(resolved, "Resolved kind can not be null for " + candidate);
        assertEquals(kind, resolved);
    }

    private static Stream<Arguments> kinds() {
        return Stream.of(
                Arguments.of(String.class, PropertyKind.STRING),
                Arguments.of(Character.class, PropertyKind.STRING),
                Arguments.of(File.class, PropertyKind.STRING),
                Arguments.of(Class.class, PropertyKind.STRING),

                Arguments.of(Integer.class, PropertyKind.NUMBER),
                Arguments.of(Long.class, PropertyKind.NUMBER),
                Arguments.of(Double.class, PropertyKind.NUMBER),
                Arguments.of(BigDecimal.class, PropertyKind.NUMBER),
                Arguments.of(BigInteger.class, PropertyKind.NUMBER),

                Arguments.of(Boolean.class, PropertyKind.BOOLEAN),

                Arguments.of(Duration.class, PropertyKind.DURATION),

                Arguments.of(TimeZone.class, PropertyKind.TIME_ZONE),

                Arguments.of(LocalDate.class, PropertyKind.DATE),

                Arguments.of(Date.class, PropertyKind.DATE_TIME),
                Arguments.of(Instant.class, PropertyKind.DATE_TIME),
                Arguments.of(LocalDateTime.class, PropertyKind.DATE_TIME),
                Arguments.of(ZonedDateTime.class, PropertyKind.DATE_TIME),
                Arguments.of(OffsetDateTime.class, PropertyKind.DATE_TIME),
                Arguments.of(ZonedDateTime.class, PropertyKind.DATE_TIME),

                Arguments.of(URL.class, PropertyKind.URI),
                Arguments.of(URI.class, PropertyKind.URI),

                Arguments.of(InetAddress.class, PropertyKind.INTERNET_ADDRESS),

                Arguments.of(DataSize.class, PropertyKind.DATA_SIZE),

                Arguments.of(MimeType.class, PropertyKind.MIME_TYPE),

                Arguments.of(Resource.class, PropertyKind.RESOURCE),

                Arguments.of(Charset.class, PropertyKind.CHARSET),

                Arguments.of(Locale.class, PropertyKind.LOCALE),

                Arguments.of(PropertyKind.class, PropertyKind.STRING),
                Arguments.of(HttpOptions.class, PropertyKind.STRING)
        );
    }

}