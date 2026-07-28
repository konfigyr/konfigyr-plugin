package com.konfigyr.test;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.konfigyr.ArtifactoryClient;
import com.konfigyr.ArtifactoryClientFactory;
import com.konfigyr.Registry;
import com.konfigyr.TransportOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;

/**
 * Abstract test class that registers a customized {@link WireMockExtension} and creates the
 * {@link StubFactories} for easier programmatic stubbing.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see WireMockExtension
 */
public class AbstractWiremockTest {

    /**
     * The Wiremock extension used to create a local Artifactory instance for testing.
     */
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

    /**
     * Wiremock stub factories used to create Konfigyr Artifactory stubs.
     */
    protected final StubFactories stubFactories = new StubFactories(wiremock);

    /**
     * Stubs the OAuth2 Authorization Server Metadata document that every {@link Registry} built by
     * {@link #registry()} is discovered from, pointing its token endpoint at {@code /oauth/token} on
     * this test's wiremock instance. Registered fresh before every test, since WireMock resets its
     * stubs between tests.
     */
    @BeforeEach
    void stubAuthorizationServerMetadata() {
        stubFactories.authorizationServerMetadataFor(URI.create(wiremock.baseUrl()));
    }

    /**
     * Creates the {@link Registry.Builder} with the {@code host} pointing at this test's wiremock
     * instance.
     *
     * @return the registry builder, never {@literal null}.
     */
    protected Registry.Builder registry() {
        return Registry.builder().host(wiremock.baseUrl());
    }

    /**
     * Creates an {@link ArtifactoryClientFactory} using default {@link TransportOptions}, for tests
     * that need a real {@link ArtifactoryClient} rather than exercising lower-level collaborators
     * directly.
     *
     * @return the client factory, never {@literal null}.
     */
    protected ArtifactoryClientFactory clientFactory() {
        return new ArtifactoryClientFactory(TransportOptions.DEFAULT);
    }

}
