package com.konfigyr;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 09.10.22, Sun
 **/
public abstract class KonfigyrUploadTask extends DefaultTask {

    @Input
    abstract Property<String> getHost();

    @Input
    abstract Property<String> getToken();

    @Input
    abstract ListProperty<GradleArtifact> getArtifacts();

    @InputFiles
    abstract Property<FileCollection> getClasspath();

    @OutputFile
    abstract RegularFileProperty getOutput();

    @TaskAction
    void upload() throws IOException {
        final Project project = getProject();

        final ConfigurationMetadataUploaderBuilder builder = ConfigurationMetadataUploaderBuilder.create()
                .host(getHost().get())
                .token(getToken().get())
                .classpath(getClasspath().get())
                .logger(getLogger());

        final Artifact artifact = new Artifact(
                Objects.toString(project.getGroup()),
                project.getName(),
                Objects.toString(project.getVersion())
        );

        getLogger().debug("Registering artifact for current Gradle Project {}", artifact);

        // include the current project as an artifact
        builder.artifact(artifact, getClasspath().get()
                .getAsFileTree()
                .matching(filters -> filters.include(
                        "**/spring-configuration-metadata.json",
                        "**/additional-spring-configuration-metadata.json"
                ))
                .getFiles()
        );

        // include the resolved project dependencies as artifacts
        getArtifacts().getOrElse(Collections.emptyList()).forEach(it -> builder.artifact(it.getArtifact(), it));

        final List<ConfigurationMetadata> metadata = builder.build().upload();

        getLogger().debug("Uploaded {}", metadata);

        final String json = JSONWriter.valueToString(metadata);

        Files.write(
                getOutput().get().getAsFile().toPath(),
                json.getBytes(StandardCharsets.UTF_8)
        );
    }

}
