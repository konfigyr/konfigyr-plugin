package com.konfigyr;

import lombok.Value;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.Serializable;
import java.util.Iterator;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 17.10.22, Mon
 **/
@Value
public class GradleArtifact implements Serializable, Iterable<File>, Comparable<GradleArtifact> {
    private static final long serialVersionUID = -2122654973223976222L;

    Artifact artifact;

    Iterable<File> metadata;

    @Override
    public Iterator<File> iterator() {
        return metadata.iterator();
    }

    @Override
    public int compareTo(@Nonnull GradleArtifact o) {
        return artifact.compareTo(o.artifact);
    }

    static GradleArtifact from(ModuleComponentIdentifier identifier, Iterable<File> metadata) {
        final Artifact artifact = new Artifact(identifier.getGroup(), identifier.getModule(), identifier.getVersion());
        return new GradleArtifact(artifact, metadata);
    }
}
