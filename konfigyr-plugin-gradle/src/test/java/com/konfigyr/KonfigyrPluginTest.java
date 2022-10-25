package com.konfigyr;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.konfigyr.test.TestFactories;
import org.apache.hc.core5.http.HttpHeaders;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : vladimir.spasic@ebf.com
 * @since : 09.10.22, Sun
 **/
@WireMockTest(httpPort = 9999)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KonfigyrPluginTest {

    private GradleRunner runner;

    @BeforeEach
    void setup() throws IOException {
        runner = GradleRunner.create()
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(TestFactories.loadResource("com.acme/acme"));
    }

    @Test
    @Order(1)
    void assertPluginApplied(WireMockRuntimeInfo info) {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("com.konfigyr");

        assertThat(project.getExtensions())
                .isNotNull()
                .extracting(it -> it.findByName("konfigyr"))
                .isNotNull();

        assertThat(project.getTasks())
                .isNotNull()
                .extracting(Task::getName)
                .containsAnyOf("konfigyr");

        project.getExtensions().configure(KonfigyrExtension.class, ext -> {
            ext.getHost().set(info.getHttpBaseUrl());
            ext.getToken().set("token");
            ext.getToken().set("token");
        });

        project.getTasks().withType(KonfigyrUploadTask.class, task -> {
            assertThat(task)
                    .returns(info.getHttpBaseUrl(), it -> it.getHost().get())
                    .returns("token", it -> it.getToken().get())
                    .returns(
                            new File(project.getBuildDir(), "konfigyr/konfigyr-metadata.json"),
                            it -> it.getOutput().get().getAsFile()
                    );
        });
    }

    @Test
    @Order(2)
    public void assertPluginExecuted(WireMockRuntimeInfo info) {
        registerMappingFor(info, "/upload/com.acme/acme/1.0.0");
        registerMappingFor(info, "/upload/org.springframework.boot/spring-boot/2.7.4");
        registerMappingFor(info, "/upload/org.springframework.boot/spring-boot-autoconfigure/2.7.4");
        registerMappingFor(info, "/upload/org.zalando/logbook-spring-boot-autoconfigure/2.14.0");

        BuildResult result = runner
                .withArguments("clean", "konfigyr", "--stacktrace")
                .build();

        assertThat(result.task(":konfigyr"))
                .isNotNull()
                .returns(TaskOutcome.SUCCESS, BuildTask::getOutcome);

        verifyMappingFor("/upload/com.acme/acme/1.0.0");
        verifyMappingFor("/upload/org.springframework.boot/spring-boot/2.7.4");
        verifyMappingFor("/upload/org.springframework.boot/spring-boot-autoconfigure/2.7.4");
        verifyMappingFor("/upload/org.zalando/logbook-spring-boot-autoconfigure/2.14.0");
    }

    @Test
    @Order(3)
    public void assertPluginIsCached() {
        BuildResult result = runner
                .withArguments("konfigyr", "--info", "--stacktrace")
                .build();

        assertThat(result.task(":konfigyr"))
                .isNotNull()
                .returns(TaskOutcome.UP_TO_DATE, BuildTask::getOutcome);

        verify(0, RequestPatternBuilder.allRequests());
    }

    private static void registerMappingFor(WireMockRuntimeInfo info, String path) {
        info.getWireMock().register(
                post(urlPathEqualTo(path))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalToIgnoreCase("Bearer test-access-token"))
                        .withHost(equalToIgnoreCase("localhost"))
                        .withPort(info.getHttpPort())
                        .withScheme("http")
                        .willReturn(
                                okJson("{}").withUniformRandomDelay(200, 600)
                        )
        );
    }

    private static void verifyMappingFor(String path) {
        verify(RequestPatternBuilder.newRequestPattern(RequestMethod.POST, urlPathEqualTo(path)));
    }

}