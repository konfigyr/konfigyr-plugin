package com.konfigyr.test;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.konfigyr.ArtifactoryConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.http.HttpClient;

/**
 * Abstract test class that registers a customized {@link WireMockExtension} and creates the
 * {@link StubFactories} for easier programmatic stubbing.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see WireMockExtension
 */
public class AbstractWiremockTest {

    @RegisterExtension
    protected static WireMockExtension wiremock = WireMockExtension.newInstance()
            .resetOnEachTest(true)
            .options(WireMockConfiguration.options()
                    .dynamicPort()
                    .globalTemplating(true)
                    .templatingEnabled(true)
                    .notifier(new Slf4jNotifier(true))
            )
            .build();

    protected final StubFactories stubFactories = new StubFactories(wiremock);

    /**
     * Creates the {@link ArtifactoryConfiguration.Builder} with the {@code host} and {@code tokenUri}
     * parameters extracted from the {@link com.github.tomakehurst.wiremock.client.WireMock} base URI.
     *
     * @return the Artifactory configuration builder, never {@literal null}.
     */
    protected ArtifactoryConfiguration.Builder configuration() {
        return ArtifactoryConfiguration.builder()
                .host(wiremock.baseUrl())
                .tokenUri(wiremock.baseUrl() + "/oauth/token");
    }

    /**
     * Creates the {@link HttpClient.Builder} using the specified {@link ArtifactoryConfiguration}.
     *
     * @return the test HTTP client builder, never {@literal null}.
     */
    protected HttpClient.Builder createHttpClient(ArtifactoryConfiguration configuration) {
        return HttpClient.newBuilder()
                .connectTimeout(configuration.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1);
    }

}
