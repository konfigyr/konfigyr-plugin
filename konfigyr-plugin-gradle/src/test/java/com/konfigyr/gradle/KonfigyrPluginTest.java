package com.konfigyr.gradle;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.AbstractWiremockTest;
import com.konfigyr.test.ResourceUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KonfigyrPluginTest extends AbstractWiremockTest {

    final ArtifactoryConfiguration configuration = configuration()
            .namespace("konfigyr")
            .service("test-service")
            .clientId("konfigyr-client-id")
            .clientSecret("konfigyr-client-secret")
            .build();

    private GradleRunner runner;

    @BeforeEach
    void setup() throws IOException {
        runner = GradleRunner.create()
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(ResourceUtils.loadResource("com.acme/acme").getFile());
    }

    @Test
    @Order(1)
    @DisplayName("should apply Konfigyr plugin and create tasks")
    void assertPluginApplied() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply("com.konfigyr.publish-manifest");

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
            ext.getNamespace().set("konfigyr");
            ext.getClientId().set("client_id");
            ext.getClientSecret().set("client_secret");
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
                .returns(Set.of(GenerateArtifactMetadataTask.NAME), Task::getDependsOn);
    }

    @Test
    @Order(2)
    @DisplayName("should execute the configured Konfigyr upload task")
    void assertPluginExecuted() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(configuration, "test-service-manifest.json");
        stubFactories.publishResponseFor(configuration, "test-service-manifest.json");

        final var json = JsonNodeFactory.instance.objectNode()
                .put("groupId", "{{jsonPath request.body '$.groupId'}}")
                .put("artifactId", "{{jsonPath request.body '$.artifactId'}}")
                .put("version", "{{jsonPath request.body '$.version'}}")
                .put("state", ReleaseState.PENDING.name())
                .put("checksum", "{{jsonPath request.body '$.checksum'}}")
                .putPOJO("errors", Collections.emptyList())
                .put("releaseDate", Instant.now().toString());

        stubFactories.getReleaseExistsResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                false
        );

        stubFactories.createReleaseResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                WireMock.jsonResponse(json.toPrettyString(), 200)
        );

        stubFactories.getReleaseResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                WireMock.jsonResponse(json.put("state", ReleaseState.RELEASED.name()).toPrettyString(), 200)
        );

        BuildResult result = runner
                .withArguments("clean", "konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl(), "-Pspring=3.5.7")
                .build();

        assertThat(result.tasks(TaskOutcome.SUCCESS))
                .as("Konfigyr tasks should be successfully executed")
                .extracting(BuildTask::getPath)
                .contains(
                        ":generateArtifactMetadata",
                        ":publishArtifactMetadata",
                        ":konfigyr"
                );

        wiremock.verify(WireMock.postRequestedFor(urlPathEqualTo("/oauth/token")));
        wiremock.verify(1, WireMock.getRequestedFor(urlPathEqualTo("/namespaces/konfigyr/services/test-service/manifest")));
        wiremock.verify(1, WireMock.postRequestedFor(urlPathEqualTo("/namespaces/konfigyr/services/test-service/manifest")));
        wiremock.verify(2, WireMock.headRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
        wiremock.verify(2, WireMock.postRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
        wiremock.verify(2, WireMock.getRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
        wiremock.verify(0, WireMock.anyRequestedFor(urlPathEqualTo("/artifacts/org.springframework.boot/spring-boot-autoconfigure/3.5.7")));
    }

    @Test
    @Order(3)
    @DisplayName("should not execute generate metadata task when classpath is not changed")
    void assertGenerateMetadataTaskIsCached() {
        BuildResult result = runner
                .withArguments("generateArtifactMetadata", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.UP_TO_DATE))
                .as("Generate artifact metadata tasks should be cached")
                .extracting(BuildTask::getPath)
                .contains(":generateArtifactMetadata");

        wiremock.verify(0, WireMock.anyRequestedFor(anyUrl()));
    }

    @Test
    @Order(3)
    @DisplayName("should not release artifact metadata when they are present in Artifactory")
    void ignoreReleasedArtifacts() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(configuration, "test-service-manifest.json");
        stubFactories.publishResponseFor(configuration, "test-service-manifest.json");

        stubFactories.getReleaseExistsResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                true
        );

        BuildResult result = runner
                .withArguments("konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.SUCCESS))
                .as("Publish artifact metadata task should not be cached")
                .extracting(BuildTask::getPath)
                .contains(":publishArtifactMetadata", ":konfigyr");

        wiremock.verify(WireMock.postRequestedFor(urlPathEqualTo("/oauth/token")));
        wiremock.verify(1, WireMock.getRequestedFor(urlPathEqualTo("/namespaces/konfigyr/services/test-service/manifest")));
        wiremock.verify(1, WireMock.postRequestedFor(urlPathEqualTo("/namespaces/konfigyr/services/test-service/manifest")));
        wiremock.verify(2, WireMock.headRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
        wiremock.verify(0, WireMock.postRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
        wiremock.verify(0, WireMock.getRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
    }

    @Test
    @Order(4)
    @DisplayName("should execute the configured Konfigyr upload task in Gradle multi project setup")
    void assertPluginExecutedInMultiproject() throws IOException {
        stubFactories.tokenExchangeSuccessFor(configuration);

        final var manifest = ResourceUtils.readResource("test-service-manifest.json");

        wiremock.stubFor(
                get(urlPathTemplate("/namespaces/{namespace}/services/{service}/manifest"))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(jsonResponse(manifest, 200).withHeader("Content-Type", "application/json"))
        );

        wiremock.stubFor(
                post(urlPathTemplate("/namespaces/{namespace}/services/{service}/manifest"))
                        .withHeader("Authorization", matching("^Bearer\\s+([a-zA-Z0-9-._~+/]+=*)$"))
                        .willReturn(jsonResponse(manifest, 200).withHeader("Content-Type", "application/json"))
        );

        stubFactories.getReleaseExistsResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                false
        );

        final var json = JsonNodeFactory.instance.objectNode()
                .put("groupId", "{{jsonPath request.body '$.groupId'}}")
                .put("artifactId", "{{jsonPath request.body '$.artifactId'}}")
                .put("version", "{{jsonPath request.body '$.version'}}")
                .put("state", ReleaseState.PENDING.name())
                .put("checksum", "{{jsonPath request.body '$.checksum'}}")
                .putPOJO("errors", Collections.emptyList())
                .put("releaseDate", Instant.now().toString());

        stubFactories.createReleaseResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                WireMock.jsonResponse(json.toPrettyString(), 200)
        );

        stubFactories.getReleaseResponseFor(
                urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}"),
                WireMock.jsonResponse(json.put("state", ReleaseState.RELEASED.name()).toPrettyString(), 200)
        );

        BuildResult result = runner
                .withProjectDir(ResourceUtils.loadResource("com.acme.multiproject").getFile())
                .withArguments("clean", "konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertThat(result.tasks(TaskOutcome.SUCCESS))
                .extracting(BuildTask::getPath)
                .contains(
                        ":core:generateArtifactMetadata",
                        ":core:publishArtifactMetadata",
                        ":core:konfigyr",
                        ":customers:generateArtifactMetadata",
                        ":customers:publishArtifactMetadata",
                        ":customers:konfigyr",
                        ":inventory:generateArtifactMetadata",
                        ":inventory:publishArtifactMetadata",
                        ":inventory:konfigyr",
                        ":orders:generateArtifactMetadata",
                        ":orders:publishArtifactMetadata",
                        ":orders:konfigyr"
                );
    }

}