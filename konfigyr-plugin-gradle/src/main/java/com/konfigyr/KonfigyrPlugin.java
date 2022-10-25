package com.konfigyr;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.springframework.util.ClassUtils;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 09.10.22, Sun
 **/
public class KonfigyrPlugin implements Plugin<Project> {

    static String PLUGIN_NAME = "konfigyr";

    @Override
    public void apply(@Nonnull Project project) {
        final KonfigyrExtension extension = project.getExtensions().create(PLUGIN_NAME, KonfigyrExtension.class);

        project.getTasks().create(PLUGIN_NAME, KonfigyrUploadTask.class, task -> {
            task.getHost().set(extension.getHost());
            task.getToken().set(extension.getToken());
            task.getOutput().set(extension.getOutput());
            task.getArtifacts().set(project.provider(() -> resolveArtifacts(project)));
            task.getClasspath().set(project.provider(() -> resolveClasspath(project)));

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Task that would upload the Spring Property metadata to your Konfigyr service");

            task.mustRunAfter("compileJava");
        });
    }

    private static FileCollection resolveClasspath(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets()
                .stream()
                .map(SourceSet::getCompileClasspath)
                .reduce(FileCollection::plus)
                .orElseGet(project::files);
    }

    private static Iterable<GradleArtifact> resolveArtifacts(Project project) {
        return project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .getResolvedConfiguration()
                .getResolvedArtifacts()
                .stream()
                .map(artifact -> createGradleArtifact(project, artifact))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static GradleArtifact createGradleArtifact(Project project, ResolvedArtifact artifact) {
        final ComponentIdentifier identifier = artifact.getId().getComponentIdentifier();

        if (!ClassUtils.isAssignableValue(ModuleComponentIdentifier.class, identifier)) {
            return null;
        }

        final FileTree metadata = project.zipTree(
                artifact.getFile()
        ).matching(spec -> spec.include(
                "**/spring-configuration-metadata.json",
                "**/additional-spring-configuration-metadata.json"
        ));

        if (metadata.isEmpty()) {
            return null;
        }

        return GradleArtifact.from((ModuleComponentIdentifier) identifier, metadata.getFiles());
    }
}
