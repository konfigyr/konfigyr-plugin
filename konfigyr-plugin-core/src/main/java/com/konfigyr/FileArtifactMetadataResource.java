package com.konfigyr;


import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@NullMarked
final class FileArtifactMetadataResource implements ArtifactMetadataResource {

    private final Path file;

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
