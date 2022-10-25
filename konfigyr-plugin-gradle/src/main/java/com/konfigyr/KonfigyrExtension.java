package com.konfigyr;

import lombok.Getter;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import java.nio.file.Paths;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 09.10.22, Sun
 **/
@Getter
public class KonfigyrExtension {

    /**
     * Host where the Konfigyr server is reachable, can't be {@code null}.
     */
    private final Property<String> host;

    /**
     * Konfgyr Access token used to authenticate the metadata upload requests, can't be {@code null}.
     */
    private final Property<String> token;

    /**
     * Konfigyr plugin metadata dist file where the plugin would keep the state of all uploaded
     * property metadata.
     */
    private final RegularFileProperty output;

    public KonfigyrExtension(Project project, ObjectFactory factory) {
        host = factory.property(String.class);
        token = factory.property(String.class);
        output = factory.fileProperty().fileValue(Paths.get(
                project.getBuildDir().getAbsolutePath(), "konfigyr", "konfigyr-metadata.json"
        ).toFile());
    }
}
