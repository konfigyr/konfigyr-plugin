package com.konfigyr.gradle;

import com.konfigyr.test.ResourceUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Konfigyr plugin is compatible with the Gradle configuration cache: a build using it
 * should be reusable across separate Gradle invocations without reconfiguring the project.
 * <p>
 * {@code generateArtifactMetadata} never performs a network call, so this test needs no WireMock
 * server at all - the {@code -Pwiremock=} property below only needs to be a syntactically valid URL,
 * to satisfy the fixture's {@code konfigyr { host = "${wiremock}" } } interpolation at configuration
 * time. Unlike the other {@code KonfigyrPlugin*Test} classes, this one doesn't extend
 * {@link com.konfigyr.test.AbstractWiremockTest}.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
class KonfigyrPluginConfigurationCacheTest {

    @Test
    @DisplayName("should reuse the configuration cache across separate builds")
    void assertConfigurationCacheIsReused(@TempDir Path projectDir) throws IOException {
        // copy into an isolated directory rather than pointing at the shared fixture resource
        // directory the other KonfigyrPlugin*Test classes build against, then drop any leftover
        // build output so this test starts from a genuinely clean slate regardless of what else ran
        FileSystemUtils.copyRecursively(ResourceUtils.loadResource("com.acme/acme").getFile(), projectDir.toFile());
        FileSystemUtils.deleteRecursively(new File(projectDir.toFile(), "build"));

        final GradleRunner runner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments(
                        "generateArtifactMetadata",
                        "--configuration-cache",
                        "--stacktrace",
                        "-Pwiremock=http://localhost:1"
                );

        final BuildResult first = runner.build();

        assertThat(first.getOutput()).doesNotContain("Reusing configuration cache");
        assertThat(first.task(":generateArtifactMetadata"))
                .isNotNull()
                .returns(TaskOutcome.SUCCESS, BuildTask::getOutcome);

        final BuildResult second = runner.build();

        assertThat(second.getOutput()).contains("Reusing configuration cache");
        assertThat(second.task(":generateArtifactMetadata"))
                .isNotNull()
                .returns(TaskOutcome.UP_TO_DATE, BuildTask::getOutcome);
    }

}
