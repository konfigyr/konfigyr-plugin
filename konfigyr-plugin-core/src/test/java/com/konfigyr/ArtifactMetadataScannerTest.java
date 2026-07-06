package com.konfigyr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactMetadataScannerTest {

    private static final String METADATA_CONTENTS = "{\"properties\":[]}";

    @TempDir
    Path dir;

    @Test
    @DisplayName("should return an empty list when the artifact does not exist")
    void shouldReturnEmptyListForNonExistentArtifact() throws IOException {
        final File artifact = dir.resolve("does-not-exist.jar").toFile();

        assertThat(ArtifactMetadataScanner.scan(artifact)).isEmpty();
    }

    @Test
    @DisplayName("should return an empty list when a jar contains no configuration metadata")
    void shouldReturnEmptyListForJarWithoutMetadata() throws IOException {
        final File jar = createJar("empty.jar", Map.of("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"));

        assertThat(ArtifactMetadataScanner.scan(jar)).isEmpty();
    }

    @Test
    @DisplayName("should return an empty list when a directory contains no configuration metadata")
    void shouldReturnEmptyListForDirectoryWithoutMetadata() throws IOException {
        assertThat(ArtifactMetadataScanner.scan(dir.toFile())).isEmpty();
    }

    @Test
    @DisplayName("should scan a jar for every well-known configuration metadata path it contains")
    void shouldScanJarForMetadata() throws IOException {
        final Map<String, String> entries = new HashMap<>();
        ArtifactMetadataScanner.METADATA_PATHS.forEach(path -> entries.put(path, METADATA_CONTENTS));

        final File jar = createJar("artifact.jar", entries);

        final List<ArtifactMetadataResource> resources = ArtifactMetadataScanner.scan(jar);

        assertThat(resources)
                .extracting(ArtifactMetadataResource::name)
                .containsExactlyInAnyOrderElementsOf(ArtifactMetadataScanner.METADATA_PATHS);

        assertThat(resources)
                .extracting(this::readContent)
                .containsOnly(METADATA_CONTENTS);
    }

    @Test
    @DisplayName("should ignore jar entries that are not well-known configuration metadata paths")
    void shouldIgnoreUnrelatedJarEntries() throws IOException {
        final File jar = createJar("artifact.jar", Map.of(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n",
                "com/acme/Widget.class", "not-real-bytecode",
                "META-INF/spring-configuration-metadata.json", METADATA_CONTENTS
        ));

        final List<ArtifactMetadataResource> resources = ArtifactMetadataScanner.scan(jar);

        assertThat(resources)
                .singleElement()
                .extracting(ArtifactMetadataResource::name)
                .isEqualTo("META-INF/spring-configuration-metadata.json");
    }

    @Test
    @DisplayName("should scan a class-output directory for every well-known configuration metadata path it contains")
    void shouldScanDirectoryForMetadata() throws IOException {
        for (String path : ArtifactMetadataScanner.METADATA_PATHS) {
            final Path metadata = dir.resolve(path);
            Files.createDirectories(metadata.getParent());
            Files.writeString(metadata, METADATA_CONTENTS);
        }

        final List<ArtifactMetadataResource> resources = ArtifactMetadataScanner.scan(dir.toFile());

        assertThat(resources).hasSize(ArtifactMetadataScanner.METADATA_PATHS.size());

        assertThat(resources)
                .extracting(ArtifactMetadataResource::name)
                .allSatisfy(name -> assertThat(ArtifactMetadataScanner.METADATA_PATHS).anyMatch(name::endsWith));

        assertThat(resources)
                .extracting(this::readContent)
                .containsOnly(METADATA_CONTENTS);
    }

    @Test
    @DisplayName("should fail when the jar file can not be read")
    void shouldFailForCorruptJar() throws IOException {
        final Path jar = dir.resolve("corrupt.jar");
        Files.writeString(jar, "not a zip file");

        assertThatThrownBy(() -> ArtifactMetadataScanner.scan(jar.toFile()))
                .isInstanceOf(IOException.class);
    }

    private File createJar(String name, Map<String, String> entries) throws IOException {
        final File jar = dir.resolve(name).toFile();

        try (var out = new ZipOutputStream(Files.newOutputStream(jar.toPath()))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }

        return jar;
    }

    private String readContent(ArtifactMetadataResource resource) {
        try (var is = resource.open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
