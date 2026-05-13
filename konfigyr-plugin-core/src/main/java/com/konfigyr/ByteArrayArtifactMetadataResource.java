package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@NullMarked
final class ByteArrayArtifactMetadataResource implements ArtifactMetadataResource {

    private final String name;
    private final byte[] contents;

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
