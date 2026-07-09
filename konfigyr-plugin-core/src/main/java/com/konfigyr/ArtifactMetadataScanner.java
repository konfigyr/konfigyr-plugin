package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans a jar file or an unpacked class-output directory for Spring Boot configuration metadata
 * files, producing the {@link ArtifactMetadataResource}s that can be parsed into
 * {@link com.konfigyr.artifactory.PropertyDescriptor}s.
 * <p>
 * The same well-known metadata paths are checked regardless of whether the artifact being scanned
 * is a resolved dependency (a jar) or a project's own compiled output (a directory, before its jar
 * task has run) — this is the single source of truth for "does this artifact expose Spring Boot
 * configuration metadata", shared by every place in the build that needs to answer that question.
 *
 * @author Vladimir Spasic
 * @since 1.1.0
 */
@NullMarked
public final class ArtifactMetadataScanner {

    /**
     * The well-known relative paths where Spring Boot writes generated configuration metadata.
     */
    public static final Set<String> METADATA_PATHS = Set.of(
            "META-INF/spring-configuration-metadata.json",
            "META-INF/additional-spring-configuration-metadata.json",
            "META-INF/spring/org.springframework.boot.configuration-metadata.json"
    );

    private ArtifactMetadataScanner() {
    }

    /**
     * Scans the given jar file or class-output directory for Spring Boot configuration metadata.
     *
     * @param artifact the jar file or directory to scan, must not be {@literal null}.
     * @return the metadata resources found, empty if the artifact contains none or does not exist.
     * @throws IOException if the artifact is a jar file that could not be read.
     */
    public static List<ArtifactMetadataResource> scan(File artifact) throws IOException {
        if (!artifact.exists()) {
            return List.of();
        }

        return artifact.isDirectory() ? scanDirectory(artifact) : scanJar(artifact);
    }

    private static List<ArtifactMetadataResource> scanJar(File jar) throws IOException {
        final List<ArtifactMetadataResource> candidates = new ArrayList<>();

        try (var zip = new ZipFile(jar)) {
            for (String path : METADATA_PATHS) {
                final ZipEntry entry = zip.getEntry(path);

                if (entry != null) {
                    final InputStream is = zip.getInputStream(entry);
                    candidates.add(ArtifactMetadataResource.of(entry.getName(), is.readAllBytes()));
                }
            }
        }

        return candidates;
    }

    private static List<ArtifactMetadataResource> scanDirectory(File dir) {
        final List<ArtifactMetadataResource> candidates = new ArrayList<>();

        for (String path : METADATA_PATHS) {
            final File metadata = new File(dir, path);

            if (metadata.exists()) {
                candidates.add(ArtifactMetadataResource.of(metadata));
            }
        }

        return candidates;
    }

}
