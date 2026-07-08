package com.konfigyr;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.konfigyr.test.AbstractWiremockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.UncheckedIOException;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.*;

class OAuthClientCredentialsProviderTest extends AbstractWiremockTest {

    static final Credentials clientCredentials = new ClientCredentials("test-plugin-client-id", "client-secret");
    static final Credentials tokenExchange = new TokenExchange("test-plugin-client-id", "test-subject-token");

    @Test
    @DisplayName("should obtain OAuth Access Token from the supplied token exchange URI using client credentials grant")
    void shouldObtainAccessToken() {
        final var configuration = createConfiguration(clientCredentials);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeSuccessFor(configuration);

        assertThat(provider.getAccessToken())
                .isEqualTo("oauth-access-token");

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token"))
                .withFormParam("grant_type", WireMock.equalTo("client_credentials"))
                .withFormParam("client_id", WireMock.equalTo("test-plugin-client-id"))
                .withFormParam("client_secret", WireMock.equalTo("client-secret"))
                .withFormParam("scope", WireMock.equalTo("artifactory:publish namespaces:publish-releases")));
    }

    @Test
    @DisplayName("should obtain OAuth Access Token using the Token Exchange grant")
    void shouldObtainAccessTokenUsingTokenExchange() {
        final var configuration = createConfiguration(tokenExchange);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeSuccessFor(configuration);

        assertThat(provider.getAccessToken())
                .isEqualTo("oauth-access-token");

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token"))
                .withFormParam("grant_type", WireMock.equalTo("urn:ietf:params:oauth:grant-type:token-exchange"))
                .withFormParam("client_id", WireMock.equalTo("test-plugin-client-id"))
                .withFormParam("subject_token", WireMock.equalTo("test-subject-token"))
                .withFormParam("subject_token_type", WireMock.equalTo("urn:ietf:params:oauth:token-type:jwt"))
                .withFormParam("scope", WireMock.equalTo("artifactory:publish namespaces:publish-releases")));
    }

    @Test
    @DisplayName("should not refresh OAuth Access Token when it is not expired")
    void shouldNotRefreshAccessToken() {
        final var configuration = createConfiguration(clientCredentials);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeSuccessFor(configuration);

        assertThat(provider.getAccessToken())
                .isEqualTo("oauth-access-token")
                .isEqualTo(provider.getAccessToken());

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("should refresh OAuth Access Token when if it is expired")
    void shouldRefreshAccessToken() throws Exception {
        final var configuration = createConfiguration(clientCredentials);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeSuccessFor(configuration, 61);

        assertThat(provider.getAccessToken())
                .isEqualTo("oauth-access-token");

        // wait for it to expire...
        Thread.sleep(1000);

        assertThat(provider.getAccessToken())
                .isEqualTo("oauth-access-token");

        wiremock.verify(2, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("should fail to obtain OAuth Access Token with invalid credentials")
    void shouldNotObtainAccessToken() {
        final var configuration = createConfiguration(clientCredentials);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeErrorFor(configuration);

        assertThatExceptionOfType(HttpResponseException.class)
                .isThrownBy(provider::getAccessToken)
                .withMessageContaining("Could not obtain OAuth2 access token due to server error response")
                .withMessageContaining("\"error\" : \"invalid_client\"")
                .withNoCause();

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("should fail to obtain OAuth Access Token due to connection error")
    void shouldCatchConnectionErrors() {
        final var configuration = createConfiguration(clientCredentials);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeResponseFor(configuration, WireMock.aResponse()
                .withFault(Fault.RANDOM_DATA_THEN_CLOSE));

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(provider::getAccessToken)
                .withMessageContaining("Error occurred while establishing connection")
                .withCauseInstanceOf(IOException.class);

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("should fail to obtain OAuth Access Token with invalid response")
    void shouldReadInvalidAccessTokenResponse() {
        final var configuration = createConfiguration(clientCredentials);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeResponseFor(configuration, WireMock.jsonResponse("invalid JSON", 200));

        assertThatIllegalStateException()
                .isThrownBy(provider::getAccessToken)
                .withMessageContaining("Failed to extract OAuth2 access token from server response")
                .withCauseInstanceOf(JacksonException.class);

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("should fail to obtain OAuth Access Token with invalid response")
    void shouldReadEmptyAccessTokenResponse() {
        final var configuration = createConfiguration(tokenExchange);
        final var provider = createProviderFor(configuration);

        stubFactories.tokenExchangeResponseFor(configuration, WireMock.jsonResponse("{}", 200));

        assertThatIllegalStateException()
                .isThrownBy(provider::getAccessToken)
                .withMessageContaining("Failed to extract OAuth2 access token from server response")
                .withNoCause();

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    ArtifactoryConfiguration createConfiguration(Credentials credentials) {
        return configuration().credentials(credentials).build();
    }

    OAuthClientCredentialsProvider createProviderFor(ArtifactoryConfiguration configuration) {
        return new OAuthClientCredentialsProvider(createHttpClient(configuration).build(), configuration);
    }

}