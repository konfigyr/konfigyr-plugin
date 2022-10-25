package com.konfigyr;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
@Value
public class ConfigurationMetadata implements Iterable<PropertyMetadata>, Serializable {
    private static final long serialVersionUID = -8950927097761268585L;

    /**
     * Artifact that is the owner of the configuration metadata.
     */
    @Nonnull Artifact artifact;

    /**
     * Collection that contains the metadata information of Spring configuration properties.
     */
    @Nonnull List<PropertyMetadata> metadata;

    /**
     * Returns a {@link Stream} of parsed metadata information of Spring configuration properties.
     *
     * @return metadata property stream
     */
    @Nonnull
    public Stream<PropertyMetadata> stream() {
        return metadata.stream();
    }

    /**
     * Returns a {@link Iterator} of parsed metadata information of Spring configuration properties.
     *
     * @return metadata property iterator
     */
    @Nonnull
    @Override
    public Iterator<PropertyMetadata> iterator() {
        return metadata.iterator();
    }

    public int size() {
        return metadata.size();
    }
}
