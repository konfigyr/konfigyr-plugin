package com.konfigyr;


import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * {@link ArtifactMetadataResource} backed by a file on the filesystem.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see ArtifactMetadataResource#of(Path)
 */
@NullMarked
final class FileArtifactMetadataResource implements ArtifactMetadataResource {

    private final Path file;

    /**
     * Creates a new {@link FileArtifactMetadataResource}.
     *
     * @param file the path to the file containing Spring Boot configuration metadata, cannot be {@literal null}.
     */
    FileArtifactMetadataResource(Path file) {
        this.file = file;
    }

    @Override
    public String name() {
        return file.toUri().toString();
    }

    @Override
    public InputStream open() throws IOException {
        return Files.newInputStream(file, StandardOpenOption.READ);
    }
}
