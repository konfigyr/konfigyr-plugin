package com.konfigyr.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.jspecify.annotations.NonNull;
import org.springframework.util.ClassUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gradle plugin for Konfigyr Artifacts.
 * <p>
 * This plugin provides a {@code konfigyr} task for uploading Spring Boot configuration metadata to Konfigyr.
 * <p>
 * You can configure the plugin using the {@code konfigyr} extension.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 **/
public class KonfigyrPlugin implements Plugin<@NonNull Project> {

    static final String PLUGIN_NAME = "konfigyr";

    @Override
    public void apply(@NonNull Project project) {
        final KonfigyrExtension extension = project.getExtensions().create(PLUGIN_NAME, KonfigyrExtension.class);

        project.getGradle().getSharedServices().registerIfAbsent(PLUGIN_NAME, ArtifactoryService.class, spec -> {
            spec.getParameters().getConfiguration().set(project.provider(extension::toConfiguration));
            spec.getParameters().getClasspath().set(project.provider(() -> resolveClasspath(project).getFiles()));
        });

        project.getTasks().register(PLUGIN_NAME, KonfigyrUploadTask.class, task -> {
            task.getConfiguration().set(project.provider(extension::toConfiguration));
            task.getArtifacts().set(project.provider(() -> resolveArtifacts(project)));
            task.getClasspath().set(project.provider(() -> resolveClasspath(project)));
            task.getReleaseTimeout().set(extension.getReleasePollTimeout().map(Duration::ofMillis));
            task.getReleasePollingInterval().set(extension.getReleasePollInterval().map(Duration::ofMillis));
            task.getOutput().set(project.getLayout().getBuildDirectory().file("konfigyr/manifest.json"));

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Task that would upload the Spring Boot configuration metadata to your Konfigyr service");

            task.mustRunAfter("compileJava");
        });
    }

    private static FileCollection resolveClasspath(Project project) {
        final var compile = project.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets()
                .stream()
                .map(SourceSet::getCompileClasspath);

        final var runtime = project.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets()
                .stream()
                .map(SourceSet::getRuntimeClasspath);

        return Stream.concat(compile, runtime)
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

        return GradleArtifact.create((ModuleComponentIdentifier) identifier, project.zipTree(artifact.getFile()));
    }
}
