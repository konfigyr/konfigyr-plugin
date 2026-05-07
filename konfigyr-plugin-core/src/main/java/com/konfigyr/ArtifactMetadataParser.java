package com.konfigyr;

import com.fasterxml.classmate.TypeResolver;
import com.konfigyr.artifactory.Deprecation;
import com.konfigyr.artifactory.PropertyDescriptor;
import com.konfigyr.schema.JsonSchemaGenerator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Parses configuration metadata files produced by Spring Boot applications or libraries and constructs
 * a collection of {@link PropertyDescriptor}s suitable for upload to the Konfigyr Artifactory.
 * <p>
 * The {@code ArtifactMetadataParser} is responsible for reading the standard Spring Boot metadata file
 * located at {@code META-INF/spring-configuration-metadata.json}, extracting property definitions,
 * and converting them into Konfigyr’s domain model.
 * <p>
 * This parser uses the {@link ConfigurationMetadataRepositoryJsonBuilder} to read the Spring Boot metadata
 * JSON files that are later aggregated and converted into a collection of {@link PropertyDescriptor descriptors}.
 *
 * @see PropertyDescriptor
 * @author Vladimir Spasic
 * @since 1.0.0
 */
public class ArtifactMetadataParser {

    private final TypeNameResolver typeNameResolver;
    private final JsonSchemaGenerator jsonSchemaGenerator;

    /**
     * Creates a new {@link ArtifactMetadataParser} instance using the given {@link ClassLoader} to resolve
     * type names and generate JSON schemas from the Spring Boot metadata.
     *
     * @param classLoader the class loader to use, never {@literal null}.
     */
    public ArtifactMetadataParser(@NonNull ClassLoader classLoader) {
        final TypeLoader typeLoader = new TypeLoader(classLoader);
        final TypeResolver typeResolver = new TypeResolver();

        this.typeNameResolver = new TypeNameResolver(typeLoader, typeResolver);
        this.jsonSchemaGenerator = JsonSchemaGenerator.createDefaultGenerator(typeLoader, typeResolver);
    }

    /**
     * Parses the given metadata files and returns a collection of {@link PropertyDescriptor}s.
     *
     * @param metadata the metadata files to parse, cannot be {@literal null}.
     * @return the parsed property descriptors from the Spring Boot metadata files, never {@literal null}.
     */
    public List<PropertyDescriptor> parse(Resource... metadata) {
        return parse(List.of(metadata));
    }

    /**
     * Parses the given metadata files and returns a collection of {@link PropertyDescriptor}s.
     *
     * @param metadata the metadata files to parse, cannot be {@literal null}.
     * @return the parsed property descriptors from the Spring Boot metadata files, never {@literal null}.
     */
    public List<PropertyDescriptor> parse(@NonNull Iterable<? extends Resource> metadata) {
        final ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();

        metadata.forEach(resource -> {
            try {
                builder.withJsonResource(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("Could not find metadata file data for: " + resource.getFilename() , e);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read metadata file data for: " + resource.getFilename(), e);
            }
        });

        final ConfigurationMetadataRepository repository = builder.build();

        return repository.getAllProperties().values()
                .stream()
                .map(this::resolve)
                .sorted(PropertyDescriptor::compareTo)
                .toList();
    }

    private PropertyDescriptor resolve(ConfigurationMetadataProperty metadata) {
        Assert.hasText(metadata.getId(), "Metadata property identifier can not be blank");

        ResolvedPropertyType type = typeNameResolver.resolve(metadata.getType());

        if (type == null) {
            type = ResolvedPropertyType.STRING_TYPE;
        }

        String typeName = metadata.getType();

        if (typeName == null || typeName.isBlank()) {
            typeName = ResolvedPropertyType.STRING_TYPE.getTypeName();
        }

        return PropertyDescriptor.builder()
                .name(metadata.getId())
                .schema(jsonSchemaGenerator.generateSchema(type.getType(), metadata))
                .typeName(typeName)
                .description(metadata.getDescription())
                .defaultValue(resolveDefaultValue(metadata))
                .deprecation(resolveDeprecation(metadata))
                .build();
    }

    @Nullable
    static Deprecation resolveDeprecation(ConfigurationMetadataProperty metadata) {
        if (metadata.getDeprecation() == null) {
            return null;
        }
        return new Deprecation(
                metadata.getDeprecation().getReason(),
                metadata.getDeprecation().getReplacement()
        );
    }

    @Nullable
    static String resolveDefaultValue(ConfigurationMetadataProperty metadata) {
        final Object defaultValue = metadata.getDefaultValue();
        if (defaultValue == null) {
            return null;
        }

        final String resolved;

        if (defaultValue instanceof String value) {
            resolved = value;
        } else if (defaultValue instanceof Collection<?> collection) {
            resolved = collection.stream()
                    .map(it -> Objects.toString(it, null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
        } else if (defaultValue.getClass().isArray()) {
            resolved = Arrays.stream((Object[]) defaultValue)
                    .map(it -> Objects.toString(it, null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
        } else {
            resolved = Objects.toString(defaultValue, null);
        }

        return StringUtils.hasText(resolved) ? resolved : null;
    }

}
