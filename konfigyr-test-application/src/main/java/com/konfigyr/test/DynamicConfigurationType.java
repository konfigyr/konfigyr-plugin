package com.konfigyr.test;

import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Custom type used to test dynamic configuration.
 *
 * @param name the name field
 * @param resource the resource field
 * @param size the data size field
 * @param date the date field
 * @param time the time field
 * @param dateTime the date time field
 */
public record DynamicConfigurationType(
        String name,
        Resource resource,
        DataSize size,
        LocalDate date,
        LocalTime time,
        ZonedDateTime dateTime
) {
}