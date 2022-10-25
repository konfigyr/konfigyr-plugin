package com.konfigyr;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.configurationmetadata.*;
import org.springframework.util.Assert;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 03.10.22, Mon
 **/
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ConfigurationMetadataParser {

    private final TypeResolver typeResolver;

    public ConfigurationMetadataParser(ClassLoader classLoader) {
        this(new TypeResolver(classLoader));
    }

    public ConfigurationMetadata parse(@Nonnull Artifact artifact, @Nonnull Iterable<File> metadata) {
        final ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();

        metadata.forEach(file -> {
            try {
                builder.withJsonResource(new FileInputStream(file), StandardCharsets.UTF_8);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("Could not find metadata file data for: " + file, e);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read metadata file data for: " + file, e);
            }
        });

        final ConfigurationMetadataRepository repository = builder.build();
        final List<PropertyMetadata> properties = repository.getAllProperties().values()
                .stream()
                .map(it -> resolve(repository, it))
                .collect(Collectors.toList());

        return new ConfigurationMetadata(artifact, properties);
    }

    private PropertyMetadata resolve(ConfigurationMetadataRepository repository, ConfigurationMetadataProperty metadata) {
        Assert.hasText(metadata.getName(), "Metadata property name can not be blank");

        String className = metadata.getType();

        if (className == null) {
            className = String.class.getName();
        }

        ResolvedType type = typeResolver.resolve(className);

        if (type == null) {
            type = ResolvedType.from(String.class).build();
        }

        final ConfigurationMetadataSource source = resolveConfigurationMetadataSource(repository, metadata);

        return PropertyMetadata.builder()
                .type(className)
                .kind(PropertyKind.from(type.getType()))
                .isCollection(type.isCollection())
                .isMap(type.isMap())
                .name(metadata.getId())
                .hints(resolveHints(type, metadata))
                .group(source != null ? source.getGroupId() : null)
                .source(source != null ? source.getType() : null)
                .description(metadata.getDescription())
                .defaultValue(metadata.getDefaultValue())
                .deprecation(metadata.getDeprecation())
                .build();
    }

    static ConfigurationMetadataSource resolveConfigurationMetadataSource(ConfigurationMetadataRepository repository,
                                                                          ConfigurationMetadataProperty metadata) {
        for (ConfigurationMetadataGroup group : repository.getAllGroups().values()) {
            for (ConfigurationMetadataSource source : group.getSources().values()) {
                if (source.getProperties().containsKey(metadata.getId())) {
                    return source;
                }
            }
        }

        return null;
    }


    static List<String> resolveHints(ResolvedType type, ConfigurationMetadataProperty metadata) {
        if (type.isEnumeration()) {
            Object[] constants;

            try {
                constants = type.getType().getEnumConstants();
            } catch (NoClassDefFoundError e) {
                constants = new Object[0];
            }

            if (constants == null || constants.length == 0) {
                return Collections.emptyList();
            }

            return Arrays.stream(constants)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        final Hints hints = metadata.getHints();

        if (hints == null || hints.getValueHints() == null) {
            return Collections.emptyList();
        }

        return hints.getValueHints()
                .stream()
                .map(ValueHint::getValue)
                .map(Objects::toString)
                .collect(Collectors.toList());
    }

}
