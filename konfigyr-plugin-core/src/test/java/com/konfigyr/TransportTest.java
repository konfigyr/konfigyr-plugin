package com.konfigyr;

import com.github.tomakehurst.wiremock.http.Fault;
import com.konfigyr.test.AbstractWiremockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Locale;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class TransportTest extends AbstractWiremockTest {

    @Test
    @DisplayName("should apply default headers and read timeout to every request")
    void appliesDefaultHeadersAndTimeout() {
        wiremock.stubFor(get(urlPathEqualTo("/ping")).willReturn(aResponse().withStatus(200)));

        final var transport = new Transport(TransportOptions.builder().userAgent("test-agent").build());
        final var request = HttpRequest.newBuilder().GET().uri(URI.create(wiremock.baseUrl() + "/ping")).build();

        final var response = transport.send(request);

        assertThat(response.statusCode()).isEqualTo(200);

        wiremock.verify(getRequestedFor(urlPathEqualTo("/ping"))
                .withHeader("User-Agent", equalTo("test-agent"))
                .withHeader("Accept-Language", equalTo(Locale.ENGLISH.toLanguageTag()))
                .withHeader("X-Request-Id", matching(".+")));
    }

    @Test
    @DisplayName("should preserve caller-set headers alongside the default ones")
    void preservesCallerHeaders() {
        wiremock.stubFor(get(urlPathEqualTo("/ping")).willReturn(aResponse().withStatus(200)));

        final var transport = new Transport(TransportOptions.DEFAULT);
        final var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(wiremock.baseUrl() + "/ping"))
                .header("Accept", "application/json")
                .build();

        transport.send(request);

        wiremock.verify(getRequestedFor(urlPathEqualTo("/ping"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo(TransportOptions.DEFAULT.userAgent())));
    }

    @Test
    @DisplayName("should return the response body as a plain string")
    void returnsResponseBodyAsString() {
        wiremock.stubFor(get(urlPathEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("pong")));

        final var transport = new Transport(TransportOptions.DEFAULT);
        final var request = HttpRequest.newBuilder().GET().uri(URI.create(wiremock.baseUrl() + "/ping")).build();

        final var response = transport.send(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("pong");
    }

    @Test
    @DisplayName("should wrap connection errors as UncheckedIOException")
    void wrapsConnectionErrors() {
        wiremock.stubFor(get(urlPathEqualTo("/ping")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        final var transport = new Transport(TransportOptions.DEFAULT);
        final var request = HttpRequest.newBuilder().GET().uri(URI.create(wiremock.baseUrl() + "/ping")).build();

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> transport.send(request))
                .withMessageContaining("Error occurred while establishing connection")
                .withCauseInstanceOf(IOException.class);
    }

}
