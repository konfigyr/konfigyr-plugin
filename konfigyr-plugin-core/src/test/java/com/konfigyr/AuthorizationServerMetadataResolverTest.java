package com.konfigyr;

import com.konfigyr.test.AbstractWiremockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.net.URI;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class AuthorizationServerMetadataResolverTest extends AbstractWiremockTest {

    AuthorizationServerMetadataResolver resolver;

    @BeforeEach
    void setup() {
        resolver = new AuthorizationServerMetadataResolver(JsonMapper.shared(), new Transport(TransportOptions.DEFAULT));
    }

    private static Registry registryFor(URI url) {
        return registryFor(url, false);
    }

    private static Registry registryFor(URI url, boolean insecure) {
        return Registry.builder()
                .host(url)
                .credentials(new ClientCredentials("client-id", "client-secret"))
                .insecure(insecure)
                .build();
    }

    @Test
    @DisplayName("should resolve metadata directly from the registry when Protected Resource Metadata is absent")
    void resolvesDirectlyWhenProtectedResourceMetadataIsAbsent() {
        final URI registryUrl = URI.create(wiremock.baseUrl());

        final var metadata = resolver.resolve(registryFor(registryUrl));

        assertThat(metadata.issuer()).isEqualTo(registryUrl);
        assertThat(metadata.tokenEndpoint()).isEqualTo(URI.create(wiremock.baseUrl() + "/oauth/token"));

        wiremock.verify(getRequestedFor(urlPathEqualTo("/.well-known/oauth-protected-resource")));
        wiremock.verify(getRequestedFor(urlPathEqualTo("/.well-known/oauth-authorization-server")));
    }

    @Test
    @DisplayName("should follow Protected Resource Metadata to a different Authorization Server issuer")
    void followsProtectedResourceMetadataToAuthorizationServer() {
        final URI registryUrl = URI.create(wiremock.baseUrl());
        final URI issuer = URI.create(wiremock.baseUrl() + "/idp");

        final var protectedResourceMetadataNode = JsonNodeFactory.instance.objectNode();
        protectedResourceMetadataNode.putArray("authorization_servers").add(issuer.toString());
        final String protectedResourceMetadata = protectedResourceMetadataNode.toPrettyString();

        wiremock.stubFor(get(urlPathEqualTo("/.well-known/oauth-protected-resource"))
                .willReturn(jsonResponse(protectedResourceMetadata, 200)));

        final String authorizationServerMetadata = JsonNodeFactory.instance.objectNode()
                .put("issuer", issuer.toString())
                .put("token_endpoint", issuer + "/oauth/token")
                .toPrettyString();

        wiremock.stubFor(get(urlPathEqualTo("/.well-known/oauth-authorization-server/idp"))
                .willReturn(jsonResponse(authorizationServerMetadata, 200)));

        final var metadata = resolver.resolve(registryFor(registryUrl));

        assertThat(metadata.issuer()).isEqualTo(issuer);
        assertThat(metadata.tokenEndpoint()).isEqualTo(URI.create(issuer + "/oauth/token"));

        wiremock.verify(getRequestedFor(urlPathEqualTo("/.well-known/oauth-authorization-server/idp")));
    }

    @Test
    @DisplayName("should insert the well-known segment before the registry's own path")
    void insertsWellKnownSegmentBeforeExistingPath() {
        final URI registryUrl = URI.create(wiremock.baseUrl() + "/tenant1");
        final String metadata = JsonNodeFactory.instance.objectNode()
                .put("issuer", registryUrl.toString())
                .put("token_endpoint", registryUrl + "/oauth/token")
                .toPrettyString();

        wiremock.stubFor(get(urlPathEqualTo("/.well-known/oauth-authorization-server/tenant1"))
                .willReturn(jsonResponse(metadata, 200)));

        final var resolved = resolver.resolve(registryFor(registryUrl));

        assertThat(resolved.issuer()).isEqualTo(registryUrl);
        assertThat(resolved.tokenEndpoint()).isEqualTo(URI.create(registryUrl + "/oauth/token"));
    }

    @Test
    @DisplayName("should treat a returned issuer with a redundant trailing slash as matching the expected issuer")
    void toleratesRedundantTrailingSlashOnRootIssuer() {
        final URI registryUrl = URI.create(wiremock.baseUrl());
        final String metadata = JsonNodeFactory.instance.objectNode()
                .put("issuer", registryUrl + "/")
                .put("token_endpoint", registryUrl + "/oauth/token")
                .toPrettyString();

        // overrides the default root stub registered by AbstractWiremockTest's @BeforeEach, whose
        // issuer claim carries no trailing slash - here it does, on purpose
        wiremock.stubFor(get(urlPathEqualTo("/.well-known/oauth-authorization-server"))
                .willReturn(jsonResponse(metadata, 200)));

        final var resolved = resolver.resolve(registryFor(registryUrl));

        assertThat(resolved.issuer()).isEqualTo(registryUrl);
        assertThat(resolved.tokenEndpoint()).isEqualTo(URI.create(registryUrl + "/oauth/token"));
    }

    @Test
    @DisplayName("should reject an Authorization Server Metadata document whose issuer does not match")
    void rejectsIssuerMismatch() {
        final URI registryUrl = URI.create(wiremock.baseUrl() + "/tenantA");
        final URI wrongIssuer = URI.create(wiremock.baseUrl() + "/wrong-issuer");
        final String metadata = JsonNodeFactory.instance.objectNode()
                .put("issuer", wrongIssuer.toString())
                .put("token_endpoint", wrongIssuer + "/oauth/token")
                .toPrettyString();

        wiremock.stubFor(get(urlPathEqualTo("/.well-known/oauth-authorization-server/tenantA"))
                .willReturn(jsonResponse(metadata, 200)));

        assertThatIllegalStateException()
                .isThrownBy(() -> resolver.resolve(registryFor(registryUrl)))
                .withMessageContaining("does not match the expected issuer");
    }

    @Test
    @DisplayName("should fail when the Authorization Server Metadata document is missing the token endpoint")
    void failsWhenTokenEndpointMissing() {
        final URI registryUrl = URI.create(wiremock.baseUrl() + "/tenantB");
        final String metadata = JsonNodeFactory.instance.objectNode()
                .put("issuer", registryUrl.toString())
                .toPrettyString();

        wiremock.stubFor(get(urlPathEqualTo("/.well-known/oauth-authorization-server/tenantB"))
                .willReturn(jsonResponse(metadata, 200)));

        assertThatIllegalStateException()
                .isThrownBy(() -> resolver.resolve(registryFor(registryUrl)))
                .withMessageContaining("missing required field 'token_endpoint'");
    }

    @Test
    @DisplayName("should fail when Protected Resource Metadata responds with a server error")
    void failsWhenProtectedResourceMetadataErrors() {
        final URI registryUrl = URI.create(wiremock.baseUrl() + "/tenantC");

        wiremock.stubFor(get(urlPathEqualTo("/.well-known/oauth-protected-resource/tenantC"))
                .willReturn(aResponse().withStatus(500)));

        assertThatIllegalStateException()
                .isThrownBy(() -> resolver.resolve(registryFor(registryUrl)))
                .withMessageContaining("unexpected HTTP status code 500");
    }

    @Test
    @DisplayName("should reject a non-HTTPS registry URL that is not a loopback address")
    void rejectsNonHttpsNonLoopback() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(registryFor(URI.create("http://example.com"))))
                .withMessageContaining("must use HTTPS");
    }

    @Test
    @DisplayName("should allow a non-HTTPS, non-loopback URL when insecure is true")
    void allowsNonHttpsNonLoopbackWhenInsecure() {
        assertThatCode(() -> AuthorizationServerMetadataResolver.assertHttps(URI.create("http://example.com"), true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should cache resolved metadata until the configured TTL elapses")
    void cachesResolvedMetadataUntilTtlElapses() throws InterruptedException {
        final AuthorizationServerMetadataResolver shortLivedResolver = new AuthorizationServerMetadataResolver(
                JsonMapper.shared(), new Transport(TransportOptions.DEFAULT), Duration.ofMillis(50)
        );
        final URI registryUrl = URI.create(wiremock.baseUrl());
        final Registry registry = registryFor(registryUrl);

        shortLivedResolver.resolve(registry);
        shortLivedResolver.resolve(registry);

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/.well-known/oauth-authorization-server")));

        Thread.sleep(100);

        shortLivedResolver.resolve(registry);

        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/.well-known/oauth-authorization-server")));
    }

}
