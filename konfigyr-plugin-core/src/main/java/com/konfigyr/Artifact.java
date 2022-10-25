package com.konfigyr;

import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Comparator;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 02.10.22, Sun
 **/
@Value
public class Artifact implements Comparable<Artifact>, Serializable {
    private static final long serialVersionUID = 5984514432176800197L;

    private static final Comparator<Artifact> ARTIFACT_COMPARATOR = Comparator
            .comparing(Artifact::getGroup)
            .thenComparing(Artifact::getName)
            .thenComparing(Artifact::getVersion);

    /**
     * The group of this artifact. The group is often required to find the artifacts of a dependency in a
     * repository.
     */
    @Nonnull String group;

    /**
     * The name of this artifact. The name is almost always required to find the artifacts of a dependency in
     * a repository.
     */
    @Nonnull String name;

    /**
     * The version of this artifact. The version is often required to find the artifacts of a dependency in a
     * repository.
     */
    @Nonnull String version;

    @Override
    public int compareTo(@Nonnull Artifact o) {
        return ARTIFACT_COMPARATOR.compare(this, o);
    }

    @Override
    public String toString() {
        return group + ':' + name + ':' + version;
    }
}
