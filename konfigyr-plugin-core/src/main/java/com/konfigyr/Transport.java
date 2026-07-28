package com.konfigyr;

import com.google.common.net.HttpHeaders;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Sends an {@link HttpRequest} over an {@link HttpClient} built from {@link TransportOptions},
 * centralizing the exception-wrapping ceremony every caller in this package otherwise repeats.
 * <p>
 * Every request passed to {@link #send(HttpRequest)} has this transport's {@code Accept-Language},
 * {@code User-Agent} and {@code X-Request-Id} headers and read timeout applied to it automatically,
 * on top of whatever the caller already set, callers only need to add headers specific to their own
 * call.
 * <p>
 * This deliberately does nothing beyond that: status-code interpretation (which codes are errors, and
 * what they mean) and response body parsing both differ per caller. The Artifactory REST API,
 * the OAuth2 token endpoint, and OAuth2 metadata discovery each attach different meaning to the same
 * HTTP status codes, so callers apply that themselves to the returned {@link HttpResponse}.
 * <p>
 * A single instance is meant to be shared across every {@link Registry} configured for a build,
 * constructing one {@link Transport} and passing it to every collaborator that needs one (rather
 * than each building its own) means one shared connection pool instead of one per {@link Registry}.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 */
@NullMarked
final class Transport {

    private final Logger logger = LoggerFactory.getLogger(Transport.class);

    private final TransportOptions options;
    private final HttpClient client;

    /**
     * Creates a new {@link Transport}, building its own {@link HttpClient} from the given
     * {@link TransportOptions}.
     *
     * @param options the transport options, cannot be {@literal null}.
     */
    Transport(TransportOptions options) {
        this.options = Objects.requireNonNull(options, "Transport options must not be null");
        this.client = HttpClient.newBuilder()
                .connectTimeout(options.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * The options this transport, and every request built by its callers, was configured with.
     *
     * @return the transport options, never {@literal null}.
     */
    TransportOptions options() {
        return options;
    }

    /**
     * Sends the given request, returning its response body as a plain {@link String}.
     *
     * @param request the request to send, cannot be {@literal null}.
     * @return the response, never {@literal null}.
     */
    HttpResponse<String> send(HttpRequest request) {
        if (logger.isDebugEnabled()) {
            logger.debug("Executing HTTP request: {} {}", request.method(), request.uri());
        }

        final HttpResponse<String> response;

        final HttpRequest customized = HttpRequest.newBuilder(request, (n, v) -> true)
                .header(HttpHeaders.ACCEPT_LANGUAGE, Locale.ENGLISH.toLanguageTag())
                .header(HttpHeaders.USER_AGENT, options.userAgent())
                .header(HttpHeaders.X_REQUEST_ID, UUID.randomUUID().toString())
                .timeout(options.readTimeout())
                .build();

        try {
            response = client.send(customized, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new UncheckedIOException("Error occurred while establishing connection for HTTP request: %s %s"
                    .formatted(customized.method(), customized.uri()), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request was interrupted: %s %s"
                    .formatted(customized.method(), customized.uri()), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unexpected error occurred while executing HTTP request: %s %s"
                    .formatted(customized.method(), customized.uri()), ex);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Server {} responded with status code {} and body: {}",
                    customized.uri().getHost(), response.statusCode(), response.body());
        }

        return response;
    }

}
