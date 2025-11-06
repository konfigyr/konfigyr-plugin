package com.konfigyr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.konfigyr.test.AbstractWiremockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.*;

class OAuthClientCredentialsProviderTest extends AbstractWiremockTest {

    final ArtifactoryConfiguration configuration = configuration()
            .clientId("test-plugin-client-id")
            .clientSecret("client-secret")
            .service("test-service")
            .namespace("test-namespace")
            .build();

    final HttpClient httpClient = createHttpClient(configuration)
            .build();

    OAuthClientCredentialsProvider provider;

    @BeforeEach
    void setup() {
        provider = new OAuthClientCredentialsProvider(httpClient, configuration);
    }

    @Test
    @DisplayName("should obtain OAuth Access Token from the supplied token exchange URI and client credentials")
    void shouldObtainAccessToken() {
        stubFactories.tokenExchangeSuccessFor(configuration);

        assertThat(provider.getAccessToken())
                .isEqualTo("oauth-access-token");

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("should not refresh OAuth Access Token when it is not expired")
    void shouldNotRefreshAccessToken() {
        stubFactories.tokenExchangeSuccessFor(configuration);

        assertThat(provider.getAccessToken())
                .isEqualTo("oauth-access-token")
                .isEqualTo(provider.getAccessToken());

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("should refresh OAuth Access Token when if it is expired")
    void shouldRefreshAccessToken() throws Exception {
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
        stubFactories.tokenExchangeResponseFor(configuration, WireMock.jsonResponse("{}", 200));

        assertThatIllegalStateException()
                .isThrownBy(provider::getAccessToken)
                .withMessageContaining("Failed to extract OAuth2 access token from server response")
                .withNoCause();

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

}