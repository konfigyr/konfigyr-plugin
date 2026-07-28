package com.konfigyr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class UriBuilderTest {

    @Test
    @DisplayName("should build a URI from individually set components")
    void buildsFromScratch() {
        final URI uri = UriBuilder.create()
                .scheme("https")
                .authority("example.com")
                .path("/oauth/token")
                .query("scope=read")
                .fragment("section")
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com/oauth/token?scope=read#section"));
    }

    @Test
    @DisplayName("should build a URI with only a scheme and authority when nothing else is set")
    void buildsWithOnlySchemeAndAuthority() {
        final URI uri = UriBuilder.create()
                .scheme("https")
                .authority("example.com")
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com"));
    }

    @Test
    @DisplayName("of(URI) should reconstruct an identical URI when built without changes")
    void ofUriRoundTrips() {
        final URI original = URI.create("https://example.com:8443/tenant/resource?query=value#fragment");

        assertThat(UriBuilder.of(original).build()).isEqualTo(original);
    }

    @Test
    @DisplayName("of(String) should parse and reconstruct an identical URI when built without changes")
    void ofStringRoundTrips() {
        final String original = "https://example.com/tenant?query=value#fragment";

        assertThat(UriBuilder.of(original).build()).isEqualTo(URI.create(original));
    }

    @Test
    @DisplayName("of(URI) should let a single component be overridden while the rest are preserved")
    void ofUriOverridesOnlyGivenComponent() {
        final URI original = URI.create("https://example.com/tenant?query=value#fragment");

        final URI overridden = UriBuilder.of(original)
                .path("/other")
                .build();

        assertThat(overridden).isEqualTo(URI.create("https://example.com/other?query=value#fragment"));
    }

    @Test
    @DisplayName("path(null) should remove a previously set path component")
    void pathNullRemovesPath() {
        final URI uri = UriBuilder.of(URI.create("https://example.com/tenant"))
                .path(null)
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com"));
    }

    @Test
    @DisplayName("paths(...) should append percent-encoded segments to an empty path")
    void pathsAppendsToEmptyPath() {
        final URI uri = UriBuilder.create()
                .scheme("https")
                .authority("example.com")
                .paths("releases", "test-service", "artifacts")
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com/releases/test-service/artifacts"));
    }

    @Test
    @DisplayName("paths(...) should append percent-encoded segments after an existing path")
    void pathsAppendsAfterExistingPath() {
        final URI uri = UriBuilder.of("https://example.com/api")
                .paths("releases", "test-service")
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com/api/releases/test-service"));
    }

    @Test
    @DisplayName("paths(...) should percent-encode a segment containing reserved characters")
    void pathsPercentEncodesSegments() {
        final URI uri = UriBuilder.create()
                .scheme("https")
                .authority("example.com")
                .paths("com.acme", "artifact name", "1.0.0")
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com/com.acme/artifact%20name/1.0.0"));
    }

    @Test
    @DisplayName("paths(...) should treat a segment containing a slash as opaque, percent-encoding it too")
    void pathsPercentEncodesSlashWithinSegment() {
        final URI uri = UriBuilder.create()
                .scheme("https")
                .authority("example.com")
                .path("/existing/path/")
                .paths("foo/", "", "/", "bar", "a/b", "  ", "/baz")
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com/existing/path/foo%2F/bar/a%2Fb/baz"));
    }

    @Test
    @DisplayName("query(null) and fragment(null) should remove previously set components")
    void queryAndFragmentNullRemoveComponents() {
        final URI uri = UriBuilder.of("https://example.com/tenant?query=value#fragment")
                .query(null)
                .fragment(null)
                .build();

        assertThat(uri).isEqualTo(URI.create("https://example.com/tenant"));
    }

    @Test
    @DisplayName("setters should mutate and return the same builder instance")
    void settersAreMutableAndChainable() {
        final UriBuilder builder = UriBuilder.create();

        assertThat(builder.scheme("https")).isSameAs(builder);
        assertThat(builder.authority("example.com")).isSameAs(builder);
        assertThat(builder.path("/tenant")).isSameAs(builder);
        assertThat(builder.paths("more")).isSameAs(builder);
        assertThat(builder.query("q=1")).isSameAs(builder);
        assertThat(builder.fragment("frag")).isSameAs(builder);
    }

}
