package com.konfigyr;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Mutable builder for constructing a {@link URI} from its individual components, starting from an
 * existing {@link URI}, a URI string, or from scratch, then overriding one or more component parts
 * before {@link #build() building} the result.
 * <p>
 * Every component is stored and rendered by {@link #build()}, exactly as given, already
 * percent-encoded, i.e. "raw" in {@link URI}'s own terminology, this builder does not itself perform
 * any percent-encoding or validation beyond what {@link URI#create(String)} enforces.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 */
@NullMarked
final class UriBuilder {

    private @Nullable String scheme;
    private @Nullable String authority;
    private @Nullable String path;
    private @Nullable String query;
    private @Nullable String fragment;

    private UriBuilder() {
    }

    /**
     * Creates a new, empty {@link UriBuilder} with no components set.
     *
     * @return a new builder instance, never {@literal null}.
     */
    static UriBuilder create() {
        return new UriBuilder();
    }

    /**
     * Creates a new {@link UriBuilder}, pre-populated with every component of the given {@link URI}.
     *
     * @param uri the URI to copy components from, cannot be {@literal null}.
     * @return a new builder instance, never {@literal null}.
     */
    static UriBuilder of(URI uri) {
        return create()
                .scheme(uri.getScheme())
                .authority(uri.getRawAuthority())
                .path(uri.getRawPath())
                .query(uri.getRawQuery())
                .fragment(uri.getRawFragment());
    }

    /**
     * Creates a new {@link UriBuilder}, pre-populated with every component of the given URI string.
     *
     * @param uri the URI string to parse and copy components from, cannot be {@literal null}.
     * @return a new builder instance, never {@literal null}.
     */
    static UriBuilder of(String uri) {
        return of(URI.create(uri));
    }

    /**
     * Sets the scheme component, for example {@code https}.
     *
     * @param scheme the scheme, or {@literal null} to omit it.
     * @return this builder instance for method chaining.
     */
    UriBuilder scheme(@Nullable String scheme) {
        this.scheme = scheme;
        return this;
    }

    /**
     * Sets the authority component, for example {@code example.com:8443}.
     *
     * @param authority the raw (already encoded) authority, or {@literal null} to omit it.
     * @return this builder instance for method chaining.
     */
    UriBuilder authority(@Nullable String authority) {
        this.authority = authority;
        return this;
    }

    /**
     * Sets the path component, for example {@code /oauth/token}.
     * <p>
     * A trailing {@code /}, if present, is stripped, callers never need to worry about ending up
     * with a redundant trailing separator, whether they set the path directly or it was built up via
     * {@link #paths(String...)}.
     *
     * @param path the raw (already encoded) path, or {@literal null} to omit it.
     * @return this builder instance for method chaining.
     */
    UriBuilder path(@Nullable String path) {
        if (path != null && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        this.path = path;
        return this;
    }

    /**
     * Appends each given path segment to whatever path is already set, percent-encoding it and
     * separating it from what precedes it with a {@code /}.
     * <p>
     * Unlike {@link #path(String)}, segments passed here are not expected to already be encoded. A
     * single leading {@code /} on a segment is treated as a redundant separator and stripped, rather
     * than doubled up or encoded, but any other {@code /} - embedded or trailing - within a segment is
     * opaque content, percent-encoded like any other reserved character rather than treated as a
     * delimiter. A blank (empty or whitespace-only) segment, or one that is exactly {@code /},
     * contributes nothing and is skipped.
     *
     * @param segments the path segments to append, cannot be {@literal null}.
     * @return this builder instance for method chaining.
     */
    UriBuilder paths(String... segments) {
        final StringBuilder appended = new StringBuilder(path != null ? path : "");

        for (String segment : segments) {
            if (segment.startsWith("/")) {
                segment = segment.substring(1);
            }

            if (segment.isBlank() || "/".equals(segment)) {
                continue;
            }

            appended.append('/').append(
                    URLEncoder.encode(segment, StandardCharsets.UTF_8)
                            .replace("+", "%20"));
        }

        return path(appended.toString());
    }

    /**
     * Sets the query component, without its leading {@code ?}.
     *
     * @param query the raw (already encoded) query, or {@literal null} to omit it.
     * @return this builder instance for method chaining.
     */
    UriBuilder query(@Nullable String query) {
        this.query = query;
        return this;
    }

    /**
     * Sets the fragment component, without its leading {@code #}.
     *
     * @param fragment the raw (already encoded) fragment, or {@literal null} to omit it.
     * @return this builder instance for method chaining.
     */
    UriBuilder fragment(@Nullable String fragment) {
        this.fragment = fragment;
        return this;
    }

    /**
     * Builds the {@link URI} described by this builder's currently configured components.
     *
     * @return the built URI, never {@literal null}.
     */
    URI build() {
        final StringBuilder built = new StringBuilder();

        if (scheme != null) {
            built.append(scheme).append(':');
        }
        if (authority != null) {
            built.append("//").append(authority);
        }
        if (path != null) {
            built.append(path);
        }
        if (query != null) {
            built.append('?').append(query);
        }
        if (fragment != null) {
            built.append('#').append(fragment);
        }

        return URI.create(built.toString());
    }

}
