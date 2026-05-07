package com.konfigyr.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;

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

        // register the Gradle build service to be shared with tasks and actions
        final Provider<ArtifactoryService> service = registerArtifactoryService(project, extension);

        // register the transform action that would generate the artifact metadata for each dependency
        registerArtifactMetadataTransform(project, service);

        // register tasks...
        final Provider<GenerateArtifactMetadataTask> generateMetadataTask =
                registerGenerateMetadataTask(project, service);
        final Provider<PublishArtifactMetadataTask> publishMetadataTask =
                registerPublishMetadataTask(project, extension, service, generateMetadataTask);

        // register konfigyr task that would be used as the main entrypoint...
        project.getTasks().register(PLUGIN_NAME, DefaultTask.class, task -> {
            task.setGroup(PLUGIN_NAME);
            task.setDescription("Task that would generate and publish the Konfigyr artifact metadata for your project");

            task.dependsOn(publishMetadataTask);
        });
    }

    @NullMarked
    private static Provider<ArtifactoryService> registerArtifactoryService(Project project, KonfigyrExtension extension) {
        return project.getGradle().getSharedServices().registerIfAbsent(PLUGIN_NAME, ArtifactoryService.class, spec -> {
            spec.parameters(parameters -> {
                parameters.getConfiguration().set(project.provider(extension::toConfiguration));
                parameters.getTimeout().set(extension.getReleasePollTimeout().map(Duration::ofMillis));
                parameters.getInterval().set(extension.getReleasePollInterval().map(Duration::ofMillis));
            });
        });
    }

    @NullMarked
    private static void registerArtifactMetadataTransform(Project project, Provider<ArtifactoryService> service) {
        project.getDependencies().registerTransform(ArtifactMetadataTransform.class, spec -> {
            spec.getFrom().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            spec.getTo().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactMetadataTransform.ARTIFACT_TYPE);
            spec.parameters(parameters -> parameters.getService().set(service));
        });
    }

    @NullMarked
    private static Provider<GenerateArtifactMetadataTask> registerGenerateMetadataTask(Project project, Provider<ArtifactoryService> service) {
        return project.getTasks().register(GenerateArtifactMetadataTask.NAME, GenerateArtifactMetadataTask.class, task -> {
            task.getClasspath().from(project.provider(() -> resolveProjectCompileClasspath(project)));
            task.getArtifacts().set(project.provider(() -> resolveTransformedArtifactCollection(project)));
            task.getRuntimeClasspath().from(project.provider(() -> resolveProjectRuntimeClasspath(project)));
            task.getManifest().set(project.getLayout().getBuildDirectory().file("konfigyr/artifact-manifest.txt"));
            task.getOutput().set(project.getLayout().getBuildDirectory().dir("konfigyr/manifests"));

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Generates the Konfigyr artifact metadata for the project and it's dependencies");

            task.dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaPlugin.JAR_TASK_NAME);
            task.mustRunAfter(JavaPlugin.JAR_TASK_NAME);
        });
    }

    @NullMarked
    private static Provider<PublishArtifactMetadataTask> registerPublishMetadataTask(
            Project project,
            KonfigyrExtension extension,
            Provider<ArtifactoryService> service,
            Provider<GenerateArtifactMetadataTask> generateMetadataTask
    ) {
        return project.getTasks().register(PublishArtifactMetadataTask.NAME, PublishArtifactMetadataTask.class, task -> {
            task.getArtifactManifest().set(generateMetadataTask.flatMap(GenerateArtifactMetadataTask::getManifest));
            task.getMetadataDirectory().set(generateMetadataTask.flatMap(GenerateArtifactMetadataTask::getOutput));
            task.getReleaseTimeout().set(extension.getReleasePollTimeout().map(Duration::ofMillis));
            task.getReleasePollingInterval().set(extension.getReleasePollInterval().map(Duration::ofMillis));

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Publishes the generated Konfigyr artifact metadata and the project manifest");

            task.dependsOn(GenerateArtifactMetadataTask.NAME);
            task.mustRunAfter(GenerateArtifactMetadataTask.NAME);

            // this task is a publishing one, it should never be cached...
            task.getOutputs().upToDateWhen(ignore -> false);
        });
    }

    @NullMarked
    private static ArtifactCollection resolveTransformedArtifactCollection(Project project) {
        return project.getConfigurations()
                .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .getIncoming()
                .artifactView(view -> view.attributes(attributes -> attributes.attribute(
                        ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                        ArtifactMetadataTransform.ARTIFACT_TYPE
                )))
                .getArtifacts();
    }

    private static FileCollection resolveProjectRuntimeClasspath(Project project) {
        return project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .getIncoming()
                .getFiles();
    }

    private static FileCollection resolveProjectCompileClasspath(Project project) {
        return project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
                .getIncoming()
                .getFiles();
    }

}
