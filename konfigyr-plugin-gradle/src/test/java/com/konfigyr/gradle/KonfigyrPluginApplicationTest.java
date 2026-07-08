package com.konfigyr.gradle;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Konfigyr plugin registers its extension, shared build service and tasks when applied,
 * without exercising a real Gradle build, unlike the other {@code KonfigyrPlugin*Test} classes, this
 * one never touches {@link org.gradle.testkit.runner.GradleRunner} or WireMock.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
class KonfigyrPluginApplicationTest {

    @Test
    @DisplayName("should apply Konfigyr plugin and create tasks")
    void assertPluginApplied() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply("com.konfigyr.artifactory");

        assertThat(project.getExtensions())
                .isNotNull()
                .satisfies(it -> assertThat(it.findByType(KonfigyrExtension.class))
                        .isNotNull()
                );

        assertThat(project.getTasks())
                .isNotNull()
                .extracting(Task::getName)
                .containsAnyOf("konfigyr");

        project.getExtensions().configure(KonfigyrExtension.class, ext -> {
            ext.getService().getNamespace().set("konfigyr");
            ext.clientCredentials(clientCredentials -> {
                clientCredentials.getClientId().set("client_id");
                clientCredentials.getClientSecret().set("client_secret");
            });
        });

        assertThat(project.getGradle().getSharedServices().getRegistrations().named(KonfigyrPlugin.PLUGIN_NAME))
                .as("Should register %s shared service", KonfigyrPlugin.PLUGIN_NAME)
                .isNotNull()
                .returns(KonfigyrPlugin.PLUGIN_NAME, NamedDomainObjectProvider::getName)
                .returns(true, NamedDomainObjectProvider::isPresent);

        assertThat(project.getTasks().getByName(GenerateArtifactMetadataTask.NAME))
                .as("Should register %s task", GenerateArtifactMetadataTask.NAME)
                .isNotNull()
                .returns(KonfigyrPlugin.PLUGIN_NAME, Task::getGroup)
                .extracting(Task::getDependsOn, InstanceOfAssertFactories.iterable(TaskProvider.class))
                .extracting(TaskProvider::getName)
                .containsExactlyInAnyOrder(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaPlugin.JAR_TASK_NAME);

        assertThat(project.getTasks().getByName(PublishArtifactMetadataTask.NAME))
                .as("Should register %s task", PublishArtifactMetadataTask.NAME)
                .isNotNull()
                .returns(KonfigyrPlugin.PLUGIN_NAME, Task::getGroup)
                .extracting(Task::getDependsOn, InstanceOfAssertFactories.iterable(TaskProvider.class))
                .extracting(TaskProvider::getName)
                .containsExactlyInAnyOrder(GenerateArtifactMetadataTask.NAME);

        assertThat(project.getTasks().getByName(ResolveServiceDependenciesTask.NAME))
                .as("Should register %s task", ResolveServiceDependenciesTask.NAME)
                .isNotNull()
                .returns(KonfigyrPlugin.PLUGIN_NAME, Task::getGroup);

        assertThat(project.getTasks().getByName(CreateServiceReleaseTask.NAME))
                .as("Should register %s task", CreateServiceReleaseTask.NAME)
                .isNotNull()
                .returns(KonfigyrPlugin.PLUGIN_NAME, Task::getGroup)
                .extracting(Task::getDependsOn, InstanceOfAssertFactories.iterable(TaskProvider.class))
                .extracting(TaskProvider::getName)
                .containsExactlyInAnyOrder(GenerateArtifactMetadataTask.NAME, ResolveServiceDependenciesTask.NAME);

        assertThat(project.getTasks().getByName(KonfigyrPlugin.PLUGIN_NAME))
                .as("Should register %s task", KonfigyrPlugin.PLUGIN_NAME)
                .extracting(Task::getDependsOn, InstanceOfAssertFactories.iterable(TaskProvider.class))
                .extracting(TaskProvider::getName)
                .containsExactlyInAnyOrder(PublishArtifactMetadataTask.NAME, CreateServiceReleaseTask.NAME);
    }

}
