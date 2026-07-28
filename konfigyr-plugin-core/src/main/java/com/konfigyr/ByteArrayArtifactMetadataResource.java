package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * {@link ArtifactMetadataResource} backed by an in-memory byte array, for metadata that has already
 * been read into memory (e.g. extracted from a JAR entry) rather than living on the filesystem.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see ArtifactMetadataResource#of(String, byte[])
 */
@NullMarked
final class ByteArrayArtifactMetadataResource implements ArtifactMetadataResource {

    private final String name;
    private final byte[] contents;

    /**
     * Creates a new {@link ByteArrayArtifactMetadataResource}.
     *
     * @param name a descriptive name for the resource, cannot be {@literal null}.
     * @param contents the raw bytes of the Spring Boot configuration metadata, cannot be {@literal null}.
     */
    public ByteArrayArtifactMetadataResource(String name, byte[] contents) {
        this.name = name;
        this.contents = contents;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public InputStream open() {
        return new ByteArrayInputStream(contents);
    }
}
