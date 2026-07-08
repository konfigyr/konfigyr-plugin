package com.konfigyr.gradle;

import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.artifactory.Artifact;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.Jar;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;

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
        final Provider<ArtifactoryService> service = registerArtifactoryService(project);

        // register the transform action that would generate the artifact metadata for each dependency
        registerArtifactMetadataTransform(project, service);

        // register tasks...
        final Provider<GenerateArtifactMetadataTask> generateMetadataTask =
                registerGenerateMetadataTask(project, service);
        final Provider<PublishArtifactMetadataTask> publishMetadataTask =
                registerPublishMetadataTask(project, extension, service, generateMetadataTask);
        final Provider<ResolveServiceDependenciesTask> resolveDependenciesTask =
                registerResolveServiceDependenciesTask(project, extension, service);
        final Provider<CreateServiceReleaseTask> createReleaseTask =
                registerCreateServiceReleaseTask(project, extension, service, generateMetadataTask, resolveDependenciesTask);

        // register konfigyr task that would be used as the main entrypoint...
        project.getTasks().register(PLUGIN_NAME, DefaultTask.class, task -> {
            task.setGroup(PLUGIN_NAME);
            task.setDescription("Task that would generate and publish the Konfigyr artifact metadata for your project");

            task.dependsOn(publishMetadataTask, createReleaseTask);
        });
    }

    /**
     * Finds or creates the {@link KonfigyrExtension} on the root project.
     * <p>
     * Used by {@link #registerArtifactoryService} to source the shared {@link ArtifactoryService}'s
     * connection configuration from the root project when it's actually configured there, since the
     * underlying {@code BuildService} is a build-wide singleton and can only ever have one
     * configuration for the whole build.
     */
    @NullMarked
    private static KonfigyrExtension resolveRootExtension(Project project) {
        final Project root = project.getRootProject();
        final KonfigyrExtension rootExtension = root.getExtensions().findByType(KonfigyrExtension.class);

        if (rootExtension != null) {
            return rootExtension;
        }

        return root.getExtensions().create(PLUGIN_NAME, KonfigyrExtension.class);
    }

    /**
     * Registers the shared {@link ArtifactoryService}, exactly once for the whole build.
     * <p>
     * {@code registerIfAbsent} only honors the configuration action for the first project whose
     * {@code apply()} triggers it — every subsequent call with the same name is a no-op. The
     * connection configuration itself, however, is resolved lazily via {@link #resolveArtifactoryConfiguration},
     * which is only evaluated once the {@code BuildService} is actually realized (after every project
     * has been configured) — making the outcome deterministic regardless of which project's
     * {@code apply()} happened to trigger the registration.
     */
    @NullMarked
    private static Provider<ArtifactoryService> registerArtifactoryService(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent(PLUGIN_NAME, ArtifactoryService.class, spec -> {
            spec.parameters(parameters -> parameters.getConfiguration().set(
                    project.provider(() -> resolveArtifactoryConfiguration(project))
            ));
        });
    }

    /**
     * Resolves the single {@link ArtifactoryConfiguration} to use for the whole build's shared
     * {@link ArtifactoryService}.
     * <p>
     * If the root project's own {@link KonfigyrExtension} has credentials configured, it is always
     * used — this is the only way to make the outcome deterministic when config is instead spread
     * across several projects (e.g. via {@code subprojects { konfigyr { ... } } }), since that pattern
     * never actually configures the root project's own extension. Otherwise, every project in the
     * build is inspected, and its configured connection settings are compared for equality:
     * <ul>
     *     <li>if none are configured, the build fails since credentials are required;</li>
     *     <li>if exactly one distinct configuration is found, it is used — this is the common case
     *     where every project configures identical connection settings;</li>
     *     <li>if more than one distinct configuration is found, the build fails rather than silently
     *     picking one, since the underlying {@code BuildService} is a build-wide singleton that can
     *     only ever use a single set of credentials.</li>
     * </ul>
     *
     * @param project the project used to resolve every project in the build, cannot be {@literal null}.
     * @return the single {@link ArtifactoryConfiguration} to use, never {@literal null}.
     */
    @NullMarked
    private static ArtifactoryConfiguration resolveArtifactoryConfiguration(Project project) {
        final KonfigyrExtension rootExtension = resolveRootExtension(project);

        if (rootExtension.isConfigured()) {
            return rootExtension.toConfiguration();
        }

        final Set<ArtifactoryConfiguration> configurations = new LinkedHashSet<>();

        for (Project candidate : project.getRootProject().getAllprojects()) {
            final KonfigyrExtension extension = candidate.getExtensions().findByType(KonfigyrExtension.class);

            if (extension != null && extension.isConfigured()) {
                configurations.add(extension.toConfiguration());
            }
        }

        if (configurations.isEmpty()) {
            throw new GradleException(
                    "Konfigyr plugin requires 'clientId' and 'clientSecret' to be set. Configure them in the " +
                    "konfigyr { } block (on the root project, or via subprojects{}/allprojects{}), or via the " +
                    "'KONFIGYR_CLIENT_ID'/'KONFIGYR_CLIENT_SECRET' environment variables."
            );
        }

        if (configurations.size() > 1) {
            throw new GradleException(
                    "Multiple projects configure different Konfigyr connection settings; the connection is " +
                    "shared build-wide - configure it once, either on the root project or via " +
                    "subprojects{}/allprojects{}, or via the 'KONFIGYR_CLIENT_ID'/'KONFIGYR_CLIENT_SECRET' " +
                    "environment variables."
            );
        }

        return configurations.iterator().next();
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
            task.getProjectArchive().set(resolveJarArchiveFile(project));
            task.getRuntimeClasspath().from(project.provider(() -> resolveProjectRuntimeClasspath(project)));
            task.getProjectArtifact().set(project.provider(() -> createProjectArtifact(project)));
            task.getMetadata().set(project.getLayout().getBuildDirectory().file("konfigyr/metadata.json"));

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Generates the Konfigyr artifact metadata for this project's own artifact");

            task.dependsOn(
                    project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME),
                    project.getTasks().named(JavaPlugin.JAR_TASK_NAME)
            );
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
            task.getMetadata().set(generateMetadataTask.flatMap(GenerateArtifactMetadataTask::getMetadata));
            task.getReleaseTimeout().set(extension.getPublish().getPollTimeout().map(Duration::ofMillis));
            task.getReleasePollingInterval().set(extension.getPublish().getPollInterval().map(Duration::ofMillis));

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Publishes this project's own artifact metadata directly to the Konfigyr Artifactory");

            task.dependsOn(generateMetadataTask);
        });
    }

    @NullMarked
    private static Provider<ResolveServiceDependenciesTask> registerResolveServiceDependenciesTask(
            Project project, KonfigyrExtension extension, Provider<ArtifactoryService> service) {
        return project.getTasks().register(ResolveServiceDependenciesTask.NAME, ResolveServiceDependenciesTask.class, task -> {
            task.getProjectArtifacts().set(project.provider(() -> resolveProjectArtifacts(project)));
            task.getArtifacts().set(project.provider(() -> resolveTransformedArtifactCollection(project)));
            task.getRuntimeClasspath().from(project.provider(() -> resolveProjectRuntimeClasspath(project)));
            task.getDependencyManifest().set(project.getLayout().getBuildDirectory().file("konfigyr/dependency-manifest.txt"));
            task.getDependencyDirectory().set(project.getLayout().getBuildDirectory().dir("konfigyr/dependencies"));
            task.getNamespace().set(extension.getService().getNamespace());
            task.getServiceName().set(extension.getService().getName());

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Resolves this service's dependencies that expose Spring Boot configuration metadata");

            task.onlyIf(
                    "a namespace and service must be configured to create a service release",
                    ignore -> task.getNamespace().isPresent() && task.getServiceName().isPresent()
            );
        });
    }

    @NullMarked
    private static Provider<CreateServiceReleaseTask> registerCreateServiceReleaseTask(
            Project project,
            KonfigyrExtension extension,
            Provider<ArtifactoryService> service,
            Provider<GenerateArtifactMetadataTask> generateMetadataTask,
            Provider<ResolveServiceDependenciesTask> resolveDependenciesTask
    ) {
        return project.getTasks().register(CreateServiceReleaseTask.NAME, CreateServiceReleaseTask.class, task -> {
            task.getServiceArtifactMetadata().set(generateMetadataTask.flatMap(GenerateArtifactMetadataTask::getMetadata));
            task.getDependencyManifest().set(resolveDependenciesTask.flatMap(ResolveServiceDependenciesTask::getDependencyManifest));
            task.getDependencyDirectory().set(resolveDependenciesTask.flatMap(ResolveServiceDependenciesTask::getDependencyDirectory));
            task.getNamespace().set(extension.getService().getNamespace());
            task.getServiceName().set(extension.getService().getName());

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Creates a Service Release for this service, uploading required artifact metadata");

            task.onlyIf(
                    "a namespace and service must be configured to create a service release",
                    ignore -> task.getNamespace().isPresent() && task.getServiceName().isPresent()
            );

            task.dependsOn(generateMetadataTask, resolveDependenciesTask);
        });
    }

    private static Provider<RegularFile> resolveJarArchiveFile(Project project) {
        return project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class).flatMap(Jar::getArchiveFile);
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

    private static Map<String, Artifact> resolveProjectArtifacts(Project project) {
        final Set<Project> projects = project.getRootProject().getAllprojects();
        final Map<String, Artifact> artifacts = new LinkedHashMap<>(projects.size());

        project.getRootProject().getAllprojects().forEach(p -> {
            final Artifact artifact = createProjectArtifact(p);

            if (artifact != null) {
                artifacts.put(p.getPath(), artifact);
            }
        });

        return Collections.unmodifiableMap(artifacts);
    }

    @Nullable
    private static Artifact createProjectArtifact(Project project) {
        final String groupId = Objects.toString(project.getGroup(), null);
        final String artifactId = Objects.toString(project.getName(), null);
        final String version = Objects.toString(project.getVersion(), null);

        if (groupId == null || groupId.isBlank()
                || artifactId == null || artifactId.isBlank()
                || version == null || version.isBlank()) {
            return null;
        }

        return Artifact.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .name(project.getDisplayName())
                .description(project.getDescription())
                .build();
    }

}
