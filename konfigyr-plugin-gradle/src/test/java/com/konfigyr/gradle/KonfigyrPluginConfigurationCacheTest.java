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
 * Extends {@link AbstractKonfigyrPluginTest} (rather than staying a plain WireMock-free test, as this
 * class originally was) specifically so {@link #assertConfigurationCacheIsReusedForServiceRelease}
 * can exercise {@code createServiceReleaseToKonfigyrCentral} - a task whose {@code onlyIf} predicate
 * reads a {@code Property<Boolean>} computed from a lambda that captures a {@link RegistrySpec} and
 * {@link KonfigyrExtension.ServiceSpec}, confirming that capturing those objects inside a
 * {@code project.provider(...)} assigned to a task property survives a configuration cache reuse, as
 * opposed to capturing them directly inside the {@code onlyIf} lambda itself.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 */
class KonfigyrPluginConfigurationCacheTest extends AbstractKonfigyrPluginTest {

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

    @Test
    @DisplayName("should reuse the configuration cache for a registry- and service-gated task across separate builds")
    void assertConfigurationCacheIsReusedForServiceRelease(@TempDir Path projectDir) throws IOException {
        FileSystemUtils.copyRecursively(ResourceUtils.loadResource("com.acme/acme").getFile(), projectDir.toFile());
        FileSystemUtils.deleteRecursively(new File(projectDir.toFile(), "build"));

        stubFactories.tokenExchangeSuccessFor(registry);
        stubPublishOwnArtifact(false);
        stubServiceRelease();

        final GradleRunner runner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments(
                        "createServiceReleaseToKonfigyrCentral",
                        "--configuration-cache",
                        "--stacktrace",
                        "-Pwiremock=" + wiremock.baseUrl()
                );

        final BuildResult first = runner.build();

        assertThat(first.getOutput()).doesNotContain("Reusing configuration cache");
        assertThat(first.task(":createServiceReleaseToKonfigyrCentral"))
                .isNotNull()
                .returns(TaskOutcome.SUCCESS, BuildTask::getOutcome);

        // createServiceReleaseTask is @DisableCachingByDefault, so it always re-executes; only the
        // *configuration* is expected to be skipped on this second invocation, hitting wiremock again
        // is expected, its stubs above are reusable (not single-shot).
        final BuildResult second = runner.build();

        assertThat(second.getOutput()).contains("Reusing configuration cache");
        assertThat(second.task(":createServiceReleaseToKonfigyrCentral"))
                .isNotNull()
                .returns(TaskOutcome.SUCCESS, BuildTask::getOutcome);
    }

}
