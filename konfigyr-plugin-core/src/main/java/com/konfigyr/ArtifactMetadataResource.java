package com.konfigyr;

import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Represents a metadata resource for an artifact, providing mechanisms to access its content 
 * and retrieve its name. This interface abstracts how Spring Boot configuration metadata is 
 * provided to the metadata parser, allowing different sources to be used interchangeably.
 * <p>
 * The artifact metadata may be backed by various sources such as files on the filesystem, 
 * in-memory byte arrays, or entries within JAR archives. Implementations of this interface 
 * provide a consistent way to read Spring Boot configuration metadata files (e.g., 
 * {@code spring-configuration-metadata.json}) regardless of their underlying storage mechanism.
 * <p>
 * Typical usage involves creating instances through the static factory methods and then 
 * opening the resource as an input stream to parse the Spring Boot configuration metadata 
 * and generate {@link com.konfigyr.artifactory.PropertyDescriptor} instances.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
@NullMarked
public interface ArtifactMetadataResource {

    /**
     * Returns the name of the resource containing the Spring Boot configuration metadata.
     * <p>
     * The format of the name depends on the underlying implementation. For file-based resources, 
     * this would typically be the absolute file path or URI. For in-memory resources, this could 
     * be a descriptive label or the original entry name from a JAR archive.
     * <p>
     * This name is primarily used for logging, debugging, and error reporting purposes.
     *
     * @return the name of the resource, never {@literal null}.
     */
    String name();

    /**
     * Opens the resource and returns an {@link InputStream} to read its contents.
     * <p>
     * Each invocation of this method creates a new input stream, allowing the resource to be 
     * read multiple times if necessary. The caller is responsible for closing the returned 
     * stream to prevent resource leaks.
     * <p>
     * The returned stream provides access to the Spring Boot configuration metadata in JSON format,
     * which can then be parsed to extract property descriptors.
     *
     * @return a new input stream for reading the resource contents, never {@literal null}.
     * @throws IOException if an error occurs while opening the resource or accessing the underlying data.
     */
    InputStream open() throws IOException;

    /**
     * Creates an {@link ArtifactMetadataResource} from a {@link File}.
     * <p>
     * This is a convenience factory method that delegates to {@link #of(Path)} after converting
     * the file to a path.
     *
     * @param file the file containing Spring Boot configuration metadata, must not be {@literal null}.
     * @return a new artifact metadata resource backed by the specified file, never {@literal null}.
     */
    static ArtifactMetadataResource of(File file) {
        return of(file.toPath());
    }

    /**
     * Creates an {@link ArtifactMetadataResource} from a file system {@link Path}.
     * <p>
     * This factory method creates a resource that reads metadata from a file on the filesystem.
     * The file should contain valid Spring Boot configuration metadata in JSON format.
     *
     * @param file the path to the file containing Spring Boot configuration metadata, must not be {@literal null}.
     * @return a new artifact metadata resource backed by the specified file path, never {@literal null}.
     */
    static ArtifactMetadataResource of(Path file) {
        return new FileArtifactMetadataResource(file);
    }

    /**
     * Creates an {@link ArtifactMetadataResource} from an in-memory byte array.
     * <p>
     * This factory method is useful when the configuration metadata has already been read into
     * memory, such as when extracting entries from JAR archives or processing resources from
     * non-file sources. The byte array should contain valid Spring Boot configuration metadata
     * in JSON format.
     *
     * @param name     a descriptive name for the resource (e.g., original entry name), must not be {@literal null}.
     * @param contents the raw bytes of the Spring Boot configuration metadata, must not be {@literal null}.
     * @return a new artifact metadata resource backed by the specified byte array, never {@literal null}.
     */
    static ArtifactMetadataResource of(String name, byte[] contents) {
        return new ByteArrayArtifactMetadataResource(name, contents);
    }

}
