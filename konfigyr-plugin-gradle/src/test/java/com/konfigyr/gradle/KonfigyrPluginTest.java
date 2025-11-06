package com.konfigyr.gradle;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.konfigyr.ArtifactoryConfiguration;
import com.konfigyr.artifactory.ReleaseState;
import com.konfigyr.test.AbstractWiremockTest;
import com.konfigyr.test.ResourceUtils;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
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
        project.getPluginManager().apply("com.konfigyr");

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
                .isNotNull()
                .returns(KonfigyrPlugin.PLUGIN_NAME, NamedDomainObjectProvider::getName)
                .returns(true, NamedDomainObjectProvider::isPresent);

        project.getTasks().withType(KonfigyrUploadTask.class, task -> {
            assertThat(task.getConfiguration().get())
                    .returns(ArtifactoryConfiguration.DEFAULT_HOST, ArtifactoryConfiguration::host)
                    .returns(ArtifactoryConfiguration.DEFAULT_TOKEN_URI, ArtifactoryConfiguration::tokenUri)
                    .returns("konfigyr", ArtifactoryConfiguration::namespace)
                    .returns(project.getName(), ArtifactoryConfiguration::service)
                    .returns("client_id", ArtifactoryConfiguration::clientId)
                    .returns("client_secret", ArtifactoryConfiguration::clientSecret)
                    .returns("konfigyr-plugin", ArtifactoryConfiguration::userAgent)
                    .returns(Duration.ofSeconds(10), ArtifactoryConfiguration::connectTimeout)
                    .returns(Duration.ofSeconds(30), ArtifactoryConfiguration::readTimeout);

            assertThat(task.getArtifacts().get())
                    .isNotNull();

            assertThat(task.getClasspath().get())
                    .isNotEmpty();

            assertThat(task.getWorkerExecutor())
                    .isNotNull();

            assertThat(task.getArtifactoryService())
                    .isNotNull();
        });
    }

    @Test
    @Order(2)
    @DisplayName("should execute the configured Konfigyr upload task")
    void assertPluginExecuted() {
        stubFactories.tokenExchangeSuccessFor(configuration);
        stubFactories.manifestResponseFor(configuration, "test-service-manifest.json");

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
                .withArguments("clean", "konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl(), "-Pspring=3.5.7")
                .build();

        assertBuildResult(result, TaskOutcome.SUCCESS);

        wiremock.verify(WireMock.postRequestedFor(urlPathEqualTo("/oauth/token")));
        wiremock.verify(2, WireMock.getRequestedFor(urlPathEqualTo("/namespaces/konfigyr/test-service/manifest")));
        wiremock.verify(3, WireMock.postRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
        wiremock.verify(3, WireMock.getRequestedFor(urlPathTemplate("/artifacts/{groupId}/{artifactId}/{version}")));
    }

    @Test
    @Order(3)
    @DisplayName("should not execute the Konfigyr upload task as it is up to date")
    void assertPluginIsCached() {
        BuildResult result = runner
                .withArguments("konfigyr", "--stacktrace", "--info", "-Pwiremock=" + wiremock.baseUrl())
                .build();

        assertBuildResult(result, TaskOutcome.UP_TO_DATE);

        wiremock.verify(0, RequestPatternBuilder.allRequests());
    }

    static void assertBuildResult(BuildResult result, TaskOutcome outcome) {
        final var task = result.task(":konfigyr");

        assertThat(task)
                .as("Konfigyr task should be present")
                .isNotNull();

        assertThat(task.getOutcome())
                .as("Konfigyr task outcome should be %s but was: %s", outcome, task.getOutcome())
                .isEqualTo(outcome);
    }

}