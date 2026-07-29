package com.konfigyr.gradle;

import com.konfigyr.ClientCredentials;
import com.konfigyr.Registry;
import com.konfigyr.TokenExchange;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Verifies {@link RegistrySpec} behavior directly, without going through a real Gradle build - the
 * DSL closure delegation itself (i.e. that {@code clientCredentials { } } }} inside a build script
 * actually reaches {@link RegistrySpec.ClientCredentialsSpec}) is exercised by the {@code
 * KonfigyrPlugin*Test} TestKit fixtures instead.
 *
 * @author Vladimir Spasic
 * @since 1.2.0
 */
class RegistrySpecTest {

    private ObjectFactory objects;
    private ProviderFactory providers;

    @BeforeEach
    void setup() {
        final Project project = ProjectBuilder.builder().build();
        objects = project.getObjects();
        providers = project.getProviders();
    }

    @Test
    @DisplayName("is not configured by default")
    void notConfiguredByDefault() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);

        assertThat(spec.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("konfigyrCentral defaults its url to the default host")
    void centralDefaultsUrl() {
        final RegistrySpec spec = new RegistrySpec("konfigyrCentral", objects, providers, true);

        assertThat(spec.getUrl().get()).isEqualTo(Registry.DEFAULT_HOST);
    }

    @Test
    @DisplayName("a custom registry has no url convention")
    void customRegistryHasNoUrlConvention() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);

        assertThat(spec.getUrl().isPresent()).isFalse();
    }

    @Test
    @DisplayName("a custom registry's client credentials have no environment variable convention")
    void customRegistryHasNoEnvironmentConvention() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);

        spec.clientCredentials(credentials -> { });

        assertThat(spec.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("clientCredentials lazily creates its spec once and reuses it on repeat calls")
    void clientCredentialsReused() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        final RegistrySpec.ClientCredentialsSpec[] captured = new RegistrySpec.ClientCredentialsSpec[2];

        spec.clientCredentials(credentials -> captured[0] = credentials);
        spec.clientCredentials(credentials -> captured[1] = credentials);

        assertThat(captured[0]).isNotNull().isSameAs(captured[1]);
    }

    @Test
    @DisplayName("tokenExchange lazily creates its spec once and reuses it on repeat calls")
    void tokenExchangeReused() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        final RegistrySpec.TokenExchangeSpec[] captured = new RegistrySpec.TokenExchangeSpec[2];

        spec.tokenExchange(exchange -> captured[0] = exchange);
        spec.tokenExchange(exchange -> captured[1] = exchange);

        assertThat(captured[0]).isNotNull().isSameAs(captured[1]);
    }

    @Test
    @DisplayName("is configured once a url and clientCredentials are set")
    void configuredWithClientCredentials() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        spec.getUrl().set(URI.create("https://staging.konfigyr.io"));

        spec.clientCredentials(credentials -> {
            credentials.getClientId().set("client-id");
            credentials.getClientSecret().set("client-secret");
        });

        assertThat(spec.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("is configured once a url and tokenExchange are set")
    void configuredWithTokenExchange() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        spec.getUrl().set(URI.create("https://staging.konfigyr.io"));

        spec.tokenExchange(exchange -> {
            exchange.getClientId().set("client-id");
            exchange.getSubjectToken().set("subject-token");
            exchange.getSubjectTokenType().set("urn:ietf:params:oauth:token-type:jwt");
        });

        assertThat(spec.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("tokenExchange takes priority over clientCredentials when both are configured")
    void tokenExchangeTakesPriority() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        spec.getUrl().set(URI.create("https://staging.konfigyr.io"));

        spec.clientCredentials(credentials -> {
            credentials.getClientId().set("client-credentials-id");
            credentials.getClientSecret().set("client-credentials-secret");
        });
        spec.tokenExchange(exchange -> {
            exchange.getClientId().set("token-exchange-id");
            exchange.getSubjectToken().set("subject-token");
            exchange.getSubjectTokenType().set("urn:ietf:params:oauth:token-type:jwt");
        });

        assertThat(spec.toRegistry().credentials())
                .isInstanceOfSatisfying(TokenExchange.class, exchange ->
                        assertThat(exchange.clientId()).isEqualTo("token-exchange-id"));
    }

    @Test
    @DisplayName("fails to resolve a Registry when the url is not set")
    void failsWithoutUrl() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);

        assertThatExceptionOfType(GradleException.class).isThrownBy(spec::toRegistry);
    }

    @Test
    @DisplayName("fails to resolve a Registry when neither grant is configured")
    void failsWithoutCredentials() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        spec.getUrl().set(URI.create("https://staging.konfigyr.io"));

        assertThatExceptionOfType(GradleException.class)
                .isThrownBy(spec::toRegistry)
                .withMessageContaining("staging");
    }

    @Test
    @DisplayName("fails to resolve a Registry when its url is not https and not a loopback address")
    void rejectsNonHttpsUrl() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        spec.getUrl().set(URI.create("http://staging.konfigyr.io"));
        spec.clientCredentials(credentials -> {
            credentials.getClientId().set("client-id");
            credentials.getClientSecret().set("client-secret");
        });

        assertThatExceptionOfType(GradleException.class)
                .isThrownBy(spec::toRegistry)
                .withMessageContaining("HTTPS");
    }

    @Test
    @DisplayName("allows a loopback http url, for local testing")
    void allowsLoopbackHttpUrl() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        spec.getUrl().set(URI.create("http://localhost:8080"));
        spec.clientCredentials(credentials -> {
            credentials.getClientId().set("client-id");
            credentials.getClientSecret().set("client-secret");
        });

        assertThat(spec.toRegistry().host()).isEqualTo(URI.create("http://localhost:8080"));
    }

    @Test
    @DisplayName("is not insecure by default")
    void notInsecureByDefault() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);

        assertThat(spec.getInsecure().get()).isFalse();
    }

    @Test
    @DisplayName("allows a non-https url when insecure is set to true")
    void allowsNonHttpsUrlWhenInsecure() {
        final RegistrySpec spec = new RegistrySpec("staging", objects, providers, false);
        spec.getUrl().set(URI.create("http://konfigyr.internal.acme.com"));
        spec.getInsecure().set(true);
        spec.clientCredentials(credentials -> {
            credentials.getClientId().set("client-id");
            credentials.getClientSecret().set("client-secret");
        });

        assertThat(spec.toRegistry().host())
                .isEqualTo(URI.create("http://konfigyr.internal.acme.com"));
    }

    @Test
    @DisplayName("isInsecureRegistry is false when insecure is not set")
    void isInsecureRegistryFalseWhenNotInsecure() {
        final Registry registry = Registry.builder()
                .host(URI.create("http://konfigyr.internal.acme.com"))
                .credentials(new ClientCredentials("client-id", "client-secret"))
                .build();

        assertThat(RegistrySpec.isInsecureRegistry(registry)).isFalse();
    }

    @Test
    @DisplayName("isInsecureRegistry is false when insecure is set but the host uses https")
    void isInsecureRegistryFalseWhenHttps() {
        final Registry registry = Registry.builder()
                .host(URI.create("https://konfigyr.internal.acme.com"))
                .credentials(new ClientCredentials("client-id", "client-secret"))
                .insecure(true)
                .build();

        assertThat(RegistrySpec.isInsecureRegistry(registry)).isFalse();
    }

    @Test
    @DisplayName("isInsecureRegistry is false when insecure is set but the host is a loopback address")
    void isInsecureRegistryFalseWhenLoopback() {
        final Registry registry = Registry.builder()
                .host(URI.create("http://localhost:8080"))
                .credentials(new ClientCredentials("client-id", "client-secret"))
                .insecure(true)
                .build();

        assertThat(RegistrySpec.isInsecureRegistry(registry)).isFalse();
    }

    @Test
    @DisplayName("isInsecureRegistry is true when insecure is set on a plaintext, non-loopback host")
    void isInsecureRegistryTrueForRealInsecureHost() {
        final Registry registry = Registry.builder()
                .host(URI.create("http://konfigyr.internal.acme.com"))
                .credentials(new ClientCredentials("client-id", "client-secret"))
                .insecure(true)
                .build();

        assertThat(RegistrySpec.isInsecureRegistry(registry)).isTrue();
    }

}
