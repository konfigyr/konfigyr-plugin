package com.konfigyr.gradle;

import com.konfigyr.Registry;
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
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;

/**
 * Gradle plugin for publishing Spring Boot configuration metadata to Konfigyr.
 * <p>
 * Registers a {@code konfigyr} meta-task, and one publish/release task pair per registry declared in
 * the {@link KonfigyrExtension#getRegistries() registries} container - there is no single "the"
 * registry or connection, every declared registry gets its own task pair and its own {@link Registry}
 * connection, keyed by registry name.
 * <p>
 * You can configure the plugin using the {@code konfigyr} extension.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see KonfigyrExtension
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

        // register tasks that are registry-independent, run once per project regardless of
        // how many registries are declared for the plugin
        final Provider<GenerateArtifactMetadataTask> generateMetadataTask =
                registerGenerateMetadataTask(project, service);
        final Provider<ResolveServiceDependenciesTask> resolveDependenciesTask =
                registerResolveServiceDependenciesTask(project, extension, service);

        // register konfigyr meta-task that would be used as the main entrypoint...
        final TaskProvider<DefaultTask> meta = project.getTasks().register(PLUGIN_NAME, DefaultTask.class, task -> {
            task.setGroup(PLUGIN_NAME);
            task.setDescription("Task that would generate and publish the Konfigyr artifact metadata for your project");
        });

        // ...and one publish/release task pair per registry this project declares, registered lazily as
        // each registry is added to the container, so declaration order relative to plugin application
        // doesn't matter.
        extension.getRegistries().all(registry -> {
            final Provider<PublishArtifactMetadataTask> publishMetadataTask =
                    registerPublishMetadataTask(project, extension, service, generateMetadataTask, registry);
            final Provider<CreateServiceReleaseTask> createReleaseTask =
                    registerCreateServiceReleaseTask(project, extension, service, generateMetadataTask, resolveDependenciesTask, registry);

            meta.configure(task -> task.dependsOn(publishMetadataTask, createReleaseTask));
        });
    }

    /**
     * Finds or creates the {@link KonfigyrExtension} on the root project.
     * <p>
     * Used by {@link #registerArtifactoryService} to source the shared {@link ArtifactoryService}'s
     * per-registry connection settings from the root project when it's actually configured there,
     * since a registry's connection is shared build-wide and can only ever have one configuration.
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
     * {@code apply()} triggers it — every later call with the same name is a no-op. The
     * connection configuration itself, however, is resolved lazily via {@link #resolveRegistries},
     * which is only evaluated once the {@code BuildService} is actually realized (after every project
     * has been configured) — making the outcome deterministic regardless of which project's
     * {@code apply()} happened to trigger the registration.
     */
    @NullMarked
    private static Provider<ArtifactoryService> registerArtifactoryService(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent(PLUGIN_NAME, ArtifactoryService.class, spec -> {
            spec.parameters(parameters -> parameters.getConfigurations().set(
                    project.provider(() -> resolveRegistries(project))
            ));
        });
    }

    /**
     * Resolves every {@link Registry} to use for the whole build's shared {@link ArtifactoryService},
     * keyed by registry name.
     * <p>
     * For each registry name declared anywhere in the build: if the root project's own extension
     * declares a fully configured registry under that name, it is always used — this is the only way
     * to make the outcome deterministic when config is instead spread across several projects (e.g.
     * via {@code subprojects { konfigyr { ... } } }), since that pattern never actually configures the
     * root project's own extension. Otherwise, every project in the build is inspected, and the
     * registries they configure under that name are compared for equality:
     * <ul>
     *     <li>if exactly one distinct registry is found for a name, it is used — this is the common
     *     case where every project configures identical connection settings;</li>
     *     <li>if more than one distinct registry is found for the same name, the build fails rather
     *     than silently picking one, since a registry's connection is shared build-wide and can only
     *     ever use a single set of credentials.</li>
     * </ul>
     * If no registry is configured anywhere in the build, the build fails since at least one is
     * required.
     *
     * @param project the project used to resolve every project in the build, cannot be {@literal null}.
     * @return every registry to use, keyed by name, never {@literal null} or empty.
     */
    @NullMarked
    private static Map<String, Registry> resolveRegistries(Project project) {
        final Map<String, Registry> rootRegistries = collectConfiguredRegistries(resolveRootExtension(project));
        final Map<String, Set<Registry>> candidatesByName = new LinkedHashMap<>();

        for (Project candidate : project.getRootProject().getAllprojects()) {
            final KonfigyrExtension extension = candidate.getExtensions().findByType(KonfigyrExtension.class);

            if (extension == null) {
                continue;
            }

            for (RegistrySpec spec : extension.getRegistries()) {
                if (spec.isConfigured()) {
                    candidatesByName.computeIfAbsent(spec.getName(), ignored -> new LinkedHashSet<>()).add(spec.toRegistry());
                }
            }
        }

        final Map<String, Registry> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, Set<Registry>> entry : candidatesByName.entrySet()) {
            final String name = entry.getKey();
            final Registry rootRegistry = rootRegistries.get(name);

            if (rootRegistry != null) {
                resolved.put(name, rootRegistry);
                continue;
            }

            final Set<Registry> candidates = entry.getValue();

            if (candidates.size() > 1) {
                throw new GradleException(
                        "Multiple projects configure different connection settings for registry '" + name + "'; " +
                        "a registry's connection is shared build-wide - configure it once, either on the root " +
                        "project or via subprojects{}/allprojects{}."
                );
            }

            resolved.put(name, candidates.iterator().next());
        }

        if (resolved.isEmpty()) {
            throw new GradleException(
                    "Konfigyr plugin requires at least one registry to be configured. Configure one via " +
                    "konfigyr { registries { konfigyrCentral() } } (on the root project, or via " +
                    "subprojects{}/allprojects{}), or konfigyr { registries { registry(\"name\") { ... } } }."
            );
        }

        return Collections.unmodifiableMap(resolved);
    }

    @NullMarked
    private static Map<String, Registry> collectConfiguredRegistries(KonfigyrExtension extension) {
        final Map<String, Registry> registries = new LinkedHashMap<>();

        for (RegistrySpec spec : extension.getRegistries()) {
            if (spec.isConfigured()) {
                registries.put(spec.getName(), spec.toRegistry());
            }
        }

        return registries;
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
            Provider<GenerateArtifactMetadataTask> generateMetadataTask,
            RegistrySpec registry
    ) {
        final String registryName = registry.getName();
        final String taskName = PublishArtifactMetadataTask.NAME + "To" + capitalize(registryName);

        return project.getTasks().register(taskName, PublishArtifactMetadataTask.class, task -> {
            task.getMetadata().set(generateMetadataTask.flatMap(GenerateArtifactMetadataTask::getMetadata));
            task.getReleaseTimeout().set(extension.getPublish().getPollTimeout().map(Duration::ofMillis));
            task.getReleasePollingInterval().set(extension.getPublish().getPollInterval().map(Duration::ofMillis));
            task.getRegistryConfigured().set(project.provider(registry::isConfigured));
            task.getRegistryName().set(registryName);

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Publishes this project's own artifact metadata directly to registry '" + registryName + "'");

            task.dependsOn(generateMetadataTask);

            task.onlyIf(
                    "registry '" + registryName + "' must be fully configured (a url and either " +
                            "clientCredentials { } or tokenExchange { }) to publish to it",
                    ignore -> task.getRegistryConfigured().get()
            );
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
            task.getServiceConfigured().set(project.provider(() -> extension.getService().isConfigured()));
            task.getServiceName().set(extension.getService().getName());

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Resolves this service's dependencies that expose Spring Boot configuration metadata");

            task.onlyIf(
                    "the service { } block must be configured to create a service release",
                    ignore -> task.getServiceConfigured().get()
            );
        });
    }

    @NullMarked
    private static Provider<CreateServiceReleaseTask> registerCreateServiceReleaseTask(
            Project project,
            KonfigyrExtension extension,
            Provider<ArtifactoryService> service,
            Provider<GenerateArtifactMetadataTask> generateMetadataTask,
            Provider<ResolveServiceDependenciesTask> resolveDependenciesTask,
            RegistrySpec registry
    ) {
        final String registryName = registry.getName();
        final String taskName = CreateServiceReleaseTask.NAME + "To" + capitalize(registryName);

        return project.getTasks().register(taskName, CreateServiceReleaseTask.class, task -> {
            task.getServiceArtifactMetadata().set(generateMetadataTask.flatMap(GenerateArtifactMetadataTask::getMetadata));
            task.getDependencyManifest().set(resolveDependenciesTask.flatMap(ResolveServiceDependenciesTask::getDependencyManifest));
            task.getDependencyDirectory().set(resolveDependenciesTask.flatMap(ResolveServiceDependenciesTask::getDependencyDirectory));
            task.getReleaseConfigured().set(project.provider(() -> extension.getService().isConfigured() && registry.isConfigured()));
            task.getServiceName().set(extension.getService().getName());
            task.getRegistryName().set(registryName);

            task.getService().set(service);
            task.usesService(service);

            task.setGroup(PLUGIN_NAME);
            task.setDescription("Creates a Service Release for this service on registry '" + registryName + "', uploading required artifact metadata");

            task.onlyIf(
                    "the service { } block must be configured, and registry '" + registryName + "' must be " +
                            "fully configured, to create a service release",
                    ignore -> task.getReleaseConfigured().get()
            );

            task.dependsOn(generateMetadataTask, resolveDependenciesTask);
        });
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }

        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
