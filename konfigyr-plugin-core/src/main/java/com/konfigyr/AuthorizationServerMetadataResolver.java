package com.konfigyr;

import com.google.common.net.HttpHeaders;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Resolves the OAuth2 endpoints of a Konfigyr Artifactory registry from its {@code url}.
 * <p>
 * Resolution fetches <a href="https://datatracker.ietf.org/doc/html/rfc9728">RFC 9728 Protected
 * Resource Metadata</a> at the registry's {@code /.well-known/oauth-protected-resource}. If present,
 * its {@code authorization_servers} entry is followed to fetch that issuer's
 * <a href="https://datatracker.ietf.org/doc/html/rfc8414">RFC 8414 Authorization Server Metadata</a>
 * at {@code /.well-known/oauth-authorization-server}. If the Protected Resource Metadata document is
 * absent ({@code 404}), the registry's {@code url} itself is treated as the Authorization Server
 * issuer and its metadata is fetched directly. Either way, the {@code issuer} claim of the resulting
 * Authorization Server Metadata document must match the issuer URI actually used to fetch it, as
 * required by RFC 8414 - this guards against a compromised or misconfigured host serving substituted
 * metadata.
 * <p>
 * Every URI involved in discovery must use {@code https}, unless its host is a loopback address
 * ({@code localhost}, {@code 127.0.0.1}, {@code ::1}), which is exempted for local testing.
 * <p>
 * Results are cached per registry {@code url} for a configurable TTL, defaulting to 15 minutes, so
 * that repeated builds sharing a JVM (e.g., the Gradle daemon) don't re-discover the same registry on
 * every invocation. Multiple registries pointing at the same {@code url} within one build only
 * trigger a single discovery fetch.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 * @see AuthorizationServerMetadata
 */
@NullMarked
final class AuthorizationServerMetadataResolver {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private static final String PROTECTED_RESOURCE_WELL_KNOWN = "oauth-protected-resource";
    private static final String AUTHORIZATION_SERVER_WELL_KNOWN = "oauth-authorization-server";

    private final Logger logger = LoggerFactory.getLogger(AuthorizationServerMetadataResolver.class);

    private final JsonMapper mapper;
    private final Transport transport;
    private final Cache<URI, AuthorizationServerMetadata> cache;

    /**
     * Creates a new {@link AuthorizationServerMetadataResolver} using the default 15 minute cache TTL.
     *
     * @param mapper the JSON mapper to use, cannot be {@literal null}.
     * @param transport the shared transport used to fetch metadata documents, cannot be {@literal null}.
     */
    AuthorizationServerMetadataResolver(JsonMapper mapper, Transport transport) {
        this(mapper, transport, DEFAULT_TTL);
    }

    /**
     * Creates a new {@link AuthorizationServerMetadataResolver}.
     *
     * @param mapper the JSON mapper to use, cannot be {@literal null}.
     * @param transport the shared transport used to fetch metadata documents, cannot be {@literal null}.
     * @param ttl how long a resolved {@link AuthorizationServerMetadata} is cached for, cannot be {@literal null}.
     */
    AuthorizationServerMetadataResolver(JsonMapper mapper, Transport transport, Duration ttl) {
        this.mapper = Objects.requireNonNull(mapper, "JSON mapper must not be null");
        this.transport = Objects.requireNonNull(transport, "Transport must not be null");
        this.cache = new Cache<>(Objects.requireNonNull(ttl, "Cache TTL must not be null"));
    }

    /**
     * Resolves the {@link AuthorizationServerMetadata} for the registry reachable at the given
     * {@code url}, using a cached result if one is present and not yet expired.
     *
     * @param registryUrl the registry's {@code url}, cannot be {@literal null}.
     * @return the resolved metadata, never {@literal null}.
     */
    AuthorizationServerMetadata resolve(URI registryUrl) {
        Objects.requireNonNull(registryUrl, "registry URL must not be null");
        return cache.get(registryUrl, () -> discover(registryUrl));
    }

    private AuthorizationServerMetadata discover(URI registryUrl) {
        assertHttps(registryUrl);

        if (logger.isDebugEnabled()) {
            logger.debug("Discovering OAuth2 Authorization Server Metadata for registry: {}", registryUrl);
        }

        final JsonNode protectedResourceMetadata = fetch(buildWellKnownUri(registryUrl, PROTECTED_RESOURCE_WELL_KNOWN));

        URI authorizationServerIssuer = sanitize(registryUrl);

        if (protectedResourceMetadata != null) {
            authorizationServerIssuer = extractAuthorizationServerIssuer(protectedResourceMetadata, registryUrl);
            assertHttps(authorizationServerIssuer);
        }

        final JsonNode authorizationServerMetadata =
                fetch(buildWellKnownUri(authorizationServerIssuer, AUTHORIZATION_SERVER_WELL_KNOWN));

        if (authorizationServerMetadata == null) {
            throw new IllegalStateException(
                    "Could not discover OAuth2 Authorization Server Metadata for registry '%s' at issuer '%s'"
                            .formatted(registryUrl, authorizationServerIssuer));
        }

        final URI issuer = getRequiredUri(authorizationServerMetadata, "issuer", authorizationServerIssuer);
        final URI tokenEndpoint = getRequiredUri(authorizationServerMetadata, "token_endpoint", authorizationServerIssuer);

        if (!issuer.equals(authorizationServerIssuer)) {
            throw new IllegalStateException(
                    "Authorization Server Metadata issuer '%s' does not match the expected issuer '%s' for registry '%s'"
                            .formatted(issuer, authorizationServerIssuer, registryUrl));
        }

        logger.info("Resolved Authorization Server Metadata for registry '{}': issuer={}, tokenEndpoint={}",
                registryUrl, issuer, tokenEndpoint);

        return new AuthorizationServerMetadata(issuer, tokenEndpoint);
    }

    private @Nullable JsonNode fetch(URI uri) {
        if (logger.isDebugEnabled()) {
            logger.debug("Fetching OAuth2 metadata document from: {}", uri);
        }

        final HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name())
                .build();

        final HttpResponse<String> response = transport.send(request);

        if (response.statusCode() == 404) {
            return null;
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Received unexpected HTTP status code %d while fetching OAuth2 metadata from: %s"
                            .formatted(response.statusCode(), uri));
        }

        try {
            return mapper.readTree(response.body());
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to parse OAuth2 metadata response from: " + uri, ex);
        }
    }

    private static URI extractAuthorizationServerIssuer(JsonNode protectedResourceMetadata, URI registryUrl) {
        final JsonNode servers = protectedResourceMetadata.get("authorization_servers");

        if (servers == null || !servers.isArray() || servers.isEmpty()) {
            throw new IllegalStateException(
                    "Protected Resource Metadata for registry '%s' does not declare any 'authorization_servers'"
                            .formatted(registryUrl));
        }

        final JsonNode first = servers.get(0);

        if (first == null || !first.isValueNode() || first.asString().isBlank()) {
            throw new IllegalStateException(
                    "Protected Resource Metadata for registry '%s' declares an invalid authorization server entry"
                            .formatted(registryUrl));
        }

        return sanitize(URI.create(first.asString()));
    }

    private static URI getRequiredUri(JsonNode node, String field, URI context) {
        final JsonNode value = node.get(field);

        if (value == null || !value.isValueNode() || value.asString().isBlank()) {
            throw new IllegalStateException(
                    "Authorization Server Metadata document from '%s' is missing required field '%s'"
                            .formatted(context, field));
        }

        return sanitize(URI.create(value.asString()));
    }

    /**
     * Normalizes a {@link URI} parsed from an untrusted OAuth2 metadata document (or the registry's
     * own configured {@code url}) so every issuer/endpoint URI this resolver produces is safe to
     * compare and use consistently.
     * <p>
     * Per <a href="https://datatracker.ietf.org/doc/html/rfc3986#section-6.2.3">RFC 3986 &sect;6.2.3</a>,
     * a URI with an authority component and a path of {@code "/"} is equivalent to the same URI with
     * an empty path. Some Authorization Servers include a redundant trailing slash on their root
     * {@code issuer} claim (or an {@code authorization_servers} entry) while another document derived
     * from the same origin - most commonly the registry's own configured {@code url}, which never
     * carries one - omits it, which would otherwise cause a spurious issuer mismatch. Stripping toward
     * the no-trailing-slash form (rather than adding one) means this is a no-op for the common case,
     * where nothing had a trailing slash to begin with. This method touches nothing else about the URI
     * (a non-root path, query, fragment, or the casing of its scheme/host).
     *
     * @param uri the URI to normalize, must not be {@literal null}.
     * @return the normalized, or sanitized, URI, never {@literal null}.
     */
    private static URI sanitize(URI uri) {
        if (!"/".equals(uri.getRawPath())) {
            return uri;
        }

        return UriBuilder.of(uri).path(null).build();
    }

    /**
     * Builds the well-known metadata {@link URI} for the given base, following the path-insertion
     * rules shared by RFC 8414 and RFC 9728: the well-known segment is inserted immediately after the
     * authority, before the base's own path (if any), rather than appended after it.
     *
     * @param base the base URI, must not be {@literal null}.
     * @param wellKnownSegment the well-known segment to insert, must not be {@literal null}.
     */
    private static URI buildWellKnownUri(URI base, String wellKnownSegment) {
        final String path = base.getRawPath();
        final String suffix = (path == null || path.isEmpty() || "/".equals(path)) ? "" : path;

        return UriBuilder.of(base)
                .path("/.well-known/" + wellKnownSegment + suffix)
                .query(null)
                .fragment(null)
                .build();
    }

    private static void assertHttps(URI uri) {
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return;
        }

        if ("http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost())) {
            return;
        }

        throw new IllegalArgumentException(
                "Registry URL '%s' must use HTTPS (loopback addresses are exempt for local testing)".formatted(uri));
    }

    private static boolean isLoopback(@Nullable String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

}
